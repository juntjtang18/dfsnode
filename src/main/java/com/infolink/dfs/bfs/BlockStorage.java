package com.infolink.dfs.bfs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.infolink.dfs.Config;
import com.infolink.dfs.tobedelete.FileController;

import jakarta.annotation.PostConstruct;

@Service
public class BlockStorage {
    private static final Logger logger = LoggerFactory.getLogger(FileController.class);
    private Encryptor encryptor;
    private String rootDir;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private Config config;
    
    private static final String BLOCK_STORAGE_PREFIX = "block_storage:"; // Redis key prefix
    private int blockCount;
    private long totalSize;
    private String containerUrl;
    
    public BlockStorage(Encryptor encryptor, String rootDir) {
        this.rootDir 	= rootDir;
        this.encryptor 	= encryptor;
        initializeDirectoryStructure();
    }
    
    @PostConstruct
    public void postConstruct() {
        this.containerUrl = config.getContainerUrl();
        
        blockCount = 0;
        totalSize = 0;
        loadStats();
    }

    private void initializeDirectoryStructure() {
        try {
            // Create the base blocks directory first
            Path blocksPath = Paths.get(rootDir);
            Files.createDirectories(blocksPath);
            logger.debug("Directories created successfully.");
        } catch (IOException e) {
        	logger.debug("Error creating directories: " + e.getMessage());
        }
    }

    public void saveBlock(String hash, byte[] blockData, boolean encrypt) throws IOException, NoSuchAlgorithmException {
        String blockFilePath = getBlockFilePath(hash);
        String indexFilePath = getIndexFilePath(hash);
        
        // Create a new Block object
        Block block = new Block(blockData, encrypt, hash);
        if (encrypt) block.encrypt(encryptor);
        
        File blockFile = new File(blockFilePath);
        File indexFile = new File(indexFilePath);
        
        File parentDir = blockFile.getParentFile(); // Get the parent directory        
        if (!parentDir.exists()) {
            parentDir.mkdirs(); // Create the parent directories if they don't exist
        }

        // Check if index file exists, create if not
        if (!indexFile.exists()) {
            indexFile.createNewFile();
        }

        try (RandomAccessFile indexRaf = new RandomAccessFile(indexFile, "rw");
             RandomAccessFile blockRaf = new RandomAccessFile(blockFile, "rw")) {

            boolean found = false;
            long indexPosition = 0;
            long schemaSize = BlockSchema.getSerializedSize();

            // Search for the hash in the index file
            while (indexRaf.getFilePointer() < indexRaf.length()) {
                BlockSchema existingSchema = BlockSchema.readFrom(indexRaf);
                if (existingSchema.getHash().equals(hash)) {
                    found = true;
                    // Update reference count
                    existingSchema.setReferenceCount(existingSchema.getReferenceCount() + 1);
                    // Move the pointer back to the position of the found schema to overwrite it
                    indexRaf.seek(indexRaf.getFilePointer() - schemaSize);
                    existingSchema.writeTo(indexRaf); // Write updated schema
                    break;
                }
                indexPosition += schemaSize; // Move to next schema
            }

            if (!found) {
                // Update the schema's offset to the end of the block file
                blockRaf.seek(blockRaf.length()); // Move to the end of the block file
                long offset = blockRaf.getFilePointer();
                block.getSchema().setOffset(offset); // Update offset in the schema
                
                // Write the schema to the index file
                block.getSchema().writeTo(indexRaf);
                
                // Write the block data to the block file
                block.writeTo(blockRaf);
                
                // Update the block count and total size
                blockCount++;
                totalSize += blockData.length; // Add the size of the newly saved block

                // Update Redis directly
                redisTemplate.opsForValue().set(BLOCK_STORAGE_PREFIX + containerUrl + ":blockCount", blockCount);
                redisTemplate.opsForValue().set(BLOCK_STORAGE_PREFIX + containerUrl + ":totalSize", totalSize);
                logger.debug("Block statistics updated in Redis: count={}, size={}", blockCount, totalSize);
            }
        }
    }


    public byte[] readBlock(String hash) throws IOException, NoSuchElementException, NoSuchAlgorithmException {
        String indexFilePath = getIndexFilePath(hash);
        String blockFilePath = getBlockFilePath(hash);

        File indexFile = new File(indexFilePath);
        File blockFile = new File(blockFilePath);

        // Ensure the index file exists
        if (!indexFile.exists()) {
            throw new NoSuchElementException("Index file not found for hash: " + hash);
        }

        try (RandomAccessFile indexRaf = new RandomAccessFile(indexFile, "r");
             RandomAccessFile blockRaf = new RandomAccessFile(blockFile, "r")) {

            boolean found = false;

            // Search for the hash in the index file
            while (indexRaf.getFilePointer() < indexRaf.length()) {
                // Read the schema from the index
                BlockSchema schema = BlockSchema.readFrom(indexRaf);
                if (schema.getHash().equals(hash)) {
                    found = true;

                    // Read the block data from the block file using the offset and size from the schema
                    blockRaf.seek(schema.getOffset()); // Seek to the offset
                    Block block = new Block(schema, new byte[schema.getSize()]);
                    block.readFromFile(blockRaf, encryptor);
                    
                    if (schema.isEncrypted()) {
                    	block.decrypt(encryptor);
                    }
                    
                    logger.debug("BlockStorage::readBlock----------------- block read(byte count={})", block.getSize());
                    logger.debug(" --------------------------------------- block actual size={}", block.getData().length);
                    return block.getData(); // Return the read block data
                }
            }

            if (!found) {
                throw new NoSuchElementException("Block not found for hash: " + hash);
            }
        }
		return null;
    }

