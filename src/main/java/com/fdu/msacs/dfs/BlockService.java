package com.fdu.msacs.dfs;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fdu.msacs.dfs.bfs.BlockStorage;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

@Service
public class BlockService {
    private final RestTemplate restTemplate;
    private final BlockStorage blockStorage;
    private final String metaNodeUrl;

    public BlockService(RestTemplate restTemplate, BlockStorage blockStorage, String metaNodeUrl) {
        this.restTemplate = restTemplate;
        this.blockStorage = blockStorage;
        this.metaNodeUrl = metaNodeUrl;
    }

    public boolean checkAndStoreBlock(String hash, byte[] block) throws NoSuchAlgorithmException, IOException {
        // Step 1: Check if the block already exists on the meta node
        if (!doesBlockNeedToSave(hash)) {
            // Step 2: Save the block locally using BlockStorage methods
            blockStorage.saveBlock(hash, block, false);

            // Step 3: Register the block location on the meta node
            registerBlockLocation(hash);
            
            return true; // Block was stored and registered
        }

        return false; // Block already exists; no further action taken
    }

    private boolean doesBlockNeedToSave(String hash) {
        // Construct the URL to check block existence
        String url = String.format("%s/metadata/block-exists/%s", metaNodeUrl, hash);

        try {
            ResponseEntity<Set<String>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Set<String>>() {}
            );

            Set<String> nodeUrls = response.getBody();
            return nodeUrls != null && !nodeUrls.isEmpty();
        } catch (Exception e) {
            // Handle exception (log error, rethrow, or handle gracefully)
            e.printStackTrace();
            return false; // Assume block does not exist in case of an error
        }
    }

    private void registerBlockLocation(String hash) {
        // Prepare the request object
        RequestBlockNode request = new RequestBlockNode();
        request.setHash(hash);
        request.setNodeUrl(getNodeUrl());

        // Construct the URL for block registration
        String url = String.format("%s/metadata/register-block-location", metaNodeUrl);

        try {
            HttpEntity<RequestBlockNode> requestEntity = new HttpEntity<>(request);
            restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        } catch (Exception e) {
            // Handle exception (log error, rethrow, or handle gracefully)
            e.printStackTrace();
        }
    }
    
    public void unregisterBlock(String hash) {
        // Construct the URL for unregistering the block
        String url = String.format("%s/metadata/unregister-block/%s", metaNodeUrl, hash);

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, null, String.class);
        } catch (Exception e) {
            // Handle exception (log error, rethrow, or handle gracefully)
            e.printStackTrace();
        }
    }
    
    private String getNodeUrl() {
        // This method should return the URL of the current node, which might be
        // configured or dynamically determined.
        return "http://localhost:8080"; // Example URL, adjust as needed
    }
    
    // inner class for request
    public static class RequestBlockNode {
        private String hash;
        private String nodeUrl;

        // Getters and Setters
        public String getHash() {
            return hash;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }

        public String getNodeUrl() {
            return nodeUrl;
        }

        public void setNodeUrl(String nodeUrl) {
            this.nodeUrl = nodeUrl;
        }
    }
    
    public static class RequestUnregisterBlock {
        public String hash;
        public String nodeUrl;

        // Getters and Setters
        public String getHash() {
            return hash;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }

        public String getNodeUrl() {
            return nodeUrl;
        }

        public void setNodeUrl(String nodeUrl) {
            this.nodeUrl = nodeUrl;
        }
    }
}
