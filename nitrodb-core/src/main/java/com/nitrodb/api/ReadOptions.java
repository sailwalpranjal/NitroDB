package com.nitrodb.api;

import com.nitrodb.mvcc.Snapshot;

public record ReadOptions(Snapshot snapshot) {

    public static final ReadOptions DEFAULT = new ReadOptions(null);

    public boolean hasSnapshot() {
        return snapshot != null;
    }
}
