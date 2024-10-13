package com.fdu.msacs.dfs.bfs;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import static org.junit.jupiter.api.Assertions.*;

public class BlockSchemaTest {

    private static final String TEST_HASH = "a".repeat(64); // 64 characters for SHA-256
    private static final long TEST_OFFSET = 1024L;
    private static final int TEST_REFERENCE_COUNT = 5;
    private static final int TEST_SIZE = 256;
    private static final boolean TEST_ENCRYPTED = true;
    private static final long TEST_CREATED_TIMESTAMP = System.currentTimeMillis();
    private static final long TEST_MODIFIED_TIMESTAMP = System.currentTimeMillis();

    @Test
    public void testConstructorAndGetters() {
        BlockSchema schema = new BlockSchema(TEST_HASH, TEST_OFFSET, TEST_REFERENCE_COUNT, TEST_SIZE, TEST_ENCRYPTED, TEST_CREATED_TIMESTAMP, TEST_MODIFIED_TIMESTAMP);
        
        assertEquals(TEST_HASH, schema.getHash());
        assertEquals(TEST_OFFSET, schema.getOffset());
        assertEquals(TEST_REFERENCE_COUNT, schema.getReferenceCount());
        assertEquals(TEST_SIZE, schema.getSize());
        assertTrue(schema.isEncrypted());
        assertEquals(TEST_CREATED_TIMESTAMP, schema.getCreatedTimestamp());
        assertEquals(TEST_MODIFIED_TIMESTAMP, schema.getModifiedTimestamp());
    }

    @Test
    public void testWriteAndReadFrom() throws IOException {
        // Create a ByteArrayOutputStream to hold the data
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        // Write multiple BlockSchema objects to the output stream
        try (RandomAccessFile raf = new RandomAccessFile("temp.dat", "rw")) {
            for (int i = 0; i < 5; i++) {
                String hash = String.format("%064d", i); // Create a unique hash
                long offset = TEST_OFFSET + (i * 100); // Increment offset for each block
                BlockSchema schema = new BlockSchema(hash, offset, TEST_REFERENCE_COUNT + i, TEST_SIZE + (i * 10), TEST_ENCRYPTED, TEST_CREATED_TIMESTAMP, TEST_MODIFIED_TIMESTAMP);
                schema.writeTo(raf);
            }
            raf.seek(0); // Reset the pointer to the beginning of the file for reading
            // Read the schemas back from the file
            for (int i = 0; i < 5; i++) {
                BlockSchema readSchema = BlockSchema.readFrom(raf);
                String expectedHash = String.format("%064d", i); // Expected hash for comparison
                long expectedOffset = TEST_OFFSET + (i * 100); // Expected offset for comparison
                assertEquals(expectedHash, readSchema.getHash());
                assertEquals(expectedOffset, readSchema.getOffset());
                assertEquals(TEST_REFERENCE_COUNT + i, readSchema.getReferenceCount());
                assertEquals(TEST_SIZE + (i * 10), readSchema.getSize());
                assertTrue(readSchema.isEncrypted());
                assertEquals(TEST_CREATED_TIMESTAMP, readSchema.getCreatedTimestamp());
                assertEquals(TEST_MODIFIED_TIMESTAMP, readSchema.getModifiedTimestamp());
            }
        }
        
        // Clean up the temporary file
        new java.io.File("temp.dat").delete();
    }

    @Test
    public void testGetSerializedSize() {
        int expectedSize = BlockSchema.getSerializedSize();
        assertEquals(expectedSize, BlockSchema.getSerializedSize());
    }

    @Test
    public void testSetters() {
        BlockSchema schema = new BlockSchema(TEST_HASH, TEST_OFFSET, TEST_REFERENCE_COUNT, TEST_SIZE, TEST_ENCRYPTED, TEST_CREATED_TIMESTAMP, TEST_MODIFIED_TIMESTAMP);
        
        // Update fields
        schema.setSize(512);
        schema.setReferenceCount(10);
        schema.setOffset(2048L);

        // Validate the updates
        assertEquals(512, schema.getSize());
        assertEquals(10, schema.getReferenceCount());
        assertEquals(2048L, schema.getOffset());
    }

    @Test
    public void testReadingFromInvalidData() {
        // This test can be extended to cover scenarios where reading from an invalid
        // file or buffer would throw an exception.
        assertThrows(IOException.class, () -> {
            RandomAccessFile raf = new RandomAccessFile("invalidFile.txt", "r");
            BlockSchema.readFrom(raf);
        });
    }
}
