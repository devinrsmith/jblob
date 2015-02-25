package com.devinrsmith.jblob.api.uri;

import com.devinrsmith.jblob.api.keyless.JBlobKeyless;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Map;

/**
 * This interface is a key-less uploader of blobs that can additionally come from a url
 */
public interface JBlobUri extends JBlobKeyless {
    String upload(URI uri, Map<String, String> properties) throws IOException, InterruptedException;

    default String upload(URI uri) throws IOException, InterruptedException {
        return upload(uri, Collections.emptyMap());
    }
}
