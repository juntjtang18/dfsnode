package com.fdu.msacs.dfs;

import com.fdu.msacs.dfs.bfs.BlockStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BlockControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BlockStorage blockStorage;

    @Autowired
    private Config config;
    private String hash;
    private byte[] block;

    @BeforeEach
    public void setUp() throws Exception {
        // Generate a sample block data and its hash for testing
        restTemplate.exchange(
                config.getMetaNodeUrl() + "/metadata/clear-all-block-nodes-mapping",
                HttpMethod.DELETE, // Assuming POST is the correct method for this endpoint
                null,
                String.class
            );
        block = "Sample block data".getBytes(StandardCharsets.UTF_8);
        hash = HashUtil.calculateHash(block);
    }

    //@Test
    public void testStoreBlock() throws NoSuchElementException, NoSuchAlgorithmException, IOException {
        // Prepare the request body
        BlockController.RequestStoreBlock request = new BlockController.RequestStoreBlock(hash, block);

        // Create an HTTP entity with the request
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<BlockController.RequestStoreBlock> requestEntity = new HttpEntity<>(request, headers);

        // Make the HTTP POST request
        String url = "http://localhost:" + port + "/dfs/block/store";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);

        // Verify that the response status is 201 CREATED
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().contains("Block stored successfully with hash: " + hash));

        // Verify that the block was saved in the BlockStorage
        byte[] retrievedBlock = blockStorage.readBlock(hash);
        assertEquals(new String(block, StandardCharsets.UTF_8), new String(retrievedBlock, StandardCharsets.UTF_8));
    }

}
