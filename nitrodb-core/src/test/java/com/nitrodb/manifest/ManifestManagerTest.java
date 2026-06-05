package com.nitrodb.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nitrodb.api.DBException.CorruptionException;
import com.nitrodb.sstable.SSTableMetadata;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void reopensAndRestoresState() {
        ManifestManager manifest = new ManifestManager();
        manifest.open(tempDir);
        manifest.addSSTable(0, metadata(tempDir.resolve("sst").resolve("L0-a.sst"), 0, "L0-a"));
        manifest.setFlushedSequence(99L);
        manifest.close();

        ManifestManager reopened = new ManifestManager();
        reopened.open(tempDir);
        try {
            assertEquals(1, reopened.getLevel(0).fileCount());
            assertEquals(99L, reopened.getFlushedSequence());
        } finally {
            reopened.close();
        }
    }

    @Test
    void rejectsCorruptedChecksums() throws IOException {
        ManifestManager manifest = new ManifestManager();
        manifest.open(tempDir);
        manifest.addSSTable(0, metadata(tempDir.resolve("sst").resolve("L0-a.sst"), 0, "L0-a"));
        manifest.close();

        Path manifestPath = tempDir.resolve("MANIFEST.mf");
        try (FileChannel channel = FileChannel.open(manifestPath, StandardOpenOption.WRITE)) {
            ByteBuffer crc = ByteBuffer.allocate(Integer.BYTES);
            crc.putInt(0);
            crc.flip();
            channel.write(crc, channel.size() - Integer.BYTES);
        }

        ManifestManager reopened = new ManifestManager();
        assertThrows(CorruptionException.class, () -> reopened.open(tempDir));
    }

    private static SSTableMetadata metadata(Path path, int level, String fileId) {
        return new SSTableMetadata(path, level, 128L, "a".getBytes(), "z".getBytes(), 1L, 1L, 1L, 64L, 16, 80L, 24, fileId);
    }
}
