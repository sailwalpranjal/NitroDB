package com.nitrodb.sstable;

public record BlockHandle(long offset, int length) {
}
