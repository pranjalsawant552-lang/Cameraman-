package com.cameraman.storage.provider;

import java.io.IOException;
import java.io.InputStream;

/** A readable stored object. The caller MUST close it. {@code length} is -1 when unknown. */
public record StoredObject(InputStream stream, String contentType, long length) implements AutoCloseable {
    @Override
    public void close() throws IOException {
        stream.close();
    }
}
