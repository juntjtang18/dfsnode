package com.infolink.dfs.bfs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import com.infolink.dfs.Config;
import com.infolink.dfs.bfs.BlockStorage;
import com.infolink.dfs.bfs.Encryptor;
import com.infolink.dfs.tobedelete.FileController;

import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BlockStorageTest {
    private static final Logger logger = LoggerFactory.getLogger(BlockStorageTest.class);
	
    @Autowired
    private BlockStorage blockStorage;
    
    @Autowired
    private Config config;
    
    @Autowired
    private RedisTemplate<String, Long> redisTemplate;
    
    private Encryptor mockEncryptor;
    private String rootDir;
    private String containerUrl;
    
    @PostConstruct
    public void postConstruct() {
    	this.containerUrl = config.getContainerUrl(); 
    }
    @BeforeEach
    void setUp() throws IOException {
        // Set up a temporary directory for testing
        rootDir = Files.createTempDirectory("blockStorageTest").toString();
        mockEncryptor = Mockito.mock(Encryptor.class);
        //blockStorage = new BlockStorage(mockEncryptor, rootDir);
        clearRedisEntries();
        blockStorage.clearFiles();
    }

    @Test
    void testStoreBlockWithoutEncryption() throws IOException, NoSuchAlgorithmException {
        String hash = "abc1234567890abcdef1234567890abc";
        byte[] blockData = "Test Data".getBytes();

        // Store the block
        blockStorage.saveBlock(hash, blockData, false);

        // Verify that the block was stored correctly
        byte[] readData = blockStorage.readBlock(hash);
        assertArrayEquals(blockData, readData, "The data read from storage should match the stored data.");

        // Clean up the stored files
        blockStorage.deleteBlock(hash);
    }

    @Test
    void testStoreBlockWithEncryption() throws IOException, NoSuchAlgorithmException {
        String hash = "def1234567890abcdef1234567890abc";
        byte[] blockData = "Test Data".getBytes();

        // Mock the encryptor behavior
        byte[] encryptedData = "Encrypted Test Data".getBytes();
        Mockito.when(mockEncryptor.encrypt(blockData)).thenReturn(encryptedData);
        Mockito.when(mockEncryptor.decrypt(encryptedData)).thenReturn(blockData);

        // Store the block with encryption
        blockStorage.saveBlock(hash, blockData, true);

        // Verify that the block was stored correctly
        byte[] readData = blockStorage.readBlock(hash);
        assertArrayEquals(blockData, readData, "The data read from storage should match the stored data after decryption.");

        // Clean up the stored files
        blockStorage.deleteBlock(hash);
    }

    @Test
    void testBlockStorageWithReferenceCount() throws IOException, NoSuchAlgorithmException {
        // Define two hashes with the same first 8 characters
        String hashB1 = "abcdef12000000000000000000000001";
        String hashB2 = "abcdef12000000000000000000000002";
        byte[] dataB1 = "Block B1 Data".getBytes();
        byte[] dataB2 = "Block B2 Data".getBytes();

        // Store the first block (b1)
        blockStorage.saveBlock(hashB1, dataB1, false);

        // Store the second block (b2)
        blockStorage.saveBlock(hashB2, dataB2, false);
        // Verify block count and total size
        
        long blockCount = blockStorage.getBlockCount();
        long totalSize = blockStorage.getBlockTotalSize();
        long length = dataB1.length + dataB2.length;
        assertEquals(2, blockCount);
        assertEquals(length, totalSize);

        // Verify Redis values
        assertEquals(Long.valueOf(2), redisTemplate.opsForValue().get("block_storage:" + config.getContainerUrl() + ":blockCount"));
        assertEquals(length, redisTemplate.opsForValue().get("block_storage:" + config.getContainerUrl() + ":totalSize"));

        // Verify that both blocks can be read correctly and referenceCount starts at 1
        byte[] readDataB1 = blockStorage.readBlock(hashB1);
        byte[] readDataB2 = blockStorage.readBlock(hashB2);
        assertArrayEquals(dataB1, readDataB1, "The data read for B1 should match the stored data.");
        assertArrayEquals(dataB2, readDataB2, "The data read for B2 should match the stored data.");

        // Check that both blocks have referenceCount of 1
        assertEquals(1, blockStorage.getReferenceCount(hashB1), "Reference count for B1 should be 1.");
        assertEquals(1, blockStorage.getReferenceCount(hashB2), "Reference count for B2 should be 1.");

        // Store block B2 again to simulate another reference
        blockStorage.saveBlock(hashB2, dataB2, false);
        
        assertEquals(2L, redisTemplate.opsForValue().get("block_storage:" + config.getContainerUrl() + ":blockCount"));
        assertEquals(length, redisTemplate.opsForValue().get("block_storage:" + config.getContainerUrl() + ":totalSize"));

        // Verify that referenceCount for B2 is now 2
        assertEquals(2, blockStorage.getReferenceCount(hashB2), "Reference count for B2 should be 2 after storing it again.");

        // Delete B2 once and verify the referenceCount decreases to 1
        blockStorage.deleteBlock(hashB2);
        assertEquals(1, blockStorage.getReferenceCount(hashB2), "Reference count for B2 should be 1 after one deletion.");

        // Delete B2 again and verify the referenceCount decreases to 0
        blockStorage.deleteBlock(hashB2);
        assertEquals(0, blockStorage.getReferenceCount(hashB2), "Reference count for B2 should be 0 after the second deletion.");

        // Try deleting B2 again and verify that the referenceCount stays at 0
        blockStorage.deleteBlock(hashB2);
        assertEquals(0, blockStorage.getReferenceCount(hashB2), "Reference count for B2 should remain 0 after attempting to delete again.");

        // Clean up the stored files for B1 and B2 to ensure no residual data
        blockStorage.deleteBlock(hashB1);
        blockStorage.deleteBlock(hashB2);
        
        

        // Verify Redis values
        blockCount = blockStorage.getBlockCount();
        totalSize = blockStorage.getBlockTotalSize();
        assertEquals(0, blockCount);
        assertEquals(0, totalSize);
        assertEquals(0L, redisTemplate.opsForValue().get("block_storage:" + config.getContainerUrl() + ":blockCount"));
        assertEquals(0, redisTemplate.opsForValue().get("block_storage:" + config.getContainerUrl() + ":totalSize"));

    }

    @Test
    void testStoreMultipleBlocksInSameFile() throws IOException, NoSuchAlgorithmException {
        // Hashes and data for multiple blocks sharing a common prefix
        String[] hashes = {
            "abcdef01data1",  // prefix: "abcdef01"
            "abcdef02data2",  // prefix: "abcdef02"
            "abcdef03data3"   // prefix: "abcdef03"
        };

        byte[][] blockDataArray = {
            "Block 1 Data".getBytes(),
            "Block 2 Data".getBytes(),
            "Block 3 Data".getBytes()
        };

        // Store multiple blocks
        for (int i = 0; i < hashes.length; i++) {
            blockStorage.saveBlock(hashes[i], blockDataArray[i], false);
        }

        // Read back each block and verify data
        for (int i = 0; i < hashes.length; i++) {
            byte[] readData = blockStorage.readBlock(hashes[i]);
            assertArrayEquals(blockDataArray[i], readData, "The data read from storage should match the stored data for hash: " + hashes[i]);
        }

        // Clean up the stored files
        for (String hash : hashes) {
            blockStorage.deleteBlock(hash);
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up the temporary directory
        File dir = new File(rootDir);
        if (dir.exists()) {
            for (File file : dir.listFiles()) {
                file.delete();
            }
            dir.delete();
        }
    }
    
    public void clearRedisEntries() {
    	
        String pattern = BlockStorage.BLOCK_STORAGE_PREFIX + containerUrl + ":*"; // Pattern for all keys related to the current container
        Set<String> keys = redisTemplate.keys(pattern); // Get keys matching the pattern

        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys); // Delete the keys from Redis
            logger.debug("Deleted Redis keys matching pattern '{}': {}", pattern, keys);
        } else {
            logger.debug("No Redis keys found matching pattern '{}'", pattern);
        }
    }
}
