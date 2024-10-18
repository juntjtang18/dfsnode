package com.fdu.msacs.dfs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fdu.msacs.dfs.bfs.BlockStorage;
import com.fdu.msacs.dfs.BlockController.RequestStoreBlock;
import com.fdu.msacs.dfs.HashUtil; // Import the HashUtil class

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Service
public class BlockService {
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private BlockStorage blockStorage;

    @Autowired
    private Config config;

    public String checkAndStoreBlock(byte[] block) throws NoSuchAlgorithmException, IOException {
        // Calculate the hash of the block using the HashUtil
        String hash = HashUtil.calculateHash(block);

        // Step 1: Get the nodes responsible for the block from the meta node
        List<String> nodes = getNodesForBlock(hash);
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalStateException("No nodes found for the block with hash: " + hash);
        }

        // Step 2: Process each node
        for (String nodeUrl : nodes) {
            if (isCurrentNode(nodeUrl)) {
                // Current node: Save the block locally and register its location
                blockStorage.saveBlock(hash, block, false);
                registerBlockLocation(hash);
            } else {
                // Not the current node: Store the block remotely
                storeBlockOnRemoteNode(nodeUrl, hash, block);
            }
        }

        return hash; // Return the hash of the stored block
    }

    private List<String> getNodesForBlock(String hash) {
        String url = String.format("%s/metadata/nodes-for-block", config.getMetaNodeUrl());

        // Create a request object
        RequestNodesForBlock request = new RequestNodesForBlock();
        request.setHash(hash);
        request.setNodeUrl(config.getContainerUrl()); // Set the nodeUrl as well

        try {
            ResponseEntity<List<String>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(request), // Set the request body
                new ParameterizedTypeReference<List<String>>() {}
            );

            return response.getBody();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<String>(); // Return null in case of an error
        }
    }


    private boolean isCurrentNode(String nodeUrl) {
        return config.getContainerUrl().equals(nodeUrl);
    }

    private void storeBlockOnRemoteNode(String nodeUrl, String hash, byte[] block) {
        String url = String.format("%s/dfs/block/store", nodeUrl);
        RequestStoreBlock request = new RequestStoreBlock(hash, block);

        try {
            HttpEntity<RequestStoreBlock> requestEntity = new HttpEntity<>(request);
            restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        } catch (Exception e) {
            e.printStackTrace(); // Handle exception as needed
        }
    }

    private void registerBlockLocation(String hash) {
        // Prepare the request object
        RequestBlockNode request = new RequestBlockNode();
        request.setHash(hash);
        request.setNodeUrl(config.getContainerUrl());

        // Construct the URL for block registration
        String url = String.format("%s/metadata/register-block-location", config.getMetaNodeUrl());

        try {
            HttpEntity<RequestBlockNode> requestEntity = new HttpEntity<>(request);
            restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        } catch (Exception e) {
            e.printStackTrace(); // Handle exception as needed
        }
    }

    public void unregisterBlock(String hash) {
        String url = String.format("%s/metadata/unregister-block/%s", config.getMetaNodeUrl(), hash);

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, null, String.class);
        } catch (Exception e) {
            e.printStackTrace(); // Handle exception as needed
        }
    }

    // Inner class for request
    public static class RequestBlockNode {
        private String hash;
        private String nodeUrl;

        // Getters and Setters
        public String getHash() 			{            return hash;        }
        public void setHash(String hash) 	{            this.hash = hash;        }
        public String getNodeUrl() 			{            return nodeUrl;        }
        public void setNodeUrl(String nodeUrl) {            this.nodeUrl = nodeUrl;        }
    }

    public static class RequestUnregisterBlock {
        public String hash;
        public String nodeUrl;

        // Getters and Setters
        public String getHash() {            return hash;        }
        public void setHash(String hash) {            this.hash = hash;        }
        public String getNodeUrl() {            return nodeUrl;        }
        public void setNodeUrl(String nodeUrl) {            this.nodeUrl = nodeUrl;        }
    }
 // Inner class to represent the request body
    public static class RequestNodesForBlock {
        private String hash;
        private String nodeUrl;

        // Getters and Setters
        public String getHash() {            return hash;        }
        public void setHash(String hash) {            this.hash = hash;        }
        public String getNodeUrl() {            return nodeUrl;        }
        public void setNodeUrl(String nodeUrl) {            this.nodeUrl = nodeUrl;        }
    }
}
