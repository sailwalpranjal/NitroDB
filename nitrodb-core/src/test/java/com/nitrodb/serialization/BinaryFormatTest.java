package com.nitrodb.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BinaryFormatTest {

    @Test
    void recordOffsetsAreInternallyConsistent() {
        assertEquals(0, BinaryFormat.SEQ_OFFSET);
        assertEquals(8, BinaryFormat.SEQ_SIZE);
        assertEquals(8, BinaryFormat.TYPE_OFFSET);
        assertEquals(1, BinaryFormat.TYPE_SIZE);
        assertEquals(9, BinaryFormat.KEY_LEN_OFFSET);
        assertEquals(4, BinaryFormat.KEY_LEN_SIZE);
        assertEquals(13, BinaryFormat.KEY_DATA_OFFSET);
        assertEquals(17, BinaryFormat.MIN_RECORD_SIZE);
    }

    @Test
    void computesVariableOffsetsCorrectly() {
        assertEquals(16, BinaryFormat.valueLengthOffset(3));
        assertEquals(20, BinaryFormat.valueDataOffset(3));
        assertEquals(27, BinaryFormat.encodedRecordSize(3, 7));
    }

    @Test
    void rejectsNegativeLengths() {
        assertThrows(IllegalArgumentException.class, () -> BinaryFormat.valueLengthOffset(-1));
        assertThrows(IllegalArgumentException.class, () -> BinaryFormat.encodedRecordSize(1, -1));
    }
}
