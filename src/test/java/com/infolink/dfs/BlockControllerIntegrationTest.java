package com.infolink.dfs;

import com.infolink.dfs.BlockController.RequestStoreBlock;
import com.infolink.dfs.bfs.BlockStorage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BlockControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    BlockStorage blockStorage;
    @Test
    public void testStoreBlock_Success() throws IOException, NoSuchAlgorithmException {
        // Given: Prepare a RequestStoreBlock with test data
        String hash = "testHash";
        byte[] block = "testBlockData".getBytes();
        RequestStoreBlock requestStoreBlock = new RequestStoreBlock(hash, block);
        
        // Define the endpoint URL
        String url = "http://localhost:" + port + "/dfs/block/store";
        
        // Prepare HTTP headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Create an HTTP entity with headers and request payload
        HttpEntity<RequestStoreBlock> requestEntity = new HttpEntity<>(requestStoreBlock, headers);
        
        // When: Send a POST request using RestTemplate
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                String.class
        );
        
        // Then: Validate the response
        assertEquals(HttpStatus.CREATED, response.getStatusCode(), "Status should be 201 Created");
        assertTrue(response.getBody().contains("Block stored successfully with hash: " + hash), "Response should contain success message");
        
        // Optionally, verify that the block was stored correctly using additional REST calls or by directly checking storage.
        
        byte[] readblock = blockStorage.readBlock(hash);
        assertNotNull(readblock, "Read block should not be null");
        assertArrayEquals(block, readblock, "Read block data should match the original block data");
        
        
    }

}
