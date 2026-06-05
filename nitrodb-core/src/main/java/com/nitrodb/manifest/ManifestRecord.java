package com.nitrodb.manifest;

public record ManifestRecord(ManifestRecordType type, ManifestEntry payload, int crc) {

    public enum ManifestRecordType {
        ADD_SSTABLE,
        REMOVE_SSTABLE,
        SET_FLUSHED_SEQ,
        COMPACTION_START,
        COMPACTION_END
    }
}
