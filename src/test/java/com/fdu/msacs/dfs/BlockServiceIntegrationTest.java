package com.fdu.msacs.dfs;

import com.fdu.msacs.dfs.bfs.BlockStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class BlockServiceIntegrationTest {

    @Autowired
    private BlockService blockService;

    @Autowired
    private BlockStorage blockStorage;

    //private final String metaNodeUrl = "http://localhost:8080";
    private final String testHash = "testhash12345";
    private final byte[] testBlock = "This is a test block".getBytes();

    @BeforeEach
    public void setUp() {
        // Perform any setup tasks here if needed.
    }

    @AfterEach
    public void tearDown() {
        // Clean up any test data, such as removing test blocks saved locally.
        File blockFile = new File(blockStorage.getBlockFilePath(testHash));
        if (blockFile.exists()) {
            blockFile.delete();
        }

        // Unregister the block from the meta node for cleanup.
        blockService.unregisterBlock(testHash);
    }

    @Test
    public void testCheckAndStoreBlock_WhenBlockDoesNotExist() throws NoSuchAlgorithmException, IOException {
        // Step 1: Ensure the block does not exist on the meta node
        blockService.unregisterBlock(testHash);

        // Step 2: Try to store the block using BlockService
        boolean stored = blockService.checkAndStoreBlock(testHash, testBlock);

        // Step 3: Verify that the block was stored and registered.
        assertTrue(stored, "Block should have been stored and registered.");

        // Step 4: Verify that the block was saved locally
        File blockFile = new File(blockStorage.getBlockFilePath(testHash));
        assertTrue(blockFile.exists(), "Block should have been saved locally.");
    }

    @Test
    public void testCheckAndStoreBlock_WhenBlockAlreadyExists() throws NoSuchAlgorithmException, IOException {
        // Step 1: Store the block initially
        blockService.checkAndStoreBlock(testHash, testBlock);

        // Step 2: Attempt to store the block again
        boolean storedAgain = blockService.checkAndStoreBlock(testHash, testBlock);

        // Step 3: Verify that the block was not stored again
        assertFalse(storedAgain, "Block should not be stored again if it already exists.");
    }
}
