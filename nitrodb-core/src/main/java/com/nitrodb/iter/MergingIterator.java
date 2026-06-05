package com.nitrodb.iter;

import com.nitrodb.memtable.MemTableEntry;
import com.nitrodb.mvcc.VersionedKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

public class MergingIterator implements InternalIterator {

    private final List<InternalIterator> iterators;
    private final PriorityQueue<HeapEntry> heap;

    public MergingIterator(List<? extends InternalIterator> iterators) {
        this.iterators = new ArrayList<>(iterators);
        this.heap = new PriorityQueue<>(Comparator.comparing(entry -> new VersionedKey(entry.current.key(), entry.current.sequenceNumber())));
        primeHeap();
    }

    @Override
    public void seek(byte[] key, long maxSeq) {
        heap.clear();
        for (int i = 0; i < iterators.size(); i++) {
            InternalIterator iterator = iterators.get(i);
            iterator.seek(key, maxSeq);
        }
        primeHeap();
    }

    private void primeHeap() {
        heap.clear();
        for (int i = 0; i < iterators.size(); i++) {
            InternalIterator iterator = iterators.get(i);
            if (iterator.hasNext()) {
                heap.offer(new HeapEntry(i, iterator.next()));
            }
        }
    }

    @Override
    public boolean hasNext() {
        return !heap.isEmpty();
    }

    @Override
    public MemTableEntry next() {
        if (heap.isEmpty()) {
            throw new NoSuchElementException();
        }
        HeapEntry entry = heap.poll();
        InternalIterator source = iterators.get(entry.sourceIndex);
        if (source.hasNext()) {
            heap.offer(new HeapEntry(entry.sourceIndex, source.next()));
        }
        return entry.current;
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (InternalIterator iterator : iterators) {
            try {
                iterator.close();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        heap.clear();
        if (failure != null) {
            throw failure;
        }
    }

    protected record HeapEntry(int sourceIndex, MemTableEntry current) {
    }
}
