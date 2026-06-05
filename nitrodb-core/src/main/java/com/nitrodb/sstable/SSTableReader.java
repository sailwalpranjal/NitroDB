package com.nitrodb.sstable;

import com.google.common.primitives.UnsignedBytes;
import com.nitrodb.DBConfig;
import com.nitrodb.api.DBException.CorruptionException;
import com.nitrodb.bloom.BloomFilter;
import com.nitrodb.bloom.BloomFilterSerializer;
import com.nitrodb.cache.BlockCache;
import com.nitrodb.cache.CacheKey;
import com.nitrodb.index.SparseIndex;
import com.nitrodb.io.ChecksumUtil;
import com.nitrodb.memtable.MemTableEntry;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public final class SSTableReader implements AutoCloseable {

    private static final java.util.Comparator<byte[]> KEY_COMPARATOR = UnsignedBytes.lexicographicalComparator();

    private final Path path;
    private final DBConfig config;
    private final FileChannel channel;
    private final SSTableMetadata metadata;
    private final BloomFilter bloomFilter;
    private final SparseIndex sparseIndex;
    private final BlockCache blockCache;

    private SSTableReader(
            Path path,
            DBConfig config,
            FileChannel channel,
            SSTableMetadata metadata,
            BloomFilter bloomFilter,
            SparseIndex sparseIndex,
            BlockCache blockCache) {
        this.path = path;
        this.config = config;
        this.channel = channel;
        this.metadata = metadata;
        this.bloomFilter = bloomFilter;
        this.sparseIndex = sparseIndex;
        this.blockCache = blockCache;
    }

    public static SSTableReader open(Path path, DBConfig config) {
        return open(path, config, null);
    }

    public static SSTableReader open(Path path, DBConfig config, BlockCache blockCache) {
        try {
            FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
            ByteBuffer header = ByteBuffer.allocate(SSTableConstants.HEADER_SIZE);
            channel.read(header, 0L);
            header.flip();
            if (header.getLong() != SSTableConstants.MAGIC || header.getShort() != SSTableConstants.VERSION) {
                throw new CorruptionException("Invalid SSTable header in " + path);
            }

            long fileSize = channel.size();
            ByteBuffer trailer = ByteBuffer.allocate(Integer.BYTES + Long.BYTES);
            channel.read(trailer, fileSize - trailer.capacity());
            trailer.flip();
            int footerLength = trailer.getInt();
            long footerMagic = trailer.getLong();
            if (footerMagic != SSTableConstants.FOOTER_MAGIC) {
                throw new CorruptionException("Invalid SSTable footer trailer in " + path);
            }

            ByteBuffer footerBytes = ByteBuffer.allocate(footerLength);
            channel.read(footerBytes, fileSize - trailer.capacity() - footerLength);
            footerBytes.flip();
            SSTableFooter footer = SSTableFooter.decode(footerBytes);

            byte[] bloomBytes = readBytes(channel, footer.bloomFilterOffset(), footer.bloomFilterLength());
            byte[] indexBytes = readBytes(channel, footer.sparseIndexOffset(), footer.sparseIndexLength());
            BloomFilter bloomFilter = new BloomFilterSerializer().deserialize(bloomBytes);
            SparseIndex sparseIndex = SparseIndex.deserialize(indexBytes);
            String fileId = path.getFileName().toString().replace(".sst", "");
            int level = parseLevel(path);
            SSTableMetadata metadata = new SSTableMetadata(
                    path,
                    level,
                    fileSize,
                    footer.minKey(),
                    footer.maxKey(),
                    footer.entryCount(),
                    footer.minSeq(),
                    footer.maxSeq(),
                    footer.bloomFilterOffset(),
                    footer.bloomFilterLength(),
                    footer.sparseIndexOffset(),
                    footer.sparseIndexLength(),
                    fileId);
            return new SSTableReader(path, config, channel, metadata, bloomFilter, sparseIndex, blockCache);
        } catch (IOException e) {
            throw new com.nitrodb.api.DBException.IOStorageException("Failed to open SSTable " + path, e);
        }
    }

    public Optional<byte[]> get(byte[] key, long maxSeq) {
        return getEntry(key, maxSeq)
                .filter(entry -> !entry.isTombstone())
                .map(MemTableEntry::value);
    }

    public Optional<MemTableEntry> getEntry(byte[] key, long maxSeq) {
        if (KEY_COMPARATOR.compare(key, metadata.minKey()) < 0 || KEY_COMPARATOR.compare(key, metadata.maxKey()) > 0) {
            return Optional.empty();
        }
        if (!bloomFilter.mightContain(key)) {
            return Optional.empty();
        }
        BlockHandle handle = sparseIndex.findBlock(key);
        if (handle == null) {
            return Optional.empty();
        }
        return readBlock(handle).search(key, maxSeq);
    }

    public Block readBlock(BlockHandle handle) {
        CacheKey cacheKey = new CacheKey(metadata.fileId(), handle.offset());
        if (blockCache != null) {
            Optional<ByteBuffer> cached = blockCache.get(cacheKey);
            if (cached.isPresent()) {
                return Block.decode(cached.orElseThrow());
            }
        }
        try {
            ByteBuffer header = ByteBuffer.allocate(SSTableConstants.BLOCK_HEADER_SIZE);
            channel.read(header, handle.offset());
            header.flip();
            int expectedCrc = header.getInt();
            int blockLength = header.getInt();
            ByteBuffer data = ByteBuffer.allocateDirect(blockLength);
            readFully(channel, data, handle.offset() + SSTableConstants.BLOCK_HEADER_SIZE);
            data.flip();
            if (config.checksumVerification()) {
                int actualCrc = ChecksumUtil.compute(data, 0, data.remaining());
                if (actualCrc != expectedCrc) {
                    throw new CorruptionException("CRC32C mismatch for SSTable block in " + path);
                }
            }
            if (blockCache != null) {
                blockCache.put(cacheKey, data.duplicate());
            }
            return Block.decode(data);
        } catch (IOException e) {
            throw new com.nitrodb.api.DBException.IOStorageException("Failed to read SSTable block from " + path, e);
        }
    }

    public SSTableIterator iterator(byte[] fromKey, long maxSeq) {
        return new SSTableIterator(this, fromKey, maxSeq);
    }

    public BloomFilter bloomFilter() {
        return bloomFilter;
    }

    public SparseIndex sparseIndex() {
        return sparseIndex;
    }

    public SSTableMetadata metadata() {
        return metadata;
    }

    @Override
    public void close() {
        try {
            channel.close();
        } catch (IOException e) {
            throw new com.nitrodb.api.DBException.IOStorageException("Failed to close SSTable reader " + path, e);
        }
    }

    private static int parseLevel(Path path) {
        String filename = path.getFileName().toString();
        if (filename.startsWith("L")) {
            int dash = filename.indexOf('-');
            if (dash > 1) {
                return Integer.parseInt(filename.substring(1, dash));
            }
        }
        return 0;
    }

    private static byte[] readBytes(FileChannel channel, long position, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        channel.read(buffer, position);
        return buffer.array();
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        long currentPosition = position;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, currentPosition);
            if (read < 0) {
                throw new CorruptionException("Unexpected end of file while reading SSTable block");
            }
            currentPosition += read;
        }
    }
}
