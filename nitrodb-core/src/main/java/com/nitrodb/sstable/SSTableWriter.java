package com.nitrodb.sstable;

import com.google.common.primitives.UnsignedBytes;
import com.nitrodb.DBConfig;
import com.nitrodb.bloom.BloomFilter;
import com.nitrodb.bloom.BloomFilterBuilder;
import com.nitrodb.bloom.BloomFilterSerializer;
import com.nitrodb.index.SparseIndexBuilder;
import com.nitrodb.io.ChecksumUtil;
import com.nitrodb.io.FileManager;
import com.nitrodb.memtable.MemTableEntry;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public final class SSTableWriter implements AutoCloseable {

    private static final java.util.Comparator<byte[]> KEY_COMPARATOR = UnsignedBytes.lexicographicalComparator();

    private final Path dir;
    private final int level;
    private final DBConfig config;
    private final FileManager fileManager;
    private final Path tempFile;
    private final Path finalFile;
    private final FileChannel channel;
    private final BlockBuilder blockBuilder;
    private final SparseIndexBuilder sparseIndexBuilder = new SparseIndexBuilder();
    private final BloomFilterBuilder bloomFilterBuilder = BloomFilterBuilder.build(1_024, 0.01d);
    private final String fileId = UUID.randomUUID().toString();

    private long currentOffset = SSTableConstants.HEADER_SIZE;
    private long entryCount;
    private long minSeq = Long.MAX_VALUE;
    private long maxSeq = Long.MIN_VALUE;
    private byte[] minKey;
    private byte[] maxKey;
    private byte[] lastKeyAdded;
    private boolean closed;

    private SSTableWriter(Path dir, int level, DBConfig config, FileManager fileManager, Path tempFile, Path finalFile, FileChannel channel) {
        this.dir = dir;
        this.level = level;
        this.config = config;
        this.fileManager = fileManager;
        this.tempFile = tempFile;
        this.finalFile = finalFile;
        this.channel = channel;
        this.blockBuilder = new BlockBuilder(config.blockSizeBytes());
        writeHeader();
    }

    public static SSTableWriter open(Path dir, int level, DBConfig config) {
        try {
            FileManager fileManager = new FileManager();
            Path sstableDir = dir.resolve("sst");
            Path tempFile = fileManager.createTempFile(sstableDir, "L" + level + "-", ".tmp");
            String fileId = UUID.randomUUID().toString();
            Path finalFile = sstableDir.resolve("L%d-%s.sst".formatted(level, fileId));
            FileChannel channel = FileChannel.open(tempFile, StandardOpenOption.WRITE, StandardOpenOption.READ);
            return new SSTableWriter(dir, level, config, fileManager, tempFile, finalFile, channel);
        } catch (IOException e) {
            throw new com.nitrodb.api.DBException.IOStorageException("Failed to open SSTable writer", e);
        }
    }

    public void add(byte[] key, byte[] value, long seq, MemTableEntry.EntryType type) {
        if (blockBuilder.hasEntries()
                && blockBuilder.isFull()
                && KEY_COMPARATOR.compare(lastKeyAdded == null ? key : lastKeyAdded, key) != 0) {
            flushBlock();
        }
        blockBuilder.add(key, value, seq, type);
        bloomFilterBuilder.add(key);
        entryCount++;
        lastKeyAdded = key.clone();
        minSeq = Math.min(minSeq, seq);
        maxSeq = Math.max(maxSeq, seq);
        minKey = minKey == null || KEY_COMPARATOR.compare(key, minKey) < 0 ? key.clone() : minKey;
        maxKey = maxKey == null || KEY_COMPARATOR.compare(key, maxKey) > 0 ? key.clone() : maxKey;
    }

    public SSTableMetadata finish() {
        if (closed) {
            throw new IllegalStateException("SSTableWriter is already closed");
        }
        try {
            if (blockBuilder.hasEntries()) {
                flushBlock();
            }
            BloomFilter bloomFilter = bloomFilterBuilder.finish();
            BloomFilterSerializer bloomSerializer = new BloomFilterSerializer();
            byte[] bloomBytes = bloomSerializer.serialize(bloomFilter);
            long bloomOffset = currentOffset;
            channel.write(ByteBuffer.wrap(bloomBytes), currentOffset);
            currentOffset += bloomBytes.length;

            byte[] indexBytes = sparseIndexBuilder.serialize();
            long indexOffset = currentOffset;
            channel.write(ByteBuffer.wrap(indexBytes), currentOffset);
            currentOffset += indexBytes.length;

            SSTableFooter footer = new SSTableFooter(
                    bloomOffset,
                    bloomBytes.length,
                    indexOffset,
                    indexBytes.length,
                    entryCount,
                    minSeq == Long.MAX_VALUE ? 0L : minSeq,
                    maxSeq == Long.MIN_VALUE ? 0L : maxSeq,
                    minKey == null ? new byte[0] : minKey,
                    maxKey == null ? new byte[0] : maxKey,
                    0);
            byte[] footerBytes = footer.encode();
            channel.write(ByteBuffer.wrap(footerBytes), currentOffset);
            currentOffset += footerBytes.length;
            ByteBuffer trailer = ByteBuffer.allocate(Integer.BYTES + Long.BYTES);
            trailer.putInt(footerBytes.length);
            trailer.putLong(SSTableConstants.FOOTER_MAGIC);
            trailer.flip();
            channel.write(trailer, currentOffset);
            currentOffset += trailer.capacity();
            channel.force(true);
            channel.close();
            fileManager.atomicRename(tempFile, finalFile);
            closed = true;
            return new SSTableMetadata(
                    finalFile,
                    level,
                    currentOffset,
                    minKey == null ? new byte[0] : minKey,
                    maxKey == null ? new byte[0] : maxKey,
                    entryCount,
                    minSeq == Long.MAX_VALUE ? 0L : minSeq,
                    maxSeq == Long.MIN_VALUE ? 0L : maxSeq,
                    bloomOffset,
                    bloomBytes.length,
                    indexOffset,
                    indexBytes.length,
                    finalFile.getFileName().toString().replace(".sst", ""));
        } catch (IOException e) {
            throw new com.nitrodb.api.DBException.IOStorageException("Failed to finish SSTable writer", e);
        }
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                channel.close();
            } catch (IOException e) {
                throw new com.nitrodb.api.DBException.IOStorageException("Failed to close SSTable writer", e);
            }
            closed = true;
        }
    }

    private void writeHeader() {
        try {
            ByteBuffer header = ByteBuffer.allocate(SSTableConstants.HEADER_SIZE);
            header.putLong(SSTableConstants.MAGIC);
            header.putShort(SSTableConstants.VERSION);
            header.put(new byte[6]);
            header.flip();
            channel.write(header, 0L);
        } catch (IOException e) {
            throw new com.nitrodb.api.DBException.IOStorageException("Failed to write SSTable header", e);
        }
    }

    private void flushBlock() {
        try {
            byte[] blockData = blockBuilder.finish();
            int crc = ChecksumUtil.compute(blockData);
            ByteBuffer frame = ByteBuffer.allocate(SSTableConstants.BLOCK_HEADER_SIZE + blockData.length);
            frame.putInt(crc);
            frame.putInt(blockData.length);
            frame.put(blockData);
            frame.flip();
            channel.write(frame, currentOffset);
            sparseIndexBuilder.add(blockBuilder.firstKey(), currentOffset, frame.capacity());
            currentOffset += frame.capacity();
            blockBuilder.reset();
        } catch (IOException e) {
            throw new com.nitrodb.api.DBException.IOStorageException("Failed to flush SSTable block", e);
        }
    }
}
