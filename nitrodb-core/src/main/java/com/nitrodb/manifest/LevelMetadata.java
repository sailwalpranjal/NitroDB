package com.nitrodb.manifest;

import com.google.common.primitives.UnsignedBytes;
import com.nitrodb.sstable.SSTableMetadata;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LevelMetadata {

    private static final java.util.Comparator<byte[]> KEY_COMPARATOR = UnsignedBytes.lexicographicalComparator();

    private final int level;
    private final List<SSTableMetadata> files;

    public LevelMetadata(int level, List<SSTableMetadata> files) {
        this.level = level;
        this.files = Collections.unmodifiableList(new ArrayList<>(files));
    }

    public int level() {
        return level;
    }

    public List<SSTableMetadata> files() {
        return files;
    }

    public List<SSTableMetadata> overlapping(byte[] minKey, byte[] maxKey) {
        List<SSTableMetadata> overlapping = new ArrayList<>();
        for (SSTableMetadata metadata : files) {
            boolean overlap = KEY_COMPARATOR.compare(metadata.maxKey(), minKey) >= 0
                    && KEY_COMPARATOR.compare(metadata.minKey(), maxKey) <= 0;
            if (overlap) {
                overlapping.add(metadata);
            }
        }
        return overlapping;
    }

    public long totalBytes() {
        return files.stream().mapToLong(SSTableMetadata::fileSize).sum();
    }

    public int fileCount() {
        return files.size();
    }
}
