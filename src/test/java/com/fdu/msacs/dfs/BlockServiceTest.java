package com.fdu.msacs.dfs;

import com.fdu.msacs.dfs.bfs.BlockStorage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BlockServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(BlockServiceTest.class);

    @Autowired
    private BlockService blockService;

    @Autowired
    private BlockStorage blockStorage;

    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private Config config;

    private byte[] testBlock;
    private String testHash;

    @BeforeEach
    public void setUp() throws NoSuchAlgorithmException, IOException {
        // Initialize test data
    	String url = config.getMetaNodeUrl() + "/metadata/clear-all-block-nodes-mapping";
        /*
    	restTemplate.exchange(
                url,
                HttpMethod.DELETE, // Assuming POST is the correct method for this endpoint
                null,
                String.class
            );
        //clearRegisteredNodes();
        testBlock = "testBlockData".getBytes();
        testHash = blockService.checkAndStoreBlock(testBlock); // Store block and get the hash
        */
    }
    
    // Utility method to clear registered nodes
    private void clearRegisteredNodes() {
        ResponseEntity<String> clearNodesResponse = restTemplate.postForEntity(config.getMetaNodeUrl() + "/metadata/clear-registered-nodes", null, String.class);
        assertEquals(200, clearNodesResponse.getStatusCodeValue());
        assertEquals("Registered nodes cleared.", clearNodesResponse.getBody());
    }

    //@Test
    public void testStoreBlockLocally_Success() throws NoSuchAlgorithmException, IOException {
        // Given: A test block and its hash
        String hash = HashUtil.calculateHash(testBlock);

        // When: The block is stored locally
        String returnedHash = blockService.storeBlockLocally(hash, testBlock, false);

        // Then: Validate that the stored block is not null and matches the original data
        byte[] storedBlock = blockStorage.readBlock(returnedHash);
        assertNotNull(storedBlock, "Stored block should not be null");
        assertArrayEquals(testBlock, storedBlock, "Stored block data should match the original data");

        // Verify that the block's location is registered in the metadata node
        ResponseEntity<List<String>> response = restTemplate.exchange(
        	    config.getMetaNodeUrl() + "/metadata/block-nodes/{hash}",
        	    HttpMethod.GET,
        	    null,
        	    new ParameterizedTypeReference<List<String>>() {},
        	    returnedHash
        	);
        List<String> nodes = response.getBody();
     // Assertions to ensure the response is valid
        assertNotNull(nodes, "Nodes should not be null");
        assertFalse(nodes.isEmpty(), "Nodes list should not be empty");

        // Verify that the current node URL is among the registered nodes
        String currentNodeUrl = config.getContainerUrl();
        boolean isNodeRegistered = nodes.contains(currentNodeUrl);
        assertTrue(isNodeRegistered, "The current node should be registered for the block");
    }

    //@Test
    public void testCheckAndStoreBlock_Success() throws NoSuchAlgorithmException, IOException {
        // Calculate the hash of the stored block
        String hash = blockService.checkAndStoreBlock(testBlock);

        // Validate that the block is stored correctly
        byte[] storedBlock = blockStorage.readBlock(hash);
        assertNotNull(storedBlock, "Stored block should not be null");
        assertArrayEquals(testBlock, storedBlock, "Stored block data should match the original data");
        
        // Verify that the block is registered in the metadata node
        ResponseEntity<List<String>> response = restTemplate.exchange(
        	    config.getMetaNodeUrl() + "/metadata/block-nodes/{hash}",
        	    HttpMethod.GET,
        	    null,
        	    new ParameterizedTypeReference<List<String>>() {},
        	    hash
        	);
        List<String> nodes = response.getBody();
        
        assertNotNull(nodes, "Nodes should not be null");
        boolean isNodeRegistered = nodes.contains(config.getContainerUrl());
        assertTrue(isNodeRegistered, "The current node should be registered for the block");
    }

    @Test
    public void testRegisterBlockLocation_Success() {
        // Given: A hash for a block that should be registered
    	testHash = "testHash";
        String hash = testHash;

        // When: Registering the block's location
        blockService.registerBlockLocation(hash);

        // Then: Verify that the block's location is correctly registered in the metadata node
        String url = config.getMetaNodeUrl() + "/metadata/block-nodes/" + hash;
        logger.info("url to get block node mapping: {}", url);
        
        ResponseEntity<List<String>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<String>>() {}
        );

        // Extract the list of registered nodes from the response
        List<String> nodes = response.getBody();

        // Assertions to ensure the response is valid
        assertNotNull(nodes, "Nodes should not be null");
        assertFalse(nodes.isEmpty(), "Nodes list should not be empty");

        // Verify that the current node URL is among the registered nodes
        String currentNodeUrl = config.getContainerUrl();
        boolean isNodeRegistered = nodes.contains(currentNodeUrl);

        assertTrue(isNodeRegistered, "The current node should be registered for the block");
    }

    @Test
    public void testUnregisterBlock_Success() {
        // Unregister the previously stored block
    	testHash = "testHash";
        blockService.unregisterBlock(testHash);

        // Validate that the block is unregistered
        ResponseEntity<String> response = restTemplate.exchange(
            config.getMetaNodeUrl() + "/metadata/unregister-block/" + testHash,
            HttpMethod.DELETE,
            null,
            String.class
        );

        //assertEquals("Block unregistered successfully", response.getBody(), "Expected unregistration message");
    }


}
