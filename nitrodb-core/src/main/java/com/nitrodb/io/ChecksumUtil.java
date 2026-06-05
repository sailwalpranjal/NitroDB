package com.nitrodb.io;

import com.nitrodb.api.DBException.CorruptionException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32C;

public final class ChecksumUtil {

    private ChecksumUtil() {
    }

    public static int compute(byte[] data) {
        CRC32C crc32c = new CRC32C();
        crc32c.update(data, 0, data.length);
        return (int) crc32c.getValue();
    }

    public static int compute(ByteBuffer buffer, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > buffer.limit()) {
            throw new IllegalArgumentException("offset/length out of bounds");
        }
        CRC32C crc32c = new CRC32C();
        ByteBuffer duplicate = buffer.duplicate();
        duplicate.position(offset);
        duplicate.limit(offset + length);
        crc32c.update(duplicate);
        return (int) crc32c.getValue();
    }

    public static void validate(byte[] data, int expected) {
        int actual = compute(data);
        if (actual != expected) {
            throw new CorruptionException(
                    "CRC32C mismatch: expected=%s actual=%s".formatted(
                            Integer.toUnsignedString(expected),
                            Integer.toUnsignedString(actual)));
        }
    }
}
