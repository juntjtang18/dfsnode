package com.fdu.msacs.dfs.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fdu.msacs.dfsmetanode.RequestFileLocation;
import com.fdu.msacs.dfsmetanode.RequestReplicationNodes;

import jakarta.annotation.PostConstruct;

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
    @Value("${metadata.service.url}")
    private String metaNodeUrl;
    @Autowired
    private RestTemplate restTemplate;
    @Value("${dfs.node.address}")
    private String nodeHost;
    private String nodePort;
    private String nodeUrl;
    
    @Autowired
    private Config config;
    
    public Path getRootDir() {
    	return rootDir;
    }
    
    public FileService() throws IOException {
    }

    @PostConstruct
    public void postContruction() {
        this.rootDir = Paths.get(Config.getAppDirectory(), "/file-storage");
        logger.info("FileService rootDir at {}", this.rootDir.toString());
        if (!Files.exists(rootDir)) {
        	try {
        		Files.createDirectories(rootDir);
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
        }
        this.nodePort = config.getPort();
        this.nodeUrl = this.nodeHost+":"+this.nodePort;
    }
    
    public void saveFile(MultipartFile file) throws IOException {
        logger.info("saveFile(...) called...");

        String filename = file.getOriginalFilename();
        logger.info("Original filename: {}", filename); // Log the original filename

        // Sanitize the filename
        if (filename != null) {
            filename = filename.replace("'", ""); // Remove single quotes
            filename = filename.trim(); // Trim leading/trailing spaces
            //filename = filename.replaceAll("[^a-zA-Z0-9.\\-]", "_"); // Replace with underscores
        }

        Path filePath = rootDir.resolve(filename);
        logger.info("Saving file to path: {}", filePath); // Log the path where the file will be saved
        
        // Ensure the parent directory exists
        Files.createDirectories(filePath.getParent());  // Create the directory if it doesn't exist

        try {
            file.transferTo(filePath.toFile());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void saveFileAndReplicate(MultipartFile file) throws IOException {
        logger.info("Saving file locally and starting replication...");

        // 1. Save the file locally
        saveFile(file);

        // 2. Register the file with the metadata node
        String filename = file.getOriginalFilename();
        String currentNodeUrl = nodeUrl;  // Replace with actual node URL
        
        String restUrl = metaNodeUrl + "/metadata/register-file-location";
        logger.info("Register file location rest URL: {}", restUrl);
        
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

        // 4. Replicate the file to the retrieved nodes
        for (String nodeUrl : replicationNodes) {
            logger.info("Replicating file to node: " + nodeUrl);
            restTemplate.postForObject(nodeUrl + "/dfs/replicate", file, String.class);
        }
    }
    
    public Path getFilePath(String filename) throws IOException {
        logger.info("FileService::getFilePath({}) called...", filename);
        
        // Create the file path relative to the root directory
        Path filePath = rootDir.resolve(filename);

        // Check if the file exists
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            logger.warn("File not found or not readable: {}", filename);
            return null; // or throw an exception if you prefer
        }

        return filePath;
    }

    public byte[] getFile(String filename) throws IOException {
        logger.info("FileService::getFile({}) called...", filename);

        // Create the file path relative to the root directory
        Path filePath = rootDir.resolve(filename);

        // Check if the file exists and is readable
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            logger.warn("File not found or not readable: {}", filename);
            return null; // or throw an exception if you prefer
        }

        // Read the file content into a byte array
        return Files.readAllBytes(filePath);
    }

    // New method to get a list of files in the rootDir
    public List<String> getFileList() throws IOException {
        logger.info("getFileList() called...");

        List<String> fileNames = new ArrayList<>();
        Files.list(rootDir).forEach(path -> fileNames.add(path.getFileName().toString()));
        return fileNames;
    }
}
