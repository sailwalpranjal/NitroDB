package com.nitrodb.manifest;

import com.google.common.primitives.UnsignedBytes;
import com.nitrodb.io.ChecksumUtil;
import com.nitrodb.io.FileManager;
import com.nitrodb.sstable.SSTableMetadata;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public final class ManifestManager implements AutoCloseable {

    private static final java.util.Comparator<byte[]> KEY_COMPARATOR = UnsignedBytes.lexicographicalComparator();

    private final FileManager fileManager = new FileManager();
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<Integer, List<SSTableMetadata>> levels = new HashMap<>();

    private Path manifestPath;
    private FileChannel channel;
    private long flushedSequence;

    public void open(Path dataDir) {
        lock.lock();
        try {
            Files.createDirectories(dataDir);
            manifestPath = dataDir.resolve("MANIFEST.mf");
            channel = FileChannel.open(
                    manifestPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
            if (channel.size() == 0L) {
                writeHeader();
            } else {
                validateHeader();
            }
            rebuildState();
        } catch (IOException e) {
            throw new com.nitrodb.api.DBException.IOStorageException("Failed to open manifest", e);
        } finally {
            lock.unlock();
        }
    }

    public LevelMetadata getLevel(int level) {
        lock.lock();
        try {
            return new LevelMetadata(level, levels.getOrDefault(level, List.of()));
        } finally {
            lock.unlock();
        }
    }

    public List<SSTableMetadata> getAllSSTables() {
        lock.lock();
        try {
            return levels.values().stream().flatMap(List::stream).toList();
        } finally {
            lock.unlock();
        }
    }

    public long getFlushedSequence() {
        lock.lock();
        try {
            return flushedSequence;
        } finally {
            lock.unlock();
        }
    }

    public void addSSTable(int level, SSTableMetadata metadata) {
        lock.lock();
        try {
            writeRecord(new ManifestRecord(ManifestRecord.ManifestRecordType.ADD_SSTABLE, ManifestEntry.sstable(level, metadata), 0));
            applyAdd(level, metadata);
        } finally {
            lock.unlock();
        }
    }

    public void applyCompaction(List<SSTableMetadata> remove, List<SSTableMetadata> add) {
        lock.lock();
        try {
            long txId = System.nanoTime();
            writeRecord(new ManifestRecord(
                    ManifestRecord.ManifestRecordType.COMPACTION_START,
                    ManifestEntry.compaction(txId, remove, add),
                    0));
            for (SSTableMetadata metadata : remove) {
                writeRecord(new ManifestRecord(
                        ManifestRecord.ManifestRecordType.REMOVE_SSTABLE,
                        ManifestEntry.sstable(metadata.level(), metadata),
                        0));
                applyRemove(metadata.level(), metadata);
            }
            for (SSTableMetadata metadata : add) {
                writeRecord(new ManifestRecord(
                        ManifestRecord.ManifestRecordType.ADD_SSTABLE,
                        ManifestEntry.sstable(metadata.level(), metadata),
                        0));
                applyAdd(metadata.level(), metadata);
            }
            writeRecord(new ManifestRecord(
                    ManifestRecord.ManifestRecordType.COMPACTION_END,
                    ManifestEntry.transactionEnd(txId),
                    0));
        } finally {
            lock.unlock();
        }
    }

    public void setFlushedSequence(long sequence) {
        lock.lock();
        try {
            writeRecord(new ManifestRecord(
                    ManifestRecord.ManifestRecordType.SET_FLUSHED_SEQ,
                    ManifestEntry.flushedSequence(sequence),
                    0));
            flushedSequence = Math.max(flushedSequence, sequence);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (channel != null) {
                channel.close();
                channel = null;
            }
        } catch (IOException e) {
            throw new com.nitrodb.api.DBException.IOStorageException("Failed to close manifest", e);
        } finally {
            lock.unlock();
        }
    }

    private void rebuildState() throws IOException {
        levels.clear();
        flushedSequence = 0L;
        channel.position(ManifestConstants.HEADER_SIZE);
        Map<Long, ManifestEntry> pendingCompactions = new HashMap<>();
        while (channel.position() < channel.size()) {
            ByteBuffer header = ByteBuffer.allocate(1 + Integer.BYTES);
            int read = channel.read(header);
            if (read == -1 || read == 0) {
                break;
            }
            if (read < header.capacity()) {
                break;
            }
            header.flip();
            ManifestRecord.ManifestRecordType type = ManifestRecord.ManifestRecordType.values()[Byte.toUnsignedInt(header.get())];
            int payloadLength = header.getInt();
            ByteBuffer payloadBuffer = ByteBuffer.allocate(payloadLength);
            if (channel.read(payloadBuffer) < payloadLength) {
                break;
            }
            byte[] payloadBytes = payloadBuffer.array();
            ByteBuffer crcBuffer = ByteBuffer.allocate(Integer.BYTES);
            if (channel.read(crcBuffer) < Integer.BYTES) {
                break;
            }
            crcBuffer.flip();
            ChecksumUtil.validate(frame(type, payloadBytes), crcBuffer.getInt());
            ManifestEntry entry = decodeEntry(type, payloadBytes);
            switch (type) {
                case ADD_SSTABLE -> applyAdd(entry.level(), entry.metadata());
                case REMOVE_SSTABLE -> applyRemove(entry.level(), entry.metadata());
                case SET_FLUSHED_SEQ -> flushedSequence = Math.max(flushedSequence, entry.sequence());
                case COMPACTION_START -> pendingCompactions.put(entry.transactionId(), entry);
                case COMPACTION_END -> pendingCompactions.remove(entry.transactionId());
            }
        }
        for (ManifestEntry pending : pendingCompactions.values()) {
            for (SSTableMetadata metadata : pending.addFiles()) {
                applyRemove(metadata.level(), metadata);
            }
            for (SSTableMetadata metadata : pending.removeFiles()) {
                applyAdd(metadata.level(), metadata);
            }
        }
    }

    private void writeHeader() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(ManifestConstants.HEADER_SIZE);
        header.putLong(ManifestConstants.MAGIC);
        header.putShort(ManifestConstants.VERSION);
        header.put(new byte[6]);
        header.flip();
        channel.write(header, 0L);
        channel.force(true);
    }

    private void validateHeader() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(ManifestConstants.HEADER_SIZE);
        channel.read(header, 0L);
        header.flip();
        if (header.getLong() != ManifestConstants.MAGIC || header.getShort() != ManifestConstants.VERSION) {
            throw new com.nitrodb.api.DBException.CorruptionException("Invalid manifest header");
        }
    }

    private void writeRecord(ManifestRecord record) {
        try {
            byte[] payload = encodeEntry(record.type(), record.payload());
            byte[] frame = frame(record.type(), payload);
            int crc = ChecksumUtil.compute(frame);
            ByteBuffer buffer = ByteBuffer.allocate(frame.length + Integer.BYTES);
            buffer.put(frame);
            buffer.putInt(crc);
            buffer.flip();
            channel.write(buffer, channel.size());
            channel.force(false);
        } catch (IOException e) {
            throw new com.nitrodb.api.DBException.IOStorageException("Failed to write manifest record", e);
        }
    }

    private byte[] encodeEntry(ManifestRecord.ManifestRecordType type, ManifestEntry entry) {
        return switch (type) {
            case ADD_SSTABLE, REMOVE_SSTABLE -> encodeMetadataEntry(entry.level(), entry.metadata());
            case SET_FLUSHED_SEQ -> {
                ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
                buffer.putLong(entry.sequence());
                yield buffer.array();
            }
            case COMPACTION_START -> encodeCompactionEntry(entry.transactionId(), entry.removeFiles(), entry.addFiles());
            case COMPACTION_END -> {
                ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
                buffer.putLong(entry.transactionId());
                yield buffer.array();
            }
        };
    }

    private ManifestEntry decodeEntry(ManifestRecord.ManifestRecordType type, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        return switch (type) {
            case ADD_SSTABLE, REMOVE_SSTABLE -> {
                int level = buffer.getInt();
                yield ManifestEntry.sstable(level, decodeMetadata(buffer, level));
            }
            case SET_FLUSHED_SEQ -> ManifestEntry.flushedSequence(buffer.getLong());
            case COMPACTION_START -> decodeCompactionEntry(buffer);
            case COMPACTION_END -> ManifestEntry.transactionEnd(buffer.getLong());
        };
    }

    private byte[] encodeMetadataEntry(int level, SSTableMetadata metadata) {
        byte[] fileId = metadata.fileId().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] path = metadata.filePath().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int size = Integer.BYTES * 7 + fileId.length + path.length + metadata.minKey().length + metadata.maxKey().length
                + Long.BYTES * 6;
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.putInt(level);
        putBytes(buffer, fileId);
        putBytes(buffer, path);
        buffer.putLong(metadata.fileSize());
        putBytes(buffer, metadata.minKey());
        putBytes(buffer, metadata.maxKey());
        buffer.putLong(metadata.entryCount());
        buffer.putLong(metadata.minSeq());
        buffer.putLong(metadata.maxSeq());
        buffer.putLong(metadata.bloomOffset());
        buffer.putInt(metadata.bloomLength());
        buffer.putLong(metadata.indexOffset());
        buffer.putInt(metadata.indexLength());
        return buffer.array();
    }

    private SSTableMetadata decodeMetadata(ByteBuffer buffer, int level) {
        String fileId = new String(readBytes(buffer), java.nio.charset.StandardCharsets.UTF_8);
        Path path = Path.of(new String(readBytes(buffer), java.nio.charset.StandardCharsets.UTF_8));
        long fileSize = buffer.getLong();
        byte[] minKey = readBytes(buffer);
        byte[] maxKey = readBytes(buffer);
        long entryCount = buffer.getLong();
        long minSeq = buffer.getLong();
        long maxSeq = buffer.getLong();
        long bloomOffset = buffer.getLong();
        int bloomLength = buffer.getInt();
        long indexOffset = buffer.getLong();
        int indexLength = buffer.getInt();
        return new SSTableMetadata(path, level, fileSize, minKey, maxKey, entryCount, minSeq, maxSeq, bloomOffset, bloomLength, indexOffset, indexLength, fileId);
    }

    private byte[] encodeCompactionEntry(long txId, List<SSTableMetadata> removeFiles, List<SSTableMetadata> addFiles) {
        List<byte[]> removePayloads = removeFiles.stream().map(file -> encodeMetadataEntry(file.level(), file)).toList();
        List<byte[]> addPayloads = addFiles.stream().map(file -> encodeMetadataEntry(file.level(), file)).toList();
        int size = Long.BYTES + Integer.BYTES;
        size += removePayloads.stream().mapToInt(bytes -> Integer.BYTES + bytes.length).sum();
        size += Integer.BYTES;
        size += addPayloads.stream().mapToInt(bytes -> Integer.BYTES + bytes.length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.putLong(txId);
        buffer.putInt(removePayloads.size());
        for (byte[] removePayload : removePayloads) {
            buffer.putInt(removePayload.length);
            buffer.put(removePayload);
        }
        buffer.putInt(addPayloads.size());
        for (byte[] addPayload : addPayloads) {
            buffer.putInt(addPayload.length);
            buffer.put(addPayload);
        }
        return buffer.array();
    }

    private ManifestEntry decodeCompactionEntry(ByteBuffer buffer) {
        long txId = buffer.getLong();
        List<SSTableMetadata> removeFiles = readMetadataList(buffer);
        List<SSTableMetadata> addFiles = readMetadataList(buffer);
        return ManifestEntry.compaction(txId, removeFiles, addFiles);
    }

    private List<SSTableMetadata> readMetadataList(ByteBuffer buffer) {
        int count = buffer.getInt();
        List<SSTableMetadata> files = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] payload = new byte[buffer.getInt()];
            buffer.get(payload);
            ByteBuffer nested = ByteBuffer.wrap(payload);
            int level = nested.getInt();
            files.add(decodeMetadata(nested, level));
        }
        return files;
    }

    private byte[] frame(ManifestRecord.ManifestRecordType type, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + Integer.BYTES + payload.length);
        buffer.put((byte) type.ordinal());
        buffer.putInt(payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    private void applyAdd(int level, SSTableMetadata metadata) {
        levels.computeIfAbsent(level, ignored -> new ArrayList<>());
        List<SSTableMetadata> current = new ArrayList<>(levels.get(level));
        current.removeIf(existing -> existing.fileId().equals(metadata.fileId()));
        current.add(metadata);
        current.sort((left, right) -> {
            int keyComparison = KEY_COMPARATOR.compare(left.minKey(), right.minKey());
            return keyComparison != 0 ? keyComparison : left.fileId().compareTo(right.fileId());
        });
        levels.put(level, current);
    }

    private void applyRemove(int level, SSTableMetadata metadata) {
        List<SSTableMetadata> current = new ArrayList<>(levels.getOrDefault(level, List.of()));
        current.removeIf(existing -> existing.fileId().equals(metadata.fileId()));
        levels.put(level, current);
    }

    private static void putBytes(ByteBuffer buffer, byte[] bytes) {
        buffer.putInt(bytes.length);
        buffer.put(bytes);
    }

    private static byte[] readBytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.getInt()];
        buffer.get(bytes);
        return bytes;
    }
}
