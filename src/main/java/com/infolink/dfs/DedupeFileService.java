package com.infolink.dfs;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.infolink.dfs.BlockController.RequestStoreBlock;
import com.infolink.dfs.shared.DfsNode;
import com.infolink.dfs.metanode.ResponseNodesForBlock;
import com.infolink.dfs.shared.DfsFile;
import com.infolink.dfs.shared.HashUtil;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import jakarta.annotation.PostConstruct;

@Service
public class DedupeFileService {
    private static final Logger logger = LoggerFactory.getLogger(DedupeFileService.class);
    private static final int BLOCK_SIZE = 8 * 1024; // 8 KB

    @Autowired
    private Config config;
    @Autowired
    private RestTemplate restTemplate;
    
    private String metaNodeUrl;
    //private String fileControllerUrl; // URL of the FileController
    
    @PostConstruct
    public void postConstruction() {
        this.metaNodeUrl = config.getMetaNodeUrl();
        //this.fileControllerUrl = this.metaNodeUrl;
        logger.info("DedupeFileService::metaNodeUrl={}", this.metaNodeUrl);
    }
    
    public String dedupeSaveFile(MultipartFile file, String user, String targetDir) throws IOException, NoSuchAlgorithmException {
        logger.info("dedupeSaveFile(...) called with targetDir: {}", targetDir);
        
        String filename = file.getOriginalFilename();
        logger.info("Original filename: {}", filename);
        
        // Sanitize the filename
        if (filename != null) {
            filename = filename.replace("'", ""); // Remove single quotes
            filename = filename.trim(); // Trim leading/trailing spaces
        }
        long fileSize = file.getSize();
        logger.info("File size: {} bytes", fileSize);
        
        String parentHash = HashUtil.calculateHash(targetDir.getBytes());
        logger.info("Calculated parent hash for targetDir '{}': {}", targetDir, parentHash);
        
        List<String> blockHashes = new ArrayList<>();
        try (InputStream inputStream = file.getInputStream()) {
            byte[] buffer = new byte[BLOCK_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // Calculate the hash of the block using HashUtil
                String blockHash = HashUtil.calculateHash(buffer, bytesRead);
                
                // Request the metadata node to get nodes for storing the block
                ResponseNodesForBlock response = getNodesForBlock(blockHash);
                List<DfsNode> nodes = response.getNodes();
                if (nodes.isEmpty()) {
                    logger.info("Get nodes for block retrieved 0 nodes. Response is {}", response);
                    continue;
                }
                
                // Store the block in one of the nodes
                boolean stored = storeBlockOnNodes(nodes, blockHash, buffer, bytesRead);
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

    // Method to handle streaming the file content
    StreamingResponseBody downloadFileContent(DfsFile dfsFile) {
        return outputStream -> {
            try {
                for (String blockHash : dfsFile.getBlockHashes()) {
                    // Fetch block nodes based on the block hash
                    List<String> nodeUrls = fetchBlockNodes(blockHash);
                    if (nodeUrls.isEmpty()) {
                        throw new IOException("No nodes available for block: " + blockHash);
                    }

                    // Select the first node and get the block data
                    String nodeUrl = nodeUrls.get(0); // Simplification
                    try (InputStream blockDataStream = getBlockData(nodeUrl, blockHash)) {
                        // Stream the block data to the response output stream
                        byte[] buffer = new byte[8192]; // Buffer size
                        int bytesRead;
                        while ((bytesRead = blockDataStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to stream file content", e);
            }
        };
    }

    public List<String> fetchBlockNodes(String blockHash) {
        String url = String.format("%s/metadata/block/block-nodes/%s", metaNodeUrl, blockHash); // Adjust base URL as necessary
        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        return response.getBody();
    }
    
    InputStream getBlockData(String nodeUrl, String blockHash) {
    	return null;
    }
    
    ResponseNodesForBlock getNodesForBlock(String blockHash) {
        try {
            HttpEntity<String> request = new HttpEntity<>(blockHash);
            ResponseEntity<ResponseNodesForBlock> response = restTemplate.exchange(
                metaNodeUrl + "/metadata/nodes-for-block",
                HttpMethod.POST,
                request,
                ResponseNodesForBlock.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            logger.error("Error fetching nodes for block hash {}: {}", blockHash, e.getMessage());
            return new ResponseNodesForBlock();
        }
    }

    boolean storeBlockOnNodes(List<DfsNode> nodes, String hash, byte[] block, int bytesRead) {
        for (DfsNode node : nodes) {
            try {
                byte[] truncatedBlock = new byte[bytesRead];
                System.arraycopy(block, 0, truncatedBlock, 0, bytesRead);
                
                RequestStoreBlock requestStoreBlock = new RequestStoreBlock(hash, truncatedBlock);
                HttpEntity<RequestStoreBlock> request = new HttpEntity<>(requestStoreBlock);
                
                String response = restTemplate.postForObject(
                    node.getContainerUrl() + "/dfs/block/store",
                    request,
                    String.class
                );
                
                logger.info("Block stored successfully on node: {}. Response: {}", node.getContainerUrl(), response);
                return true;
            } catch (HttpClientErrorException e) {
                logger.error("Failed to store block on node {}: {}", node.getContainerUrl(), e.getMessage());
            } catch (Exception e) {
                logger.error("An unexpected error occurred while storing block on node {}: {}", node.getContainerUrl(), e.getMessage());
            }
        }
        return false;
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
        String url = metaNodeUrl + "/metadata/file/find-by-path?path=" + fullPath;
        try {
            ResponseEntity<DfsFile> response = restTemplate.getForEntity(url, DfsFile.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            logger.error("Error fetching DfsFile by path {}: {}", fullPath, e.getMessage());
            return null;
        }
    }
}
