package com.infolink.dfs;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.infolink.dfs.BlockController.RequestStoreBlock;
import com.infolink.dfs.shared.DfsNode;
import com.infolink.dfs.metanode.ResponseNodesForBlock;
import com.infolink.dfs.shared.DfsFile;
import com.infolink.dfs.shared.HashUtil;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import com.infolink.dfs.shared.BlockNode;
import jakarta.annotation.PostConstruct;

@Service
public class DedupeFileService {
    private static final Logger logger = LoggerFactory.getLogger(DedupeFileService.class);
    //private static final int BLOCK_SIZE = 8 * 1024; // 8 KB

    @Autowired
    private Config config;
    @Autowired
    private RestTemplate restTemplate;
    @Value("${dfs.block.size:8196}")
    private int BLOCK_SIZE;
    private String metaNodeUrl;
    @Autowired
    private BlockService blockService;
    
    //private String fileControllerUrl; // URL of the FileController
    
    @PostConstruct
    public void postConstruction() {
        this.metaNodeUrl = config.getMetaNodeUrl();
        //this.fileControllerUrl = this.metaNodeUrl;
        logger.info("DedupeFileService::metaNodeUrl={}", this.metaNodeUrl);
    }
    
    public String dedupeSaveFile(MultipartFile file, String user, String targetDir) throws IOException, NoSuchAlgorithmException {
        logger.debug("dedupeSaveFile(...) called with targetDir: {}", targetDir);
        logger.debug("BLOCK_SIZE={}", BLOCK_SIZE);
        
        String filename = file.getOriginalFilename();
        logger.debug("Original filename: {}", filename);
        
        // Sanitize the filename
        if (filename != null) {
            filename = filename.replace("'", ""); // Remove single quotes
            filename = filename.trim(); // Trim leading/trailing spaces
        }
        long fileSize = file.getSize();
        logger.debug("File size: {} bytes", fileSize);
        
        String parentHash = HashUtil.calculateHash(targetDir.getBytes());
        logger.debug("Calculated parent hash for targetDir '{}': {}", targetDir, parentHash);
        
        List<String> blockHashes = new ArrayList<>();
        try (InputStream inputStream = file.getInputStream()) {
            byte[] buffer = new byte[BLOCK_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // Calculate the hash of the block using HashUtil
            	byte[] realBuffer = new byte[bytesRead];
            	System.arraycopy(buffer, 0, realBuffer, 0, bytesRead);
                String blockHash = HashUtil.calculateHash(realBuffer, bytesRead);
                //logger.debug("Block: Hash={} byte[]={} ",blockHash, realBuffer);
                
                // Request the metadata node to get nodes for storing the block
                ResponseNodesForBlock response = getNodesForBlock(blockHash);
                logger.debug("getNodesForBlock(...) returns: {}", response.getStatus());
                int i = 0;
                for(DfsNode node : response.getNodes()) {
                	logger.debug("{}      {}", i, node.getContainerUrl());
                	i++;
                }
                
                List<DfsNode> nodes = response.getNodes();
                if (nodes.isEmpty()) {
                    logger.info("Get nodes for block retrieved 0 nodes. Response is {}", response);
                    continue;
                }
                
                // Store the block in one of the nodes
                boolean stored = storeBlockOnNodes(nodes, blockHash, realBuffer, bytesRead);
                
                
                logger.debug("Block saved onto nodes{}", nodes);
                
                if (stored) {
                    blockHashes.add(blockHash);
                } else {
                    logger.error("Failed to store block with hash: {}", blockHash);
                }
            }
        }
        // Calculate the hash of the entire file using block hashes
        String fileHash = calculateFileHash(blockHashes);
        logger.info("Calculated file hash: {}", fileHash);
        
        // Create a DfsFile instance with the block hashes and target directory
        DfsFile dfsFile = new DfsFile();
        dfsFile.setHash(fileHash);
        dfsFile.setOwner(user);
        dfsFile.setName(filename);
        dfsFile.setPath(targetDir + "/" + filename);
        dfsFile.setSize(fileSize);
        dfsFile.setDirectory(false);
        dfsFile.setParentHash(parentHash);
        dfsFile.setBlockHashes(blockHashes);
        dfsFile.setCreateTime(new Date());
        dfsFile.setLastModifiedTime(new Date());
        saveDfsFileToMetaNode(dfsFile, targetDir);
        return fileHash;
    }

