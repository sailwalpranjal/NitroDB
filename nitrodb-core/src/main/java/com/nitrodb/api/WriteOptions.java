package com.nitrodb.api;

public record WriteOptions(SyncMode syncMode) {

    public static final WriteOptions DEFAULT = new WriteOptions(null);

    public boolean hasSyncOverride() {
        return syncMode != null;
    }

    public enum SyncMode {
        SYNC,
        ASYNC
    }
}
