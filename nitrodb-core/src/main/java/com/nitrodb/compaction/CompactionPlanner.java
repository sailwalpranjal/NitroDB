package com.nitrodb.compaction;

import com.google.common.primitives.UnsignedBytes;
import com.nitrodb.DBConfig;
import com.nitrodb.manifest.LevelMetadata;
import com.nitrodb.sstable.SSTableMetadata;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class CompactionPlanner {

    private static final java.util.Comparator<byte[]> KEY_COMPARATOR = UnsignedBytes.lexicographicalComparator();

    private final LeveledCompactionStrategy strategy;
    private final DBConfig config;

    public CompactionPlanner(DBConfig config) {
        this.strategy = new LeveledCompactionStrategy();
        this.config = config;
    }

    public Optional<CompactionJob> selectCompaction(List<LevelMetadata> levels, long minSeqForGc) {
        Optional<CompactionJob> selected = strategy.selectJob(levels, config);
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        CompactionJob job = selected.orElseThrow();
        LevelMetadata targetLevel = levels.stream()
                .filter(level -> level.level() == job.targetLevel())
                .findFirst()
                .orElse(new LevelMetadata(job.targetLevel(), List.of()));
        byte[] minKey = job.sourceFiles().stream()
                .map(SSTableMetadata::minKey)
                .min(KEY_COMPARATOR)
                .orElse(new byte[0]);
        byte[] maxKey = job.sourceFiles().stream()
                .map(SSTableMetadata::maxKey)
                .max(KEY_COMPARATOR)
                .orElse(new byte[0]);
        List<SSTableMetadata> targetFiles = findOverlappingFiles(minKey, maxKey, targetLevel);
        return Optional.of(new CompactionJob(job.sourceLevel(), job.sourceFiles(), job.targetLevel(), targetFiles, minSeqForGc));
    }

    private List<SSTableMetadata> findOverlappingFiles(byte[] minKey, byte[] maxKey, LevelMetadata targetLevel) {
        List<SSTableMetadata> overlapping = new ArrayList<>(targetLevel.overlapping(minKey, maxKey));
        overlapping.sort(Comparator.comparingLong(SSTableMetadata::maxSeq).reversed());
        return overlapping;
    }
}
