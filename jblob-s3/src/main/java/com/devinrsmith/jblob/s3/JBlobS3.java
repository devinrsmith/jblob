package com.devinrsmith.jblob.s3;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.Headers;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.Download;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.devinrsmith.jblob.api.JBlob;
import com.devinrsmith.jblob.api.JBlobMeta;
import com.devinrsmith.jblob.api.JBlobStatistics;
import com.devinrsmith.jblob.common.LazyIterator;
import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractSequentialIterator;
import com.google.common.collect.Iterators;
import com.google.common.io.ByteStreams;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Created by dsmith on 2/20/15.
 */
public class JBlobS3 implements JBlob {

    private final String bucket;
    private final TransferManager tx;

    public JBlobS3(String bucket, TransferManager tx) {
        this.bucket = Preconditions.checkNotNull(bucket);
        this.tx = Preconditions.checkNotNull(tx);
    }

    @Override
    public Stream<String> keys() {
        return summariesStream(false).map(S3ObjectSummary::getKey);
    }

    @Override
    public void upload(String key, InputStream in, Map<String, String> properties) throws InterruptedException, IOException {
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(in);
        Preconditions.checkNotNull(properties);

        // create temporary file from inputStream
        final Path tmp = Files.createTempFile("s3-" + bucket, null);
        try (final OutputStream out = Files.newOutputStream(tmp)) {
            ByteStreams.copy(in, out);
        }

        // the length is the important part here
        final ObjectMetadata m = new ObjectMetadata();

        m.setUserMetadata(properties);
        m.setContentLength(tmp.toFile().length());
        final String contentType = properties.get(Headers.CONTENT_TYPE);
        if (contentType != null) {
            m.setContentType(contentType);
        }
        final String encoding = properties.get(Headers.CONTENT_ENCODING);
        if (encoding != null) {
            m.setContentEncoding(encoding);
        }
        final String disposition = properties.get(Headers.CONTENT_DISPOSITION);
        if (disposition != null) {
            m.setContentDisposition(disposition);
        }

        // read from temporary file, let the OS delete on close
        try (final InputStream localIn = Files.newInputStream(tmp, StandardOpenOption.DELETE_ON_CLOSE)) {
            final Upload upload = tx.upload(bucket, key, localIn, m);
            upload.waitForUploadResult(); // note: we have to wait inside this block so the input stream isn't closed
        } catch (AmazonClientException e) {
            throw new IOException(e);
        }
    }

    @Override
    public Optional<JBlobMeta> downloadMetadata(String key) throws IOException {
        Preconditions.checkNotNull(key);

        final ObjectMetadata objectMetadata;
        try {
            objectMetadata = tx.getAmazonS3Client().getObjectMetadata(bucket, key);
        } catch (AmazonClientException e) {
            if (isNotFound(e)) {
                return Optional.empty();
            }
            throw new IOException(e);
        }
        return Optional.of(createBlobMeta(objectMetadata));
    }

    @Override
    public Optional<JBlobMeta> download(String key, OutputStream out) throws IOException, InterruptedException {
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(out);

        final Path tmp = Files.createTempFile("s3-" + bucket, null);

        final ObjectMetadata objectMetadata;
        try {
            final Download download = tx.download(bucket, key, tmp.toFile());
            download.waitForCompletion();
            objectMetadata = download.getObjectMetadata();
        } catch (AmazonClientException e) {
            if (isNotFound(e)) {
                return Optional.empty();
            }
            throw new IOException(e);
        }

        // read from temporary file, let the OS delete on close
        try (final InputStream localIn = Files.newInputStream(tmp, StandardOpenOption.DELETE_ON_CLOSE)) {
            ByteStreams.copy(localIn, out);
        }

        return Optional.of(createBlobMeta(objectMetadata));
    }

    @Override
    public void delete(String key) throws IOException {
        Preconditions.checkNotNull(key);

        try {
            tx.getAmazonS3Client().deleteObject(bucket, key);
        } catch (AmazonClientException e) {
            throw new IOException(e);
        }
    }

    @Override
    public JBlobStatistics stats() throws IOException {
        final LongAdder count = new LongAdder();
        final LongAdder size = new LongAdder();
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(Long.MIN_VALUE);
        try {
            // note: we are doing sequential here
            // doesn't make much sense to parallelize it
            // since we are doing chunked responses from the server anyways...
            // (we could technically do it faster by spawning a new thread that grabs
            // the new response as soon as the old ones comes in, but the summing isn't
            // a very expensive operation as compared to the round trip [I think])
            summariesStream(false).sequential().forEach(summary -> {
                count.increment();
                final long contentLength = summary.getSize();
                size.add(contentLength);

                // we could keep a thread local min around too to avoid unnecessary calls to get
                long minn;
                do {
                    minn = min.get();
                } while (contentLength < minn && !min.compareAndSet(minn, contentLength));

                // we could keep a thread local max around to avoid unnecessary calls to get
                long maxx;
                do {
                    maxx = max.get();
                } while (contentLength > maxx && !max.compareAndSet(maxx, contentLength));
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return JBlobStatistics.of(count.sum(), size.sum(), min.get(), max.get());
    }

    private static boolean isNotFound(AmazonClientException e) {
        if (e instanceof AmazonS3Exception) {
            if (((AmazonS3Exception)e).getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                return true;
            }
        }
        return false;
    }

    private static JBlobMeta createBlobMeta(ObjectMetadata objectMetadata) {
        return JBlobMeta.of(objectMetadata.getContentLength(), objectMetadata.getContentType(), objectMetadata.getUserMetadata());
    }


    private Stream<S3ObjectSummary> summariesStream(boolean parallel) {
        return StreamSupport.stream(summariesSpliterator(), parallel);
    }

    private Spliterator<S3ObjectSummary> summariesSpliterator() {
        return Spliterators.spliteratorUnknownSize(summariesIterator(), Spliterator.DISTINCT | Spliterator.IMMUTABLE | Spliterator.NONNULL);
    }

    private Iterator<S3ObjectSummary> summariesIterator() {
        return new LazyIterator<>(() -> {
            final ObjectListing objectListing;
            try {
                objectListing = tx.getAmazonS3Client().listObjects(bucket); // use the default chunking size
            } catch (AmazonClientException e) {
                throw new UncheckedIOException(new IOException(e));
            }

            final Iterator<ObjectListing> listingIterator = new AbstractSequentialIterator<ObjectListing>(objectListing) {
                @Override
                protected ObjectListing computeNext(ObjectListing previous) {
                    if (!previous.isTruncated()) {
                        return null;
                    }
                    try {
                        return tx.getAmazonS3Client().listNextBatchOfObjects(previous);
                    } catch (AmazonClientException e) {
                        throw new UncheckedIOException(new IOException(e));
                    }
                }
            };
            // wish we could do this more fluently... but typing it out all here to make it "clear"er
            final Iterator<Iterator<S3ObjectSummary>> toS3ObjectSummariesIterator = Iterators.transform(listingIterator, o -> o.getObjectSummaries().iterator());
            return Iterators.concat(toS3ObjectSummariesIterator);
        });
    }
}
