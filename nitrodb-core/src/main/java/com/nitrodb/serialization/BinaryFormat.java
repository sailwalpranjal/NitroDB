package com.nitrodb.serialization;

public final class BinaryFormat {

    public static final int SEQ_OFFSET = 0;
    public static final int SEQ_SIZE = Long.BYTES;
    public static final int TYPE_OFFSET = SEQ_OFFSET + SEQ_SIZE;
    public static final int TYPE_SIZE = 1;
    public static final int KEY_LEN_OFFSET = TYPE_OFFSET + TYPE_SIZE;
    public static final int KEY_LEN_SIZE = Integer.BYTES;
    public static final int KEY_DATA_OFFSET = KEY_LEN_OFFSET + KEY_LEN_SIZE;
    public static final int VALUE_LEN_SIZE = Integer.BYTES;
    public static final int MIN_RECORD_SIZE = KEY_DATA_OFFSET + VALUE_LEN_SIZE;

    private BinaryFormat() {
    }

    public static int valueLengthOffset(int keyLength) {
        requireNonNegative(keyLength, "keyLength");
        return KEY_DATA_OFFSET + keyLength;
    }

    public static int valueDataOffset(int keyLength) {
        return valueLengthOffset(keyLength) + VALUE_LEN_SIZE;
    }

    public static int encodedRecordSize(int keyLength, int valueLength) {
        requireNonNegative(keyLength, "keyLength");
        requireNonNegative(valueLength, "valueLength");
        return valueDataOffset(keyLength) + valueLength;
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
    }
}
