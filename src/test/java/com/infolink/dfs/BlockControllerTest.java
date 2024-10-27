package com.infolink.dfs;

import com.infolink.dfs.BlockController.RequestStoreBlock;
import com.infolink.dfs.shared.HashUtil;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BlockControllerTest {

    @LocalServerPort
    private int port;
    
    @Autowired
    private Config config;
    
    @Autowired
    private RestTemplate restTemplate;
    
    private byte[] block;
    private String hash;
    
    @BeforeEach
    public void setUp() throws Exception {
        // Generate a sample block data and its hash for testing
        restTemplate.exchange(
                config.getMetaNodeUrl() + "/metadata/block/clear-all-block-nodes-mapping",
                HttpMethod.DELETE, // Assuming POST is the correct method for this endpoint
                null,
                String.class
            );
        block = "Sample block data".getBytes(StandardCharsets.UTF_8);
        hash = HashUtil.calculateHash(block);
    }

    @Test
    public void testStoreBlock() {
        // Prepare the request data
        //String hash = "testHash123";
        byte[] blockData = "This is a test block".getBytes();

        // Create the request object
        RequestStoreBlock request = new RequestStoreBlock(hash, blockData);

        // Set the headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Create the HTTP entity
        HttpEntity<RequestStoreBlock> entity = new HttpEntity<>(request, headers);

        // Make the POST request
        String baseUrl = "http://localhost:" + port + "/dfs/block/store";
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl, HttpMethod.POST, entity, String.class);

        // Assert the response
        assertEquals(201, response.getStatusCodeValue());
        assertEquals("Block stored successfully with hash: " + hash, response.getBody());
    }
    
    @Test
    public void testReadBlock() {
        // Store a block before attempting to read it
        byte[] blockData = "This is a test block".getBytes();

        // Create the request object
        RequestStoreBlock request = new RequestStoreBlock(hash, blockData);

        // Set the headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Create the HTTP entity
        HttpEntity<RequestStoreBlock> entity = new HttpEntity<>(request, headers);

        // Make the POST request
        String baseUrl = "http://localhost:" + port + "/dfs/block/store";
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl, HttpMethod.POST, entity, String.class);

        // Construct the URL for reading the stored block
        String readUrl = "http://localhost:" + port + "/dfs/block/read/" + hash;

        // Make the GET request to retrieve the block
        ResponseEntity<byte[]> response2 = restTemplate.exchange(
            readUrl, HttpMethod.GET, null, byte[].class
        );

        // Assert the response
        assertEquals(200, response2.getStatusCodeValue());
        assertArrayEquals(blockData, response2.getBody(), "The returned block data should match the original block.");
    }

    @Test
    public void testReadBlockNotFound() {
        // Construct a URL with a hash that is not stored
        String invalidHash = "nonexistentHash123";
        String readUrl = "http://localhost:" + port + "/dfs/block/read/" + invalidHash;

        // Make the GET request to retrieve a block with a non-existing hash
        ResponseEntity<byte[]> response = restTemplate.exchange(
            readUrl, HttpMethod.GET, null, byte[].class
        );

        // Assert the response status for a block not found
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "Should return NO_CONTENT for a non-existent block.");
    }
}
