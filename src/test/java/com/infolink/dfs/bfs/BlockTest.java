package com.infolink.dfs.bfs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.infolink.dfs.bfs.Block;
import com.infolink.dfs.bfs.BlockSchema;
import com.infolink.dfs.bfs.Encryptor;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;

class BlockTest {
    private static final String TEST_HASH = "a".repeat(64); // 64 characters for SHA-256
    private static final byte[] TEST_DATA = "Test block data".getBytes();
    private Block block;
    private Encryptor mockEncryptor;

    @BeforeEach
    public void setUp() throws NoSuchAlgorithmException {
        // Create a mock Encryptor
        mockEncryptor = new Encryptor() {
            @Override
            public byte[] encrypt(byte[] data) {
                // Simple "encryption": just reverse the byte array for testing
                byte[] encrypted = new byte[data.length];
                for (int i = 0; i < data.length; i++) {
                    encrypted[i] = data[data.length - 1 - i];
                }
                return encrypted;
            }

            @Override
            public byte[] decrypt(byte[] data) {
                // Decrypt by reversing back to original
                byte[] decrypted = new byte[data.length];
                for (int i = 0; i < data.length; i++) {
                    decrypted[i] = data[data.length - 1 - i];
                }
                return decrypted;
            }
        };

        // Initialize a Block instance
        block = new Block(TEST_DATA, true, TEST_HASH);
    }

    @Test
    public void testConstructorAndGetters() {
        assertEquals(TEST_HASH, block.getSchema().getHash());
        assertEquals(TEST_DATA.length, block.getSize());
        assertTrue(block.isEncrypted());
        assertArrayEquals(TEST_DATA, block.getData());
    }

    @Test
    public void testEncrypt() throws IOException {
        // Encrypt the block data
        block.encrypt(mockEncryptor);

        // Check if the data is encrypted (reversed in this mock implementation)
        byte[] expectedEncryptedData = mockEncryptor.encrypt(TEST_DATA);
        assertArrayEquals(expectedEncryptedData, block.getData());
        assertEquals(expectedEncryptedData.length, block.getSize());
    }

    @Test
    public void testDecrypt() throws IOException {
        // First, encrypt the block data
        block.encrypt(mockEncryptor);
        
        // Now, decrypt the data
        block.decrypt(mockEncryptor);

        // The decrypted data should match the original data
        assertArrayEquals(TEST_DATA, block.getData());
        assertEquals(TEST_DATA.length, block.getSize());
    }

    @Test
    public void testWriteToAndReadFromFile() throws IOException, NoSuchAlgorithmException {
        // Create a temporary file to write and read the block
        try (RandomAccessFile raf = new RandomAccessFile("tempBlock.dat", "rw")) {
            // Write block data to the file
            block.getSchema().setEncrypted(false);
            block.writeTo(raf);
            raf.seek(0); // Reset the pointer to the beginning for reading

            // Create a new block instance to read data
            BlockSchema schema = block.getSchema();
            Block readBlock = new Block(schema, new byte[schema.getSize()]); // Create block with schema
            
            // Read from the file
            readBlock.readFromFile(raf, mockEncryptor);
            
            // Validate that the read data matches the original data
            assertArrayEquals(TEST_DATA, readBlock.getData());
        }

        // Clean up the temporary file
        new java.io.File("tempBlock.dat").delete();
    }

    @Test
    public void testReadFromFileWithEncryption() throws IOException, NoSuchAlgorithmException {
        // First, encrypt the block
        //block.encrypt(mockEncryptor);
        
        // Create a temporary file to write the encrypted block
        try (RandomAccessFile raf = new RandomAccessFile("tempEncryptedBlock.dat", "rw")) {
            // Write block data to the file
            block.writeTo(raf);
            raf.seek(0); // Reset the pointer to the beginning for reading

            // Create a new block instance to read data
            BlockSchema schema = block.getSchema();
            Block readBlock = new Block(schema, new byte[schema.getSize()]); // Create block with schema
            
            // Read from the file
            readBlock.readFromFile(raf, mockEncryptor);
            
            // Validate that the read data matches the encrypted data
            assertArrayEquals(block.getData(), readBlock.getData());
        }

        // Clean up the temporary file
        new java.io.File("tempEncryptedBlock.dat").delete();
    }
}
