package com.cameraman.storage.provider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the bytes of an upload come from. A source that can be opened more than once
 * ({@link #replayable()}) lets a provider retry a failed transfer; a one-shot stream cannot.
 * File and byte[] sources are also handed to the S3 SDK in their native form, which avoids
 * buffering a 100 MB photo in memory.
 */
public final class ContentSource {
    private final byte[] bytes;
    private final Path path;
    private final InputStream stream;

    private ContentSource(byte[] bytes, Path path, InputStream stream) {
        this.bytes = bytes;
        this.path = path;
        this.stream = stream;
    }

    public static ContentSource ofBytes(byte[] bytes) { return new ContentSource(bytes, null, null); }
    public static ContentSource ofPath(Path path) { return new ContentSource(null, path, null); }
    public static ContentSource ofStream(InputStream stream) { return new ContentSource(null, null, stream); }

    public byte[] bytes() { return bytes; }
    public Path path() { return path; }
    public boolean replayable() { return stream == null; }

    public InputStream open() throws IOException {
        if (bytes != null) return new ByteArrayInputStream(bytes);
        if (path != null) return Files.newInputStream(path);
        return stream;
    }
}
