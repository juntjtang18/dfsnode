package com.infolink.dfs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;

import com.infolink.dfs.shared.DfsNode;

import jakarta.annotation.PostConstruct;

@SpringBootApplication
@EnableScheduling
public class DFSNodeApp {
    private static final Logger logger = LoggerFactory.getLogger(DFSNodeApp.class);
    private RestTemplate restTemplate = new RestTemplate();
    private Path rootDir;
    @Autowired
    private Config config;
    
 	public static void main(String[] args) {
		SpringApplication.run(DFSNodeApp.class, args);
	}
 	
	@PostConstruct
	public void registerNodeWithMetadataService() {
		logger.info("Config instalizing... at node {}", config.getContainerUrl());
	    try {
		    createDfsFileRoot();
		    
	        heartbeatToMetaNode();
	        
	    } catch (Exception e) {
	        logger.error("An error occurred during node registration", e);
	        return;
	    }
	}
	
    @Scheduled(fixedRateString = "${dfs.node.heartbeat.rate:60000}") // Default to 10 seconds
    public void heartbeatToMetaNode() {
        String containerUrl = config.getContainerUrl();
        String localUrl = config.getLocalUrl();
        String metaUrl = config.getMetaNodeUrl();

        DfsNode dfsNode = new DfsNode(containerUrl, localUrl);
	    logger.info("Registering node {} to MetaNode at: {}", containerUrl, metaUrl);
        logger.info("Sending heartbeat to MetaNode at: {}", metaUrl);
        
        try {
            // Send POST request to register the node
            ResponseEntity<String> response = restTemplate.postForEntity(
                metaUrl + "/metadata/register-node", 
                dfsNode, 
                String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Heartbeat successful. Server response: {}", response.getBody());
            } else {
                logger.error("Failed to send heartbeat. Server returned status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            logger.error("An error occurred during heartbeat to MetaNode", e);
        }
    }
    
	/**
	 * Check if the application is running inside a Docker container.
	 * @return true if running in a Docker container, false otherwise.
	 */

	public void createDfsFileRoot() {
        this.rootDir = Paths.get(config.getRootDir());
        
        logger.info("FileService rootDir at {}", this.rootDir.toString());
        
        if (Files.exists(rootDir)) {
            if (Files.isRegularFile(rootDir)) {
                logger.error("A file exists with the same name as the desired directory: {}", rootDir);
                return; // or handle as needed
            }
        } else {
            try {
                Files.createDirectories(rootDir);
            } catch (IOException e) {
                logger.error("Failed to create directory: {}", rootDir, e);
            }
        }
	}
}
