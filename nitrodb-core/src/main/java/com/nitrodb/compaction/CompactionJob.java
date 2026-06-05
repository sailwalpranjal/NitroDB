package com.nitrodb.compaction;

import com.nitrodb.sstable.SSTableMetadata;
import java.util.List;

public record CompactionJob(
        int sourceLevel,
        List<SSTableMetadata> sourceFiles,
        int targetLevel,
        List<SSTableMetadata> targetFiles,
        long minSeqForGc) {
}
