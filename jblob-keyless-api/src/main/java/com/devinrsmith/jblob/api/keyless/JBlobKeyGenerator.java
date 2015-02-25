package com.devinrsmith.jblob.api.keyless;

import com.google.common.hash.HashCode;

/**
 * Created by dsmith on 2/24/15.
 */
public interface JBlobKeyGenerator {
    /**
     * generate a deterministic key based off of the hashCode
     */
    String generateKey(HashCode hashCode);
}
