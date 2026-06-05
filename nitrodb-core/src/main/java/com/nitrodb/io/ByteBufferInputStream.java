package com.nitrodb.io;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

public final class ByteBufferInputStream extends InputStream {

    private final ByteBuffer buffer;

    public ByteBufferInputStream(ByteBuffer buffer) {
        this.buffer = Objects.requireNonNull(buffer, "buffer").slice();
    }

    @Override
    public int read() {
        if (!buffer.hasRemaining()) {
            return -1;
        }
        return Byte.toUnsignedInt(buffer.get());
    }

    @Override
    public int read(byte[] bytes, int off, int len) {
        Objects.checkFromIndexSize(off, len, bytes.length);
        if (!buffer.hasRemaining()) {
            return -1;
        }
        int bytesToRead = Math.min(len, buffer.remaining());
        buffer.get(bytes, off, bytesToRead);
        return bytesToRead;
    }

    @Override
    public int available() {
        return buffer.remaining();
    }
}
