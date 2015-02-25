package com.devinrsmith.jblob.api.uri;

import com.devinrsmith.jblob.api.JBlobMeta;
import com.devinrsmith.jblob.api.keyless.*;
import com.devinrsmith.jblob.api.uri.JBlobHttpClient.Results;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
* Created by dsmith on 2/24/15.
*/
public class JBlobUriImpl implements JBlobUri {
    private final JBlobKeyless jblob;
    private final JBlobHttpClient client;

    public JBlobUriImpl(JBlobKeyless jblob, JBlobHttpClient client) {
        this.jblob = jblob;
        this.client = client;
    }

    @Override
    public String upload(URI uri, Map<String, String> properties) throws IOException, InterruptedException {
        Preconditions.checkNotNull(uri);
        Preconditions.checkNotNull(properties);

        final Results results = client.executeGet(uri);
        final Map<String, String> userProperties = combineProperties(properties, ImmutableMap.of("from", uri.toString()));
        final Map<String, String> combined = combineProperties(results.getHeaders(), userProperties);

        return upload(results, combined);
    }

    private String upload(Results results, Map<String, String> properties) throws IOException, InterruptedException {
        // note: we prefer to save the URL as a temporary file as opposed to created a URL ByteSource
        final Path tmp = Files.createTempFile("jblob-upload-", null);
        try {
            return upload(asFileByteSource(results, tmp), properties);
        } finally {
            cleanDelete(tmp);
        }
    }

    // note: when they overlap, we'll choose the primary key over the secondary key
    private Map<String, String> combineProperties(Map<String, String> primary, Map<String, String> secondary) {
        // note: i expect secondary to be smaller, although it likely doesn't matter
        final Set<String> keys = Sets.union(secondary.keySet(), primary.keySet());
        return keys.stream().collect(Collectors.toMap(Function.identity(),
                k -> MoreObjects.firstNonNull(
                        primary.get(k),
                        secondary.get(k))));
    }


    private void cleanDelete(Path tmp) {
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException | SecurityException e) {
            // don't care about these on trying to delete
        }
    }

    private ByteSource asFileByteSource(Results results, Path outPath) throws IOException {
        try (final InputStream in = results.openStream()) {
            try (final OutputStream out = Files.newOutputStream(outPath)) {
                ByteStreams.copy(in, out);
            }
        }
        return com.google.common.io.Files.asByteSource(outPath.toFile());
    }

    @Override
    public String upload(ByteSource source, Map<String, String> properties) throws IOException, InterruptedException {
        return jblob.upload(source, properties);
    }


    @Override
    public void upload(String key, InputStream in, Map<String, String> properties) throws InterruptedException, IOException {
        jblob.upload(key, in, properties);
    }

    @Override
    public Optional<JBlobMeta> download(String key, OutputStream out) throws IOException, InterruptedException {
        return jblob.download(key, out);
    }

    @Override
    public Optional<JBlobMeta> downloadMetadata(String key) throws IOException {
        return jblob.downloadMetadata(key);
    }

    @Override
    public void delete(String key) throws IOException {
        jblob.delete(key);
    }

    @Override
    public Stream<String> keys() {
        return jblob.keys();
    }
}
