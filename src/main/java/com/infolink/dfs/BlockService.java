package com.infolink.dfs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.infolink.dfs.BlockController.RequestStoreBlock;
import com.infolink.dfs.bfs.BlockStorage;
import com.infolink.dfs.shared.DfsNode;
import com.infolink.dfs.metanode.ResponseNodesForBlock;
import com.infolink.dfs.shared.HashUtil;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

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
    
    public String checkAndStoreBlock(byte[] block) throws NoSuchAlgorithmException, IOException, Exception {
        String hash = HashUtil.calculateHash(block);
        logger.debug("Calculated hash for the block: {}", hash);

        // Step 1: Get the nodes responsible for the block from the meta node
        ResponseNodesForBlock response = getNodesForBlock(hash);
        List<DfsNode> nodes = response.getNodes();
        
        if (nodes == null || nodes.isEmpty()) {
            logger.error("No nodes found to store the block: {}", hash);
            //List<String> blockNodesMapping = getBlockNodesMapping(hash);
            //logger.info("Block-node mapping for hash {}: {}", hash, blockNodesMapping);
            //logger.debug("No nodes assigned by meta node for the block with hash: " + hash);
            return null;
        }

        logger.debug("Found {} nodes responsible for the block with hash: {}", nodes.size(), hash);

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

    public byte[] readBlock(String hash) throws IOException, NoSuchElementException, NoSuchAlgorithmException {
        byte[] blockData = blockStorage.readBlock(hash); // Use BlockStorage to read the block
        logger.debug("Successfully read block with hash: {}", hash);
        return blockData;
    }
    
    private List<String> getBlockNodesMapping(String hash) {
        try {
            ResponseEntity<List<DfsNode>> response = restTemplate.exchange(
                config.getMetaNodeUrl() + "/metadata/block/block-nodes/{hash}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<DfsNode>>() {},
                hash
            );

            List<DfsNode> nodes = response.getBody();

            if (nodes != null) {
            	List<String> nodeUrls = new ArrayList<>();
            	for (DfsNode node : nodes) {
            		nodeUrls.add(node.getContainerUrl());
            	}
                return nodeUrls;
            } else {
                logger.warn("Received null response for block-node mapping for hash: {}", hash);
                return List.of();
            }
        } catch (Exception e) {
            logger.error("Error occurred while retrieving block-node mapping for hash: {}", hash, e);
            throw new RuntimeException("Failed to retrieve block-node mapping for hash: " + hash, e);
        }
    }

    private ResponseNodesForBlock getNodesForBlock(String hash) {
        String url = String.format("%s/metadata/block/nodes-for-block", config.getMetaNodeUrl());

        // Create a request object
        RequestNodesForBlock request = new RequestNodesForBlock();
        request.setHash(hash);
        request.setNodeUrl(config.getContainerUrl()); // Set the nodeUrl as well

        try {
            ResponseEntity<ResponseNodesForBlock> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(request), // Set the request body
                new ParameterizedTypeReference<ResponseNodesForBlock>() {}
            );

            return response.getBody();
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseNodesForBlock(); // Return null in case of an error
        }
    }


    private boolean isCurrentNode(DfsNode node) {
        return config.getContainerUrl().equals(node.getContainerUrl());
    }

    public void storeBlockOnRemoteNode(DfsNode node, String hash, byte[] block) throws Exception {
    	logger.debug("BlockService::storeBlockOnRemoteNode(...) called with DfsNode{}, hash{}, block{}", node, hash, block.length);
    	
    	if (node==null || hash==null || block==null) throw new Exception("The passed in parameter is null.");
    	
    	String nodeUrl = config.isRunningInDocker() ? node.getContainerUrl():node.getLocalUrl();
    	
    	logger.debug("storeBlockOnRemoteNode: {}", nodeUrl);
    	
        String url = String.format("%s/dfs/block/store", nodeUrl);
        RequestStoreBlock request = new RequestStoreBlock(hash, block);

        HttpEntity<RequestStoreBlock> requestEntity = new HttpEntity<>(request);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
        	throw new Exception("Fail to store block onto " + nodeUrl + ". Response is " + response.getBody());
        } else {
        	logger.debug("Successfully store the block onto {}.", nodeUrl);
        }
    }

    public boolean registerBlockLocation(String hash) {
    	if (hash==null) return false;
    	
    	logger.debug("register block onto {}", config.getContainerUrl());
    	
        // Prepare the request object
        RequestBlockNode request = new RequestBlockNode();
        request.setHash(hash);
        String containerUrl = config.getContainerUrl();
        request.setNodeUrl(containerUrl);

        // Construct the URL for block registration
        String url = String.format("%s/metadata/block/register-block-location", config.getMetaNodeUrl());

        try {
            HttpEntity<RequestBlockNode> requestEntity = new HttpEntity<>(request);
            restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            
            return true;
        } catch (Exception e) {
            e.printStackTrace(); // Handle exception as needed
        }
        return false;
    }

    public void unregisterBlock(String hash) {
        String url = String.format("%s/metadata/block/unregister-block/%s", config.getMetaNodeUrl(), hash);

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, null, String.class);
        } catch (Exception e) {
            e.printStackTrace(); // Handle exception as needed
        }
    }
    
    public long getBlockCount() {
    	return blockStorage.getBlockCount();
    }
    
    public long getBlockTotalSize() {
    	return blockStorage.getBlockTotalSize();
    }
    public void clearBlockFiles() {
    	blockStorage.clearFiles();
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
