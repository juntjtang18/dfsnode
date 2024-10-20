package com.fdu.msacs.dfs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fdu.msacs.dfs.bfs.BlockStorage;
import com.fdu.msacs.dfs.metanode.DfsNode;
import com.fdu.msacs.dfs.BlockController.RequestStoreBlock;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Service
public class BlockService {
    private static Logger logger = LoggerFactory.getLogger(BlockService.class);
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private BlockStorage blockStorage;

    @Autowired
    private Config config;

    public String storeBlockLocally(String hash, byte[] block, boolean encrypt) throws NoSuchAlgorithmException, IOException {
        blockStorage.saveBlock(hash, block, false); // Assuming false indicates no overwrite
        registerBlockLocation(hash);
        logger.debug("Block saved locally and location registered with hash: {}", hash);

        return hash;
    }
    
    public String checkAndStoreBlock(byte[] block) throws NoSuchAlgorithmException, IOException {
        // Calculate the hash of the block using the HashUtil
        String hash = HashUtil.calculateHash(block);
        logger.debug("Calculated hash for the block: {}", hash);

        // Step 1: Get the nodes responsible for the block from the meta node
        List<DfsNode> nodes = getNodesForBlock(hash);
        if (nodes == null || nodes.isEmpty()) {
            logger.error("No nodes found for the block with hash: {}", hash);

            // TODO: get all registered nodes from meta node, and show it in logger.info
            List<DfsNode> allRegisteredNodes = getAllRegisteredNodes();
            logger.info("All registered nodes in the system: {}", allRegisteredNodes);

            // TODO: get the block node mapping for the hash and show it through logger.info
            List<String> blockNodesMapping = getBlockNodesMapping(hash);
            logger.info("Block-node mapping for hash {}: {}", hash, blockNodesMapping);

            logger.debug("No nodes assigned by meta node for the block with hash: " + hash);
        }

        logger.debug("Found {} nodes responsible for the block with hash: {}", nodes.size(), hash);

        // Step 2: Process each node
        for (DfsNode node : nodes) {
            if (isCurrentNode(node)) {
                logger.debug("Current node is responsible for the block. Saving block locally with hash: {}", hash);
                // Current node: Save the block locally and register its location
                storeBlockLocally(hash, block, false);
            } else {
                logger.debug("Storing block on remote node: {} for hash: {}", node.getContainerUrl(), hash);
                // Not the current node: Store the block remotely
                storeBlockOnRemoteNode(node, hash, block);
            }
        }

        logger.debug("Successfully processed and stored block with hash: {}", hash);
        return hash; // Return the hash of the stored block
    }


    private List<DfsNode> getAllRegisteredNodes() {
        try {
            ResponseEntity<List<DfsNode>> response = restTemplate.exchange(
                config.getMetaNodeUrl() + "/metadata/get-registered-nodes",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<DfsNode>>() {}
            );

            List<DfsNode> allRegisteredNodes = response.getBody();

            if (allRegisteredNodes != null) {
                return allRegisteredNodes;
            } else {
                logger.warn("Received null response for registered nodes from meta node.");
                return List.of();
            }
        } catch (Exception e) {
            logger.error("Error occurred while retrieving all registered nodes from meta node.", e);
            throw new RuntimeException("Failed to retrieve registered nodes from meta node", e);
        }
    }

    private List<String> getBlockNodesMapping(String hash) {
        try {
            ResponseEntity<List<String>> response = restTemplate.exchange(
                config.getMetaNodeUrl() + "/metadata/block-nodes/{hash}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<String>>() {},
                hash
            );

            List<String> blockNodesMapping = response.getBody();

            if (blockNodesMapping != null) {
                return blockNodesMapping;
            } else {
                logger.warn("Received null response for block-node mapping for hash: {}", hash);
                return List.of();
            }
        } catch (Exception e) {
            logger.error("Error occurred while retrieving block-node mapping for hash: {}", hash, e);
            throw new RuntimeException("Failed to retrieve block-node mapping for hash: " + hash, e);
        }
    }

    private List<DfsNode> getNodesForBlock(String hash) {
        String url = String.format("%s/metadata/nodes-for-block", config.getMetaNodeUrl());

        // Create a request object
        RequestNodesForBlock request = new RequestNodesForBlock();
        request.setHash(hash);
        request.setNodeUrl(config.getContainerUrl()); // Set the nodeUrl as well

        try {
            ResponseEntity<List<DfsNode>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(request), // Set the request body
                new ParameterizedTypeReference<List<DfsNode>>() {}
            );

            return response.getBody();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<DfsNode>(); // Return null in case of an error
        }
    }


    private boolean isCurrentNode(DfsNode node) {
        return config.getContainerUrl().equals(node.getContainerUrl());
    }

    private void storeBlockOnRemoteNode(DfsNode node, String hash, byte[] block) {
    	String nodeUrl = config.isRunningInDocker() ? node.getContainerUrl():node.getLocalUrl();
    	
    	logger.debug("storeBlockOnRemoteNode: {}", nodeUrl);
    	
        String url = String.format("%s/dfs/block/store", nodeUrl);
        RequestStoreBlock request = new RequestStoreBlock(hash, block);

        try {
            HttpEntity<RequestStoreBlock> requestEntity = new HttpEntity<>(request);
            restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        } catch (Exception e) {
            e.printStackTrace(); // Handle exception as needed
        }
    }

    public void registerBlockLocation(String hash) {
    	logger.debug("register block onto {}", config.getContainerUrl());
    	
        // Prepare the request object
        RequestBlockNode request = new RequestBlockNode();
        request.setHash(hash);
        String containerUrl = config.getContainerUrl();
        request.setNodeUrl(containerUrl);

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
        public String getHash() 				{   return hash;        			}
        public void setHash(String hash) 		{   this.hash = hash;        		}
        public String getNodeUrl() 				{   return nodeUrl;        			}
        public void setNodeUrl(String nodeUrl) 	{   this.nodeUrl = nodeUrl;        	}
    }

    public static class RequestUnregisterBlock 	{
        public String hash;
        public String nodeUrl;

        // Getters and Setters
        public String getHash() 				{	return hash;        			}
        public void setHash(String hash) 		{   this.hash = hash;        		}
        public String getNodeUrl() 				{   return nodeUrl;        			}
        public void setNodeUrl(String nodeUrl) 	{   this.nodeUrl = nodeUrl;        	}
    }
 // Inner class to represent the request body
    public static class RequestNodesForBlock {
        private String hash;
        private String requestingNodeUrl;
        
        public RequestNodesForBlock() {};
        
        public RequestNodesForBlock(String hash, String nodeUrl) {
        	this.hash = hash;
        	this.requestingNodeUrl = nodeUrl;
        }
        // Getters and Setters
        public String getHash() 				{   return hash;        				}
        public void setHash(String hash) 		{   this.hash = hash;        			}
        public String getNodeUrl() 				{   return requestingNodeUrl;        	}
        public void setNodeUrl(String nodeUrl) 	{   this.requestingNodeUrl = nodeUrl;   }
    }
}
