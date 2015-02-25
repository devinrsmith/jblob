package com.devinrsmith.jblob.api.keyless;

import com.google.common.hash.HashCode;
import com.google.common.io.ByteSource;

import java.io.IOException;
import java.util.Optional;

/**
 * Created by dsmith on 2/24/15.
 */
public interface JBlobDeduper {
    /**
     * May or may not use source in determining whether sources are equal
     */
    Optional<String> findExistingKey(HashCode hashCode, ByteSource source) throws IOException;
    void putNewKey(HashCode hashCode, String key);
}
