package com.nitrodb.compaction;

import com.nitrodb.DBConfig;
import com.nitrodb.manifest.LevelMetadata;
import com.nitrodb.sstable.SSTableMetadata;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class LeveledCompactionStrategy implements CompactionStrategy {

    private static final int L0_FILE_THRESHOLD = 4;

    @Override
    public double score(int level, LevelMetadata metadata, DBConfig config) {
        if (level == 0) {
            return (double) metadata.fileCount() / L0_FILE_THRESHOLD;
        }
        return (double) metadata.totalBytes() / targetBytesForLevel(level, config);
    }

    @Override
    public boolean needsCompaction(int level, LevelMetadata metadata, DBConfig config) {
        return score(level, metadata, config) >= 1.0d;
    }

    @Override
    public Optional<CompactionJob> selectJob(List<LevelMetadata> levels, DBConfig config) {
        double bestScore = 1.0d;
        CompactionJob bestJob = null;
        for (LevelMetadata levelMetadata : levels) {
            int level = levelMetadata.level();
            if (level >= config.maxLevels() - 1 || !needsCompaction(level, levelMetadata, config)) {
                continue;
            }
            double score = score(level, levelMetadata, config);
            if (score < bestScore) {
                continue;
            }
            List<SSTableMetadata> sourceFiles = level == 0
                    ? new ArrayList<>(levelMetadata.files())
                    : levelMetadata.files().isEmpty()
                            ? List.of()
                            : List.of(levelMetadata.files().get(0));
            if (sourceFiles.isEmpty()) {
                continue;
            }
            bestScore = score;
            bestJob = new CompactionJob(level, sourceFiles, level + 1, List.of(), Long.MAX_VALUE);
        }
        return Optional.ofNullable(bestJob);
    }

    public long targetBytesForLevel(int level, DBConfig config) {
        if (level <= 1) {
            return config.l1MaxBytes();
        }
        long target = config.l1MaxBytes();
        for (int i = 2; i <= level; i++) {
            target *= config.levelSizeMultiplier();
        }
        return target;
    }
}
