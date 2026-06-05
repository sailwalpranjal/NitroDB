package com.nitrodb.wal;

import com.nitrodb.api.DBException.CorruptionException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;

public final class WalSegment implements AutoCloseable {

    private final Path path;
    private final FileChannel channel;
    private final AtomicLong sizeBytes;

    public WalSegment(Path path) {
        try {
            Files.createDirectories(path.getParent());
            this.path = path;
            this.channel = FileChannel.open(
                    path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
            this.sizeBytes = new AtomicLong(channel.size());
            if (sizeBytes.get() == 0L) {
                ByteBuffer header = ByteBuffer.allocate(WalConstants.HEADER_SIZE);
                header.putLong(WalConstants.MAGIC);
                header.putShort(WalConstants.VERSION);
                header.put(new byte[6]);
                header.flip();
                channel.write(header, 0L);
                channel.force(true);
                sizeBytes.set(WalConstants.HEADER_SIZE);
            } else {
                validateHeader();
            }
        } catch (IOException e) {
            throw new com.nitrodb.api.DBException.IOStorageException("Failed to open WAL segment " + path, e);
        }
    }

    public void write(byte[] data) {
        try {
            channel.write(ByteBuffer.wrap(data), sizeBytes.get());
            sizeBytes.addAndGet(data.length);
        } catch (IOException e) {
            throw new com.nitrodb.api.DBException.IOStorageException("Failed to write WAL segment " + path, e);
        }
    }

    public void force(boolean metadata) {
        try {
            channel.force(metadata);
        } catch (IOException e) {
            throw new com.nitrodb.api.DBException.IOStorageException("Failed to fsync WAL segment " + path, e);
        }
    }

    public long size() {
        return sizeBytes.get();
    }

    public Path path() {
        return path;
    }

    @Override
    public void close() {
        try {
            channel.close();
        } catch (IOException e) {
            throw new com.nitrodb.api.DBException.IOStorageException("Failed to close WAL segment " + path, e);
        }
    }

    private void validateHeader() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(WalConstants.HEADER_SIZE);
        channel.read(header, 0L);
        header.flip();
        long magic = header.getLong();
        short version = header.getShort();
        if (magic != WalConstants.MAGIC || version != WalConstants.VERSION) {
            throw new CorruptionException("Invalid WAL header in " + path);
        }
    }
}
