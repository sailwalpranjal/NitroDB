package com.nitrodb.mvcc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SnapshotManagerTest {

    @Test
    void tracksOldestAndCount() {
        SnapshotManager manager = new SnapshotManager();

        Snapshot first = manager.create(10L);
        Snapshot second = manager.create(20L);

        assertEquals(10L, manager.oldestActiveSequence());
        assertEquals(2, manager.activeSnapshotCount());

        first.close();
        assertEquals(20L, manager.oldestActiveSequence());
        assertEquals(1, manager.activeSnapshotCount());

        second.close();
        assertEquals(Long.MAX_VALUE, manager.oldestActiveSequence());
        assertEquals(0, manager.activeSnapshotCount());
    }
}
