package com.devinrsmith.jblob.api;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Created by dsmith on 2/21/15.
 */
public interface JBlobMeta {
    static JBlobMeta of(long length, String contentType, Map<String, String> properties) {
        return new JBlobMetaImpl(length, contentType, properties);
    }

    long getContentLength();
    Optional<String> getContentType();
    Map<String, String> getProperties();

    static class JBlobMetaImpl implements JBlobMeta {
        private final long length;
        private final String contentType;
        private final Map<String, String> properties;

        private JBlobMetaImpl(long length, String contentType, Map<String, String> properties) {
            this.length = length;
            this.contentType = contentType;
            this.properties = properties == null ? Collections.emptyMap() : properties;
        }

        @Override
        public long getContentLength() {
            return length;
        }

        @Override
        public Optional<String> getContentType() {
            return Optional.ofNullable(contentType);
        }

        @Override
        public Map<String, String> getProperties() {
            return properties;
        }
    }
}
