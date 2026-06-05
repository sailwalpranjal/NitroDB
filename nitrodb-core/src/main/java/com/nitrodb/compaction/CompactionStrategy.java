package com.nitrodb.compaction;

import com.nitrodb.DBConfig;
import com.nitrodb.manifest.LevelMetadata;
import java.util.List;
import java.util.Optional;

public interface CompactionStrategy {

    double score(int level, LevelMetadata metadata, DBConfig config);

    boolean needsCompaction(int level, LevelMetadata metadata, DBConfig config);

    Optional<CompactionJob> selectJob(List<LevelMetadata> levels, DBConfig config);
}
