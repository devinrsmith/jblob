package com.devinrsmith.jblob.api.keyless;

import com.google.common.hash.HashCode;
import com.google.common.io.ByteSource;

import java.io.IOException;

/**
 * Created by dsmith on 2/24/15.
 */
public interface JBlobHasher {
    HashCode computeHash(ByteSource source) throws IOException;
}
