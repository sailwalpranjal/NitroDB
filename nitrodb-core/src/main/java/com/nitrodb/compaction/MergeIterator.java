package com.nitrodb.compaction;

import com.nitrodb.iter.InternalIterator;
import com.nitrodb.iter.MergingIterator;
import java.util.List;

public final class MergeIterator extends MergingIterator {

    public MergeIterator(List<? extends InternalIterator> iterators) {
        super(iterators);
    }
}
