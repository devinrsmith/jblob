package com.devinrsmith.jblob.api.keyless;

import com.google.common.io.ByteSource;

import java.io.IOException;
import java.util.Optional;

/**
 * Created by dsmith on 2/24/15.
 */
public interface JBlobContentTyper {
    Optional<String> computeContentType(ByteSource source) throws IOException;

    static enum Null implements JBlobContentTyper {
        NULL;
        @Override
        public Optional<String> computeContentType(ByteSource source) throws IOException {
            return Optional.empty();
        }
    }
}