    private String saveDfsFileToMetaNode(DfsFile dfsFile, String targetDir) {
        if (metaNodeUrl == null || metaNodeUrl.trim().isEmpty()) {
            logger.error("FileController URL is not configured. Cannot save DfsFile metadata.");
            return "FileController URL is not configured.";
        }

        try {
            String postUrl = metaNodeUrl + "/metadata/file/save"; // Assuming this is the correct endpoint
            logger.info("postUrl={}", postUrl);
            
            // Create a request object, assuming the targetDir needs to be part of the request body.
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("dfsFile", dfsFile);
            requestBody.put("targetDirectory", targetDir);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody);
            
            ResponseEntity<String> response = restTemplate.exchange(
                postUrl,
                HttpMethod.POST,
                request,
                String.class
            );
            
            logger.info("DfsFile metadata saved successfully to MetaNode. Response: {}", response.getBody());
            return response.getBody(); // Return the ID or any message from FileController
        } catch (HttpClientErrorException e) {
            logger.error("Error saving DfsFile metadata to MetaNode: {}", e.getMessage());
            return "Error saving file: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Unexpected error when saving DfsFile metadata: {}", e.getMessage());
            return "Error saving file: " + e.getMessage();
        }
    }

    public ResponseEntity<Resource> downloadFile(String fileHash) {
    	logger.debug("DedupeFileService::downloadFile({})", fileHash);
    	
        try {
            // Fetch the list of BlockNode objects containing block data
        	logger.debug("POST {}/metadata/file/block-nodes to get the block list with nodes have them for the file", metaNodeUrl);
        	
            ResponseEntity<List<BlockNode>> response = restTemplate.exchange(
            		metaNodeUrl + "/metadata/file/block-nodes",  // Updated endpoint URL
                    HttpMethod.POST,
                    new HttpEntity<>(fileHash),  // Wrap the requestBody in HttpEntity
                    new ParameterizedTypeReference<List<BlockNode>>() {}
            );            
            List<BlockNode> blockNodeList = response.getBody();
            
            logger.debug("BlockNode list returned is: {}", blockNodeList);
            
            if (blockNodeList == null || blockNodeList.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
            }

            List<byte[]> blockDataList = new ArrayList<>();

            // Iterate over each BlockNode and download the corresponding block
            logger.debug("Iterate over each BlockNode and download the corresponding block");
            int i = 0;
            for (BlockNode blockNode : blockNodeList) {
            	
                Set<String> nodeUrls = blockNode.getNodeUrls();
                logger.debug(" Read the block({}) ({}), which stored on nodes({})", i, blockNode.getHash(), blockNode.getNodeUrls());
                
                // Ensure there is at least one node URL to fetch from
                if (nodeUrls == null || nodeUrls.isEmpty()) {
                    continue; // Skip this block node if no URLs are available
                }

                String nodeUrl = nodeUrls.iterator().next(); // Get the first available URL
                if (nodeUrl.equals(config.getContainerUrl())) {
                	logger.debug(" Read block {} from local{}.", blockNode.getHash(), nodeUrl);
                	byte[] thisBlock = blockService.readBlock(blockNode.getHash());
                	blockDataList.add(thisBlock);
                    logger.debug("Block data read: {}", new String(thisBlock));
                	
                } else {
                	logger.debug(" Read block from remote{}.", nodeUrl);
                	byte[] thisBlock = readBlockFromRemoteNode(blockNode.getHash(), nodeUrl);
                	blockDataList.add(thisBlock);
                }
                i++;
            }

            // Combine all blocks into a single byte array to reconstruct the original file
            int totalSize = blockDataList.stream().mapToInt(b -> b.length).sum();
            byte[] combinedData = new byte[totalSize];
            int currentIndex = 0;
            
            logger.debug("File totalsize read: {}", totalSize);
            
            for (byte[] blockData : blockDataList) {
                System.arraycopy(blockData, 0, combinedData, currentIndex, blockData.length);
                currentIndex += blockData.length;
            }

            // Create a ByteArrayResource from the combined data
            Resource resource = new ByteArrayResource(combinedData);
            
            logger.debug("Return Resource for the blocks read.");
            
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + fileHash + "\"")
                    .contentLength(combinedData.length)
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null); // Return server error if something goes wrong during the process
        }
    }
    
    
    byte[] readBlockFromRemoteNode(String blockHash, String nodeUrl) {
        String readUrl = "";
        
        if (config.isRunningInDocker()) {
            readUrl = nodeUrl + "/dfs/block/read/" + blockHash;
        	logger.debug("This node is running in docker, use {} to access the block.", readUrl);
        	
        } else {
            String endpointUrl = metaNodeUrl + "/metadata/get-localurl-for-node";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> requestEntity = new HttpEntity<>(nodeUrl, headers);

            //get the localUrl for the node.
            ResponseEntity<String> response2 = restTemplate.exchange(endpointUrl, HttpMethod.POST, requestEntity, String.class );
            if (response2.getStatusCode().is2xxSuccessful() && response2.getBody() != null) {
            	String localUrl = response2.getBody();
            	readUrl = localUrl + "/dfs/block/read/" + blockHash;
            	logger.debug("This node is running locally, use {} to access the block.", readUrl);
            	
            } else {
            	logger.error("Can not get node from metanode with containerUrl({}).", nodeUrl);
            	return null;
            }
        }
        
        try {
            // Attempt to download the block data
            logger.debug("Read the block from: {}", readUrl);
            ResponseEntity<byte[]> blockResponse = restTemplate.exchange(readUrl, HttpMethod.GET, null, byte[].class);
            
            if (blockResponse.getStatusCode() == HttpStatus.OK && blockResponse.getBody() != null) {
                logger.debug("Block data read: {}", new String(blockResponse.getBody()));
                return blockResponse.getBody();
                
            } else {
                logger.debug("Failed to read block from {}", readUrl);
                return null;
            }
        } catch (RestClientException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    ResponseNodesForBlock getNodesForBlock(String blockHash) {
        logger.debug("Starting getNodesForBlock with blockHash: {}", blockHash);
        
        try {
            // Create headers and set the Content-Type to application/json
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            logger.debug("Headers set with Content-Type: {}", MediaType.APPLICATION_JSON);

            // Create a JSON object using a Map
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("hash", blockHash);
            requestBody.put("nodeUrl", config.getContainerUrl());
            logger.debug("Request body created: {}", requestBody);

            // Create the request entity with the JSON body and headers
            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
            logger.debug("HTTP request entity created with body: {} and headers: {}", requestBody, headers);

            // Make the POST request using RestTemplate and include the request entity
            logger.debug("Making POST request to URL: {}/metadata/block/nodes-for-block", metaNodeUrl);
            ResponseEntity<ResponseNodesForBlock> response = restTemplate.exchange(
                metaNodeUrl + "/metadata/block/nodes-for-block",
                HttpMethod.POST,
                request,
                ResponseNodesForBlock.class
            );

            // Log the response status and body if the request is successful
            logger.debug("Received response with status: {} and body: {}", response.getStatusCode(), response.getBody());

            return response.getBody();
        } catch (HttpClientErrorException e) {
            // Log detailed information about the HTTP error
            logger.error("HTTP error fetching nodes for block hash {}: Status: {}, Error: {}",
                blockHash, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            // Log unexpected errors
            logger.error("Unexpected error fetching nodes for block hash {}: {}", blockHash, e.getMessage());
        }

        // Return an empty ResponseNodesForBlock if an error occurs
        logger.warn("Returning empty ResponseNodesForBlock due to an error for blockHash: {}", blockHash);
        return new ResponseNodesForBlock();
    }


    boolean storeBlockOnNodes(List<DfsNode> nodes, String hash, byte[] block, int bytesRead) {
    	List<String> containerUrls = nodes.stream()
    		    .map(DfsNode::getContainerUrl) // Get containerUrl for each DfsNode
    		    .collect(Collectors.toList()); // Collect results into a List<String>
    	
        logger.debug("DedupFileService::storeBlockOnNodes({}, {})", containerUrls, hash);
        logger.debug("block size={}", bytesRead);
        //logger.debug("byte[]={}", new String(block));

        // Validate inputs early and return false if invalid
        if (nodes == null || nodes.isEmpty() || hash == null || bytesRead <= 0) {
            logger.warn("Invalid input parameters for storing block: nodes={}, hash={}, bytesRead={}", nodes, hash, bytesRead);
            return false;
        }

        boolean success = false; // Variable to track if any node successfully stored the block

	     // Loop through each node and attempt to store the block
	     for (DfsNode node : nodes) {
	         logger.debug("Attempting to store block on node: {}", node.getContainerUrl());
	         
	         try {
	             // Create a truncated version of the block to store
	             byte[] truncatedBlock = Arrays.copyOf(block, bytesRead);
	             RequestStoreBlock requestStoreBlock = new RequestStoreBlock(hash, truncatedBlock);
	             HttpEntity<RequestStoreBlock> request = new HttpEntity<>(requestStoreBlock);
	             
	             // if the node is current node, save locally.
	             String nodeUrl = node.getContainerUrl();
	             if (nodeUrl.equals(config.getContainerUrl())) {
	                 // Save locally
	                 logger.debug("Store block locally.");
	                 blockService.storeBlockLocally(hash, truncatedBlock, false);
	                 success = true; // Mark success since we stored locally
	                 
	             } else {
	                 logger.debug("Store block on another node {}", nodeUrl);
	                 String baseUrl = config.isRunningInDocker() ? node.getContainerUrl() : node.getLocalUrl();
	                 String url = baseUrl + "/dfs/block/store";
	                 logger.debug("Request {} to store the block data.", url);
	                 
	                 ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
	                 
	                 // Check the response status
	                 if (response.getStatusCode() == HttpStatus.CREATED) {
	                     logger.info("Block stored successfully on node: {}. Response: {}", node.getContainerUrl(), response.getBody());
	                     success = true; // Mark success for any successful remote storage
	                 } else {
	                     logger.warn("Failed to store block on node: {}. Status: {}, Response: {}",
	                         node.getContainerUrl(), response.getStatusCode(), response.getBody());
	                 }
	             }
	         } catch (HttpClientErrorException e) {
	             logger.error("HTTP error while storing block on node {}: Status: {}, Error: {}",
	                 node.getContainerUrl(), e.getStatusCode(), e.getResponseBodyAsString());
	         } catch (Exception e) {
	             logger.error("Error occurred while storing block on node {}: {}", node.getContainerUrl(), e.getMessage());
	         }
	     }
	
	     // After attempting to store on all nodes, log the outcome
	     if (success) {
	         logger.info("Block stored successfully on at least one node for hash: {}", hash);
	     } else {
	         logger.warn("Failed to store block on any node for hash: {}", hash);
	     }
	
	     return success; // Return true if at least one store operation was successful
    }
   
    String calculateFileHash(List<String> blockHashes) throws NoSuchAlgorithmException {
        // Concatenate all block hashes into a single string
        StringBuilder concatenatedHashes = new StringBuilder();
        for (String blockHash : blockHashes) {
            concatenatedHashes.append(blockHash);
        }

        // Calculate the hash of the concatenated block hashes to represent the file hash
        return HashUtil.calculateHash(concatenatedHashes.toString().getBytes());
    }

    public DfsFile getDfsFileByHash(String hash) {
        String url = metaNodeUrl + "/metadata/file/" + hash;
        try {
            ResponseEntity<DfsFile> response = restTemplate.getForEntity(url, DfsFile.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            logger.error("Error fetching DfsFile by hash {}: {}", hash, e.getMessage());
            return null;
        }
    }

    public DfsFile getDfsFileByPath(String username, String fullPath) {
        String url = metaNodeUrl + "/metadata/file/get-by-filepath";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Create a map for the request body to include both username and filePath
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("username", username);
            requestBody.put("filePath", fullPath);

            // Convert the map to a JSON-compatible request entity
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

            // Make the POST request
            ResponseEntity<DfsFile> response = restTemplate.postForEntity(url, requestEntity, DfsFile.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            logger.error("Error fetching DfsFile by path {}: {}", fullPath, e.getMessage());
            return null;
        }
    }
}
