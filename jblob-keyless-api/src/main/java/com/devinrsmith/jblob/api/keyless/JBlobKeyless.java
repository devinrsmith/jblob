package com.devinrsmith.jblob.api.keyless;

import com.devinrsmith.jblob.api.JBlob;
import com.google.common.io.ByteSource;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * This interface is a key-less uploader of blobs
 *
 * If the content has already been uploaded, these new methods may return the existing key.
 * (In these situations, the properties may or may not be updated)
 */
public interface JBlobKeyless extends JBlob {
    String upload(ByteSource source, Map<String, String> properties) throws IOException, InterruptedException;

    default String upload(ByteSource source) throws IOException, InterruptedException {
        return upload(source, Collections.emptyMap());
    }
}
