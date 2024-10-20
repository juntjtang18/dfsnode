package com.fdu.msacs.dfs;

import com.fdu.msacs.dfs.BlockService;
import com.fdu.msacs.dfs.bfs.BlockStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

@RestController
public class BlockController {

    @Autowired
    private BlockService blockService;

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

    // Inner class for request payload
    public static class RequestStoreBlock {
        private String hash; // Field to hold the hash of the block
        private byte[] block; // Field to hold the block data
        public RequestStoreBlock(String hash, byte[] block) {
            this.hash = hash;
            this.block = block;
        }

        // Getters and Setters
        public String getHash() {
            return hash;
        }

        public void setHash(String hash) {
            this.hash = hash;
        }

        public byte[] getBlock() {
            return block;
        }

        public void setBlock(byte[] block) {
            this.block = block;
        }
    }
}
