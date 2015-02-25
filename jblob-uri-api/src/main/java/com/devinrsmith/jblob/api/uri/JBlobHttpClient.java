package com.devinrsmith.jblob.api.uri;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;

/**
 * Created by dsmith on 2/24/15.
 */
public interface JBlobHttpClient {
    static interface Results {
        Map<String, String> getHeaders();
        InputStream openStream() throws IOException;
    }

    // expected to throw IOException on 400+ statuses
    Results executeGet(URI uri) throws IOException;
}