    public void deleteBlock(String hash) throws IOException, NoSuchAlgorithmException {
        String indexFilePath = getIndexFilePath(hash);
        File indexFile = new File(indexFilePath);

        if (!indexFile.exists()) {
            System.out.println("Index file not found for hash: " + hash);
            return; // Hash does not exist, so return
        }

        try (RandomAccessFile indexRaf = new RandomAccessFile(indexFile, "rw")) {
            boolean found = false;

            // Search for the hash in the index file
            while (indexRaf.getFilePointer() < indexRaf.length()) {
                // Read the current position in the file
                long currentPosition = indexRaf.getFilePointer();
                
                // Read the schema from the index
                BlockSchema schema = BlockSchema.readFrom(indexRaf);

                if (schema.getHash().equals(hash)) {
                    found = true;

                    if (schema.getReferenceCount() > 0) {
                        schema.setReferenceCount(schema.getReferenceCount()-1);
                        indexRaf.seek(currentPosition);
                        
                        schema.writeTo(indexRaf);
                    }

                    if (schema.getReferenceCount() == 0) {
                        logger.debug("Block with hash {} can be purged.", hash);
                        
                        totalSize -= schema.getSize(); // Subtract the size of the deleted block
                        blockCount--; // Decrement the block count

                        // Update Redis directly
                        redisTemplate.opsForValue().set(BLOCK_STORAGE_PREFIX + containerUrl + ":blockCount", blockCount);
                        redisTemplate.opsForValue().set(BLOCK_STORAGE_PREFIX + containerUrl + ":totalSize", totalSize);
                        logger.debug("Block statistics updated in Redis after deletion: count={}, size={}", blockCount, totalSize);
                    }
                    break; // Exit the loop as we found the hash
                }
            }

            if (!found) {
                logger.debug("Block not found for hash: {}" + hash);
            }
        }
    }

    public int getReferenceCount(String hash) throws IOException {
        String indexFilePath = getIndexFilePath(hash);
        File indexFile = new File(indexFilePath);

        // Ensure the index file exists
        if (!indexFile.exists()) {
            throw new NoSuchElementException("Index file not found for hash: " + hash);
        }

        try (RandomAccessFile indexRaf = new RandomAccessFile(indexFile, "r")) {
            // Search for the hash in the index file
            while (indexRaf.getFilePointer() < indexRaf.length()) {
                // Read the schema from the index
                BlockSchema schema = BlockSchema.readFrom(indexRaf);

                // Check if this schema matches the hash
                if (schema.getHash().equals(hash)) {
                    return schema.getReferenceCount();
                }
            }
        }

        // If the hash was not found, throw an exception
        throw new NoSuchElementException("Block not found for hash: " + hash);
    }

    private String getIndexFilePath(String hash) {
        // Use the same directory structure as block files for the index files
        return Paths.get(rootDir, hash.substring(0, 2), hash.substring(2, 4), hash.substring(4, 6), hash.substring(6,8) + ".idx").toString();
    }

    public String getBlockFilePath(String hash) {
        return Paths.get(rootDir, hash.substring(0, 2), hash.substring(2, 4), hash.substring(4, 6), hash.substring(6,8) + ".bfs").toString();
    }
    
    public void clearFiles() {
        try {
            Path rootPath = Paths.get(rootDir);
            if (Files.exists(rootPath)) {
                Files.walk(rootPath)
                    .sorted((path1, path2) -> path2.compareTo(path1)) // Start from deepest file first
                    .map(Path::toFile)
                    .forEach(File::delete);
                logger.debug("All test files cleared successfully.");
                // Reset block count and total size
                blockCount = 0;
                totalSize = 0;

                // Update statistics in Redis
                redisTemplate.opsForValue().set(BLOCK_STORAGE_PREFIX + config.getContainerUrl() + ":blockCount", blockCount);
                redisTemplate.opsForValue().set(BLOCK_STORAGE_PREFIX + config.getContainerUrl() + ":totalSize", totalSize);
                logger.debug("Block count and total size reset to 0 in Redis.");
                
            } else {
                logger.debug("Root directory does not exist: nothing to clear.");
            }
        } catch (IOException e) {
            logger.error("Failed to clear test files: {}", e.getMessage());
        }
    }
    
    // Load stats from Redis
    private void loadStats() {
        Integer count = (Integer) redisTemplate.opsForValue().get(BLOCK_STORAGE_PREFIX + containerUrl + ":blockCount");
        Long 	size  = (Long) redisTemplate.opsForValue().get(BLOCK_STORAGE_PREFIX + containerUrl + ":totalSize");

        blockCount = (count != null) ? count : 0;
        totalSize = (size != null) ? size : 0;

        logger.debug("Block statistics loaded from Redis: count={}, size={}", blockCount, totalSize);
    }
}


