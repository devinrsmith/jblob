package com.devinrsmith.jblob.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * JBlob aims to provide a simple interface for dealing with blobs in a familiar key-value like way
 *
 * Often times blob storage is used with large media (audio, images, video), but technically a blob
 * can be any size.
 *
 * Blobs may additionally be annotated with contentType and properties.
 *
 * Implementations may have expectations or restrictions on keys, inputs, contentTypes, or properties,
 * but the hope is to keep these types of implementation specific dependencies to a minimum
 *
 * There are other blob interfaces where the interface provides or returns the blob key after uploading
 * (as opposed to this BlobApi where the caller provides the key). While fine in some circumstances,
 * it becomes overly restrictive in some situations (and the developers design a mapping middle-layer
 * to get back their desired caller-provided key functionality).
 *
 * The exceptions and naming of methods (download/upload) are meant to convey the notion that these
 * might be long running methods that have the potential to fail.
 */
public interface JBlob {

    /**
     * Create or overwrite key with bytes from in.
     */
    void upload(String key, InputStream in, Map<String, String> properties) throws InterruptedException, IOException;

    /**
     * Expected to return BlobMeta on success, empty if resource doesn't exist, and IOException otherwise
     */
    Optional<JBlobMeta> download(String key, OutputStream out) throws IOException, InterruptedException;

    /**
     * Expected to return BlobMeta on success, empty if resource doesn't exist, and IOException otherwise
     */
    Optional<JBlobMeta> downloadMetadata(String key) throws IOException;

    /**
     * Expected to return on success or if the resource doesn't exist, and IOException otherwise
     */
    void delete(String key) throws IOException;

    /**
     * throws UncheckedIOException on evaluation
     */
    Stream<String> keys();

    // ------------------------------------------------------------------------------------------------

    default void upload(String key, InputStream in) throws InterruptedException, IOException {
        upload(key, in, Collections.emptyMap());
    }

    /**
     * This is the preferred method of consuming keys as it throws the properly
     * If the Consumer throws an UncheckedIOException, the appropriate IOException will be rethrown
     */
    default void consumeKeys(Consumer<String> action) throws IOException {
        try {
            keys().parallel().forEach(action);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    default JBlobStatistics stats() throws IOException {
        final LongAdder size = new LongAdder();
        final LongAdder count = new LongAdder();
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(Long.MIN_VALUE);
        try {
            keys().parallel().forEach(k -> {
                final Optional<JBlobMeta> meta;
                try {
                    meta = downloadMetadata(k);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                if (meta.isPresent()) {
                    count.increment();
                    final long contentLength = meta.get().getContentLength();
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
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return JBlobStatistics.of(count.sum(), size.sum(), min.get(), max.get());
    }

    default void copy(String key, String otherKey, JBlob other) throws IOException, InterruptedException {
        final Path tmp = Files.createTempFile("blob-copy", null);
        final Optional<JBlobMeta> meta;
        try (final OutputStream out = Files.newOutputStream(tmp)) {
            meta = other.download(otherKey, out);
        }
        if (!meta.isPresent()) {
            return;
        }
        try (final InputStream in = Files.newInputStream(tmp, StandardOpenOption.DELETE_ON_CLOSE)) {
            upload(key, in, meta.get().getProperties());
        }
    }

    default void copy(JBlob other, Function<String, String> keyFunction) throws IOException, InterruptedException {
        try {
            other.keys().parallel().forEach(otherKey -> {
                try {
                    copy(keyFunction.apply(otherKey), otherKey, other);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        } catch (RuntimeException e) {
            if (e.getCause() != null && e.getCause() instanceof InterruptedException) {
                throw (InterruptedException)e.getCause();
            }
            throw e;
        }
    }
}
