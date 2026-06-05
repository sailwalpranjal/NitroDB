package com.nitrodb.wal;

import com.nitrodb.api.DBException.CorruptionException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class WalReader implements AutoCloseable {

    private final List<Path> walFiles;
    private final com.nitrodb.DBConfig.WalCorruptionPolicy corruptionPolicy;
    private int currentFileIndex = -1;
    private FileChannel currentChannel;
    private long currentPosition;
    private Optional<WalRecord> buffered = null;
    private boolean exhausted;

    public WalReader(List<Path> walFiles) {
        this(walFiles, com.nitrodb.DBConfig.DEFAULT_WAL_CORRUPTION_POLICY);
    }

    public WalReader(List<Path> walFiles, com.nitrodb.DBConfig.WalCorruptionPolicy corruptionPolicy) {
        this.walFiles = new ArrayList<>(walFiles);
        this.corruptionPolicy = corruptionPolicy;
    }

    public boolean hasNext() {
        if (buffered != null) {
            return buffered.isPresent();
        }
        buffered = readInternal();
        return buffered.isPresent();
    }

    public Optional<WalRecord> readNext() {
        if (buffered != null) {
            Optional<WalRecord> result = buffered;
            buffered = null;
            return result;
        }
        return readInternal();
    }

    @Override
    public void close() {
        if (currentChannel != null) {
            try {
                currentChannel.close();
            } catch (IOException e) {
                throw new com.nitrodb.api.DBException.IOStorageException("Failed to close WAL reader", e);
            } finally {
                currentChannel = null;
            }
        }
    }

    private Optional<WalRecord> readInternal() {
        if (exhausted) {
            return Optional.empty();
        }
        while (true) {
            if (!ensureOpenFile()) {
                return Optional.empty();
            }
            try {
                ByteBuffer header = ByteBuffer.allocate(WalConstants.FRAME_HEADER_SIZE);
                int headerRead = currentChannel.read(header, currentPosition);
                if (headerRead == -1 || headerRead == 0) {
                    advanceFile();
                    continue;
                }
                if (headerRead < WalConstants.FRAME_HEADER_SIZE) {
                    return handleCorruption("Truncated WAL frame header");
                }
                header.flip();
                int expectedCrc = header.getInt();
                int payloadLength = header.getInt();
                if (payloadLength <= 0) {
                    return handleCorruption("Invalid WAL payload length " + payloadLength);
                }
                ByteBuffer payload = ByteBuffer.allocate(payloadLength);
                int bodyRead = currentChannel.read(payload, currentPosition + WalConstants.FRAME_HEADER_SIZE);
                if (bodyRead < payloadLength) {
                    return handleCorruption("Truncated WAL payload");
                }
                byte[] bytes = payload.array();
                try {
                    com.nitrodb.io.ChecksumUtil.validate(bytes, expectedCrc);
                } catch (CorruptionException e) {
                    return handleCorruption(e.getMessage());
                }
                WalRecord decoded = decodeRecord(bytes);
                currentPosition += WalConstants.FRAME_HEADER_SIZE + payloadLength;
                return Optional.of(decoded);
            } catch (CorruptionException e) {
                return handleCorruption(e.getMessage());
            } catch (IOException e) {
                throw new com.nitrodb.api.DBException.IOStorageException("Failed to read WAL", e);
            }
        }
    }

    private boolean ensureOpenFile() {
        if (currentChannel != null) {
            return true;
        }
        while (++currentFileIndex < walFiles.size()) {
            Path path = walFiles.get(currentFileIndex);
            try {
                currentChannel = FileChannel.open(path, StandardOpenOption.READ);
                ByteBuffer header = ByteBuffer.allocate(WalConstants.HEADER_SIZE);
                currentChannel.read(header, 0L);
                header.flip();
                if (header.remaining() < WalConstants.HEADER_SIZE) {
                    advanceFile();
                    continue;
                }
                long magic = header.getLong();
                short version = header.getShort();
                if (magic != WalConstants.MAGIC || version != WalConstants.VERSION) {
                    throw new CorruptionException("Invalid WAL header in " + path);
                }
                currentPosition = WalConstants.HEADER_SIZE;
                return true;
            } catch (IOException e) {
                throw new com.nitrodb.api.DBException.IOStorageException("Failed to open WAL file " + path, e);
            }
        }
        return false;
    }

    private void advanceFile() {
        close();
        currentPosition = 0L;
    }

    private WalRecord decodeRecord(byte[] bytes) {
        ByteBuffer payload = ByteBuffer.wrap(bytes);
        WalRecord.RecordType type = WalRecord.RecordType.values()[Byte.toUnsignedInt(payload.get())];
        long sequenceNumber = payload.getLong();
        int keyLength = payload.getInt();
        int valueLength = payload.getInt();
        if (keyLength < 0 || valueLength < 0 || keyLength + valueLength > payload.remaining()) {
            throw new CorruptionException("Invalid WAL record lengths");
        }
        byte[] key = keyLength == 0 ? null : new byte[keyLength];
        if (key != null) {
            payload.get(key);
        }
        byte[] value = valueLength == 0 ? null : new byte[valueLength];
        if (value != null) {
            payload.get(value);
        }
        if (payload.hasRemaining()) {
            throw new CorruptionException("Unexpected trailing bytes in WAL record");
        }
        return new WalRecord(type, key, value, sequenceNumber);
    }

    private Optional<WalRecord> handleCorruption(String message) {
        if (corruptionPolicy == com.nitrodb.DBConfig.WalCorruptionPolicy.STRICT) {
            throw new CorruptionException(message);
        }
        exhausted = true;
        close();
        return Optional.empty();
    }
}
