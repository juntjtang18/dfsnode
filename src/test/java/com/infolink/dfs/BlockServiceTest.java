package com.infolink.dfs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.infolink.dfs.bfs.BlockStorage;
import com.infolink.dfs.shared.DfsNode;
import com.infolink.dfs.shared.HashUtil;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BlockServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(BlockServiceTest.class);

    @LocalServerPort
    private int port;
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
    private String baseUrl;
    private String metaNodeUrl = "http://localhost:8080";
    @BeforeEach
    public void setUp() throws NoSuchAlgorithmException, IOException {
        baseUrl = "http://localhost:8080";
        restTemplate.exchange(
                config.getMetaNodeUrl() + "/metadata/block/clear-all-block-nodes-mapping",
                HttpMethod.DELETE,
                null,
                String.class
            );        
    }
    
    // Utility method to clear registered nodes
    private void clearRegisteredNodes() {
        ResponseEntity<String> clearNodesResponse = restTemplate.postForEntity(config.getMetaNodeUrl() + "/metadata/clear-registered-nodes", null, String.class);
        assertEquals(200, clearNodesResponse.getStatusCodeValue());
        assertEquals("Registered nodes cleared.", clearNodesResponse.getBody());
    }

    @Test
    public void testStoreBlockLocally_Success() throws NoSuchAlgorithmException, IOException {
        // Given: A test block and its hash
        testBlock = "testBlockData".getBytes();
        String hash = HashUtil.calculateHash(testBlock);
        String requestingNodeUrl = config.getContainerUrl();

        // When: The block is stored locally
        String returnedHash = blockService.storeBlockLocally(hash, testBlock, false);

        // Then: Validate that the stored block is not null and matches the original data
        byte[] storedBlock = blockStorage.readBlock(returnedHash);
        assertNotNull(storedBlock, "Stored block should not be null");
        assertArrayEquals(testBlock, storedBlock, "Stored block data should match the original data");

        // Verify that the block's location is registered in the metadata node
        String url = String.format("%s/metadata/block/block-nodes/%s", config.getMetaNodeUrl(), hash);
        ResponseEntity<List<DfsNode>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<DfsNode>>() {}
        );

        // Check that the response is not null and has a status of 200 OK
        assertNotNull(response.getBody(), "The response body should not be null");
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Expected HTTP status 200");

        // Verify that the node URL is present in the response body
        List<DfsNode> nodes = response.getBody();
        List<String> nodeUrls = new ArrayList<>();
        for (DfsNode node : nodes) {
        	nodeUrls.add(node.getContainerUrl());
        }
        assertTrue(nodeUrls.contains(requestingNodeUrl), "The node URL should be registered in the metadata node");
    }


    @Test
    public void testCheckAndStoreBlock_Success() throws NoSuchAlgorithmException, IOException, Exception {
        // Calculate the hash of the stored block
        testBlock = "testBlockData".getBytes();
        String hash = blockService.checkAndStoreBlock(testBlock);

        // Verify that the block is registered in the metadata node
        ResponseEntity<List<DfsNode>> response = restTemplate.exchange(
                config.getMetaNodeUrl() + "/metadata/block/block-nodes/{hash}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<DfsNode>>() {},
                hash
        );
        List<DfsNode> nodes = response.getBody();
        
        assertNotNull(nodes, "Node URLs should not be null");
        List<String> nodeUrls = new ArrayList<>();
        for (DfsNode node : nodes) {
        	nodeUrls.add(node.getContainerUrl());
        }
        boolean isNodeRegistered = nodeUrls.contains(config.getContainerUrl());
        assertTrue(isNodeRegistered, "The current node should be registered for the block");
        
        // Validate that the block is stored correctly
        // new checkAndStoreBlock could store all blocks to remote DfsNodes, so can't assume it can be read locally.
        String dfsNodeLocalUrls = nodes.get(0).getLocalUrl();
        String nodeUrl = dfsNodeLocalUrls + "/dfs/block/read/" + hash;
        
        String readUrl = "http://localhost:" + port + "/dfs/block/read/" + hash;
        ResponseEntity<byte[]> response2 = restTemplate.exchange(readUrl, HttpMethod.GET, null, byte[].class);
        byte[] storedBlock = response2.getBody();
        assertNotNull(storedBlock, "Stored block should not be null");
        assertArrayEquals(testBlock, storedBlock, "Stored block data should match the original data");
    }

    @Test
    public void testRegisterBlockLocation_Success() {
        // Given: A hash for a block that should be registered
    	testHash = "testHash";
        String hash = testHash;

        // When: Registering the block's location
        blockService.registerBlockLocation(hash);

        // Then: Verify that the block's location is correctly registered in the metadata node
        String url = config.getMetaNodeUrl() + "/metadata/block/block-nodes/" + hash;
        logger.info("url to get block node mapping: {}", url);
        
        ResponseEntity<List<DfsNode>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<DfsNode>>() {}
        );

        // Extract the list of registered nodes from the response
        List<DfsNode> nodes = response.getBody();

        // Assertions to ensure the response is valid
        assertNotNull(nodes, "Nodes should not be null");
        assertFalse(nodes.isEmpty(), "Nodes list should not be empty");

        // Verify that the current node URL is among the registered nodes
        String currentNodeUrl = config.getContainerUrl();
        List<String> nodeUrls = new ArrayList<>();
        for (DfsNode node : nodes) {
        	nodeUrls.add(node.getContainerUrl());
        }
        boolean isNodeRegistered = nodeUrls.contains(currentNodeUrl);

        assertTrue(isNodeRegistered, "The current node should be registered for the block");
    }

    @Test
    public void testUnregisterBlock_Success() {
        // Unregister the previously stored block
    	testHash = "testHash";
        blockService.unregisterBlock(testHash);

        // Validate that the block is unregistered
        ResponseEntity<String> response = restTemplate.exchange(
            config.getMetaNodeUrl() + "/metadata/block/unregister-block/" + testHash,
            HttpMethod.DELETE,
            null,
            String.class
        );

        //assertEquals("Block unregistered successfully", response.getBody(), "Expected unregistration message");
    }

    @Test
    public void testReadBlock_Success() throws IOException, NoSuchElementException, NoSuchAlgorithmException {
        testBlock = "testBlockData".getBytes();
        testHash = HashUtil.calculateHash(testBlock); // Compute hash for the test block
        blockStorage.saveBlock(testHash, testBlock, false); // Save block for testing
        blockService.registerBlockLocation(testHash); // Register the block location
        
       // Given: A hash for a block that has been stored
        String hash = testHash;

        // When: Attempting to read the block using the readBlock method
        byte[] retrievedBlock = blockService.readBlock(hash);

        // Then: Validate that the retrieved block matches the original block data
        assertNotNull(retrievedBlock, "Retrieved block should not be null");
        assertArrayEquals(testBlock, retrievedBlock, "Retrieved block data should match the original data");
    }

}
