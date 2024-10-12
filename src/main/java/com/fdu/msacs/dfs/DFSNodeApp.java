package com.fdu.msacs.dfs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import com.fdu.msacs.dfs.server.Config;
import com.fdu.msacs.dfs.server.RequestNode;

import jakarta.annotation.PostConstruct;

@SpringBootApplication
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
		logger.info("Config instalizing... at node {}:{}", config.getNodeUrl(), config.getPort());
		
	    String nodeUrl = config.getNodeUrl();
	    String metaUrl = config.getMetaNodeUrl();
	    
	    logger.info("Registering node {} to MetaNode at: {}", nodeUrl, metaUrl);

	    try {
		    createDfsFileRoot();

		    RequestNode request = new RequestNode();
	        request.setNodeUrl(nodeUrl);
	        
	        ResponseEntity<String> response = restTemplate.postForEntity(
	        		metaUrl + "/metadata/register-node", 
	                request, 
	                String.class);

	        if (response.getStatusCode().is2xxSuccessful()) {
	            logger.info("Node successfully registered. Server response: {}", response.getBody());
	            return;
	        } else {
	            logger.error("Failed to register node. Server returned status: {}", response.getStatusCode());
	            return;
	        }
	    } catch (Exception e) {
	        logger.error("An error occurred during node registration", e);
	        return;
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
