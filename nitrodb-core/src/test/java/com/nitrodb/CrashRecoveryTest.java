package com.nitrodb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nitrodb.api.DBException.CorruptionException;
import com.nitrodb.wal.WalConstants;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrashRecoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void restartRecoversWalAndDeletesTombstones() {
        NitroDBImpl db = (NitroDBImpl) new NitroDBBuilder().dataDir(tempDir).memTableSize(1024 * 1024).build();
        db.put(bytes("alpha"), bytes("one"));
        db.put(bytes("beta"), bytes("two"));
        db.delete(bytes("beta"));
        db.simulateCrashForTesting();

        try (NitroDB reopened = new NitroDBBuilder().dataDir(tempDir).build()) {
            assertArrayEquals(bytes("one"), reopened.get(bytes("alpha")).orElseThrow());
            assertFalse(reopened.get(bytes("beta")).isPresent());
        }
    }

    @Test
    void lenientRecoveryStopsAtCorruptTail() throws IOException {
        NitroDBImpl db = (NitroDBImpl) new NitroDBBuilder()
                .dataDir(tempDir)
                .walCorruptionPolicy(DBConfig.WalCorruptionPolicy.LENIENT)
                .memTableSize(1024 * 1024)
                .build();
        db.put(bytes("alpha"), bytes("one"));
        db.put(bytes("beta"), bytes("two"));
        db.simulateCrashForTesting();
        corruptSecondFrameCrc(tempDir.resolve("wal").resolve("00000000000000000001.wal"));

        try (NitroDB reopened = new NitroDBBuilder()
                .dataDir(tempDir)
                .walCorruptionPolicy(DBConfig.WalCorruptionPolicy.LENIENT)
                .build()) {
            assertTrue(reopened.get(bytes("alpha")).isPresent());
        }
    }

    @Test
    void strictRecoveryFailsOnCorruptTail() throws IOException {
        NitroDBImpl db = (NitroDBImpl) new NitroDBBuilder()
                .dataDir(tempDir)
                .walCorruptionPolicy(DBConfig.WalCorruptionPolicy.STRICT)
                .memTableSize(1024 * 1024)
                .build();
        db.put(bytes("alpha"), bytes("one"));
        db.simulateCrashForTesting();
        corruptTailCrc(tempDir.resolve("wal").resolve("00000000000000000001.wal"));

        org.junit.jupiter.api.Assertions.assertThrows(
                CorruptionException.class,
                () -> new NitroDBBuilder()
                        .dataDir(tempDir)
                        .walCorruptionPolicy(DBConfig.WalCorruptionPolicy.STRICT)
                        .build());
    }

    @Test
    void startupDeletesDanglingTempFiles() throws IOException {
        Path sstDir = tempDir.resolve("sst");
        Files.createDirectories(sstDir);
        Files.writeString(sstDir.resolve("orphan.tmp"), "partial");

        try (NitroDB ignored = new NitroDBBuilder().dataDir(tempDir).build()) {
            assertFalse(Files.exists(sstDir.resolve("orphan.tmp")));
        }
    }

    private static void corruptTailCrc(Path walPath) throws IOException {
        try (FileChannel channel = FileChannel.open(walPath, StandardOpenOption.WRITE)) {
            ByteBuffer crc = ByteBuffer.allocate(Integer.BYTES);
            crc.putInt(0);
            crc.flip();
            channel.write(crc, WalConstants.HEADER_SIZE);
        }
    }

    private static void corruptSecondFrameCrc(Path walPath) throws IOException {
        try (FileChannel channel = FileChannel.open(walPath, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer header = ByteBuffer.allocate(WalConstants.FRAME_HEADER_SIZE);
            channel.read(header, WalConstants.HEADER_SIZE);
            header.flip();
            header.getInt();
            int payloadLength = header.getInt();
            long secondFrameOffset = WalConstants.HEADER_SIZE + WalConstants.FRAME_HEADER_SIZE + payloadLength;
            ByteBuffer crc = ByteBuffer.allocate(Integer.BYTES);
            crc.putInt(0);
            crc.flip();
            channel.write(crc, secondFrameOffset);
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
