package com.fdu.msacs.dfs.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileService {
    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    private Path rootDir;
    @Autowired
    private RestTemplate restTemplate;
    private String nodeUrl;
    private String metaNodeUrl;
    
    @Autowired
    private Config config;

    public Path getRootDir() {
        return rootDir;
    }
    
    public FileService() throws IOException {
    }

    @PostConstruct
    public void postContruction() {
    	this.rootDir = Paths.get(config.getRootDir()); 
    	this.nodeUrl = config.getNodeUrl();
    	this.metaNodeUrl = config.getMetaNodeUrl();
    }

    public String saveFile(MultipartFile file) throws IOException {
        logger.info("saveFile(...) called...");
        
        String filename = file.getOriginalFilename();
        logger.info("Original filename: {}", filename);
        
        // Sanitize the filename
        if (filename != null) {
            filename = filename.replace("'", ""); // Remove single quotes
            filename = filename.trim(); // Trim leading/trailing spaces
        }

        Path filePath = rootDir.resolve(filename);
        logger.info("Saving file to path: {}", filePath);
        
        // Ensure the parent directory exists
        Files.createDirectories(filePath.getParent());

        try {
            file.transferTo(filePath.toFile());
        } catch (Exception e) {
            logger.error("Error saving file: {}", e.getMessage());
            throw new IOException("Failed to save file", e);
        }

        // Return the full path of the local file as a String
        return filePath.toString();
    }


    public void saveFileAndReplicate(MultipartFile file) throws IOException {
        logger.info("Saving file locally and starting replication...");

        // 1. Save the file locally and get the local file path
        String localFilePath = saveFile(file); // Assuming this method returns the path of the saved file

        // 2. Register the file with the metadata node
        String filename = file.getOriginalFilename();
        String currentNodeUrl = nodeUrl; 

        String restUrl = metaNodeUrl + "/metadata/register-file-location";
        logger.info("Register file location REST URL: {}", restUrl);

        RequestFileLocation requestFileLocation = new RequestFileLocation(filename, currentNodeUrl);
        ResponseEntity<String> response = restTemplate.postForEntity(restUrl, requestFileLocation, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IOException("Failed to register file with metadata server");
        }

        // 3. Get the list of nodes to replicate the file to
        RequestReplicationNodes request = new RequestReplicationNodes();
        request.setFilename(filename);
        request.setRequestingNodeUrl(this.nodeUrl);
        HttpEntity<RequestReplicationNodes> requestEntity = new HttpEntity<>(request);
        ResponseEntity<List<String>> nodeListResponse = restTemplate.exchange(
                metaNodeUrl + "/metadata/get-replication-nodes", 
                HttpMethod.POST, 
                requestEntity, 
                new ParameterizedTypeReference<List<String>>() {}
        );
        List<String> replicationNodes = nodeListResponse.getBody();
        logger.debug("The nodes to replicate to: {}", replicationNodes);

        // 4. Replicate the file to the retrieved nodes
        for (String nodeUrl : replicationNodes) {
            logger.info("Replicating file to node: {}", nodeUrl);

            // Use FileSystemResource to replicate the file
            File fileToReplicate = new File(localFilePath);
            FileSystemResource resource = new FileSystemResource(fileToReplicate);

            // Prepare the request for replication
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);
            
            HttpEntity<MultiValueMap<String, Object>> requestEntityForReplication = new HttpEntity<>(body);

            // Send the FileSystemResource to the replication node and capture the response
            ResponseEntity<String> replicationResponse = restTemplate.postForEntity(nodeUrl + "/dfs/replicate", requestEntityForReplication, String.class);

            // Log the result of the replication
            if (replicationResponse.getStatusCode().is2xxSuccessful()) {
                logger.info("Successfully replicated file to node: {}. Response: {}", nodeUrl, replicationResponse.getBody());
            } else {
                logger.error("Failed to replicate file to node: {}. Status code: {}. Response: {}", nodeUrl, replicationResponse.getStatusCode(), replicationResponse.getBody());
            }
        }
    }

    public Path getFilePath(String filename) throws IOException {
        logger.info("FileService::getFilePath({}) called...", filename);
        
        Path filePath = rootDir.resolve(filename);

        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            logger.warn("File not found or not readable: {}", filename);
            return null; // or throw an exception if you prefer
        }

        return filePath;
    }

    public byte[] getFile(String filename) throws IOException {
        logger.info("FileService::getFile({}) called...", filename);

        Path filePath = rootDir.resolve(filename);

        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            logger.warn("File not found or not readable: {}", filename);
            return null; // or throw an exception if you prefer
        }

        return Files.readAllBytes(filePath);
    }

    public List<String> getFileList() throws IOException {
        logger.info("getFileList() called...");

        List<String> fileNames = new ArrayList<>();
        Files.list(rootDir).forEach(path -> fileNames.add(path.getFileName().toString()));
        return fileNames;
    }
}
