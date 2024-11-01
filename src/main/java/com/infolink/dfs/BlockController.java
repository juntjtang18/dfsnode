package com.infolink.dfs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import com.infolink.dfs.shared.DfsNode;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
public class BlockController {

    @Autowired
    private BlockService blockService;
    @Autowired
    private RestTemplate restTemplate;
    
    // Endpoint to store a block
    @PostMapping("/dfs/block/store")
    public ResponseEntity<String> storeBlock(@RequestBody RequestStoreBlock requestStoreBlock) {
        try {
            // Save the block locally using BlockStorage
            String hash = requestStoreBlock.getHash(); // Get the hash from the request
            byte[] block = requestStoreBlock.getBlock(); // Get the block data from the request
            
            blockService.storeBlockLocally(hash, block, false);
            
            return ResponseEntity.status(HttpStatus.CREATED).body("Block stored successfully with hash: " + hash);
        } catch (NoSuchAlgorithmException | IOException e) {
            // Handle exceptions and return appropriate response
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error storing block: " + e.getMessage());
        }
    }

    @GetMapping("/dfs/block/read/{hash}")
    public ResponseEntity<byte[]> readBlock(@PathVariable String hash) {
        try {
            byte[] blockData = blockService.readBlock(hash);
            return ResponseEntity.ok(blockData); // Return the block data with 200 OK status
            
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
            
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    
    @PostMapping("/dfs/block/replicate-to-another-node")
    public ResponseEntity<String> replicateToAnotherNode(@RequestBody Map<String, Object> requestBody) {
        byte[] blockData;
        try {
            // Extract blockHash and targetNodeUrl from the request body
            String blockHash = (String)requestBody.get("blockHash");
            DfsNode targetNode = (DfsNode)requestBody.get("targetNode");

            // Read the block data from the block service
            blockData = blockService.readBlock(blockHash);
            
            blockService.storeBlockOnRemoteNode(targetNode, blockHash, blockData);
            return ResponseEntity.status(HttpStatus.OK).body("Stored the block to target node.");

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body("Block not found: " + e.getMessage());
            
        } catch (NoSuchAlgorithmException | IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing block: " + e.getMessage());
            
        } catch (Exception e) {
        	return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to store block to target node." + e.getMessage());
        }
    }

    
    @DeleteMapping("/dfs/block/clear-files")
    public ResponseEntity<String> clearBlockFiles() {
    	blockService.clearBlockFiles();
    	return ResponseEntity.ok("All block files are cleared.");
    }
    
    // Inner class for request payload
    public static class RequestStoreBlock {
        private String hash; // Field to hold the hash of the block
        private byte[] block; // Field to hold the block data
        public RequestStoreBlock(String hash, byte[] block) {
            this.hash = hash;
            this.block = block;
        }

        // Getters and Setters
        public String getHash() 				{            return hash;        		}
        public void setHash(String hash) 		{            this.hash = hash;        	}
        public byte[] getBlock() 				{            return block;        		}
        public void setBlock(byte[] block) 		{            this.block = block;        }
    }
}
