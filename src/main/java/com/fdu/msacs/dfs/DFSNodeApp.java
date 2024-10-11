package com.fdu.msacs.dfs;

import java.io.IOException;
import java.nio.file.Files;
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
    
    @Value("${metadata.service.url}")
    private String metaServerUrl;
    @Value("${dfs.node.address}")
    private String nodeUrl;
	public static void main(String[] args) {
		SpringApplication.run(DFSNodeApp.class, args);
	}

	@PostConstruct
	public void registerNodeWithMetadataService() {
	    Config config = new Config();
	    String nodeAddress = nodeUrl + ":" + config.getPort();  // Adjust to the actual node address and port

	    // Check if the application is running inside a Docker container
	    String metaServerUrlToUse;
        if (isRunningInDocker()) {
        	String hostname = System.getenv("CONTAINER_NAME");
            nodeAddress = "http://" + hostname + ":" + config.getPort();  // Using application name as the node name
	        metaServerUrlToUse = "http://dfs-meta-node:8080"; // Use container name for requests
        } else {
            nodeAddress = nodeUrl + ":" + config.getPort(); // Using value from application.properties
	        metaServerUrlToUse = "http://localhost:8080"; // Use localhost for requests
        }
	    logger.info("Registering node {} to MetaNode at: {}", nodeAddress, metaServerUrl);

	    try {
	        RequestNode request = new RequestNode();
	        request.setNodeUrl(nodeAddress);
	        
	        ResponseEntity<String> response = restTemplate.postForEntity(
	                metaServerUrlToUse + "/metadata/register-node", 
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
	private boolean isRunningInDocker() {
	    String cgroup = "";
	    try {
	        cgroup = new String(Files.readAllBytes(Paths.get("/proc/1/cgroup")));
	    } catch (IOException e) {
	        logger.info("Could not read cgroup file. ");
	    }
	    return cgroup.contains("docker") || cgroup.contains("kubepods");
	}


}
