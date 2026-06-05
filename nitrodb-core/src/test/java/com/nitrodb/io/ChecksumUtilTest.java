package com.nitrodb.io;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nitrodb.api.DBException.CorruptionException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class ChecksumUtilTest {

    @Test
    void computesSameChecksumForArrayAndBuffer() {
        byte[] bytes = "nitrodb-checksum".getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        assertEquals(ChecksumUtil.compute(bytes), ChecksumUtil.compute(buffer, 0, buffer.remaining()));
    }

    @Test
    void validateRejectsCorruption() {
        byte[] bytes = "nitrodb-checksum".getBytes();
        int checksum = ChecksumUtil.compute(bytes);
        bytes[0] = (byte) (bytes[0] + 1);

        assertThrows(CorruptionException.class, () -> ChecksumUtil.validate(bytes, checksum));
    }

    @Test
    void validateAcceptsMatchingPayload() {
        byte[] bytes = "nitrodb-checksum".getBytes();
        int checksum = ChecksumUtil.compute(bytes);

        assertDoesNotThrow(() -> ChecksumUtil.validate(bytes, checksum));
    }
}
