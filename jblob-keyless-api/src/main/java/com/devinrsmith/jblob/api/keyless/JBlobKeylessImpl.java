package com.devinrsmith.jblob.api.keyless;

import com.devinrsmith.jblob.api.JBlob;
import com.devinrsmith.jblob.api.JBlobMeta;
import com.google.common.hash.HashCode;
import com.google.common.io.ByteSource;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
* Created by dsmith on 2/24/15.
*/
public class JBlobKeylessImpl implements JBlobKeyless {
    private final JBlob jblob;
    private final JBlobHasher hasher;
    private final JBlobDeduper deduper;
    private final JBlobKeyGenerator encoder;
    private final JBlobContentTyper typer;

    public JBlobKeylessImpl(JBlob jblob, JBlobHasher hasher, JBlobDeduper deduper, JBlobKeyGenerator encoder, JBlobContentTyper typer) {
        this.jblob = jblob;
        this.hasher = hasher;
        this.deduper = deduper;
        this.encoder = encoder;
        this.typer = typer;
    }

    @Override
    public String upload(ByteSource source, Map<String, String> properties) throws IOException, InterruptedException {
        final HashCode hashCode = hasher.computeHash(source);
        final Optional<String> existingKey = deduper.findExistingKey(hashCode, source);
        if (existingKey.isPresent()) {
            return existingKey.get();
        }

        if (properties.get(HttpHeaders.CONTENT_TYPE) == null) {
            typer.computeContentType(source).ifPresent(type -> properties.put(HttpHeaders.CONTENT_TYPE, type));
        }

        final String key = encoder.generateKey(hashCode);
        try (final InputStream in = source.openBufferedStream()) {
            jblob.upload(key, in, properties);
        }
        deduper.putNewKey(hashCode, key);
        return key;
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
