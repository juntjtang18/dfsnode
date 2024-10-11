package com.fdu.msacs.dfs.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fdu.msacs.dfs.server.ByteArrayHttpMessageConverter;

import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;

@Configuration
@Service
public class Config {
    private static Logger logger = LoggerFactory.getLogger(Config.class);
    private static Config instance;
    @Value("${dfs.node.url}")
    private String nodeUrl;
    @Value("${server.port}")
    private String nodePort;
    @Value("${meta.node.url}")
    private String metaNodeUrl;
    // Provide a global point of access to the instance
    
    @Bean
    public static synchronized Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }
    
    public Config() {
    	/*
        if (isRunningInDocker()) {
        	nodePort = System.getenv("HOST_PORT");
        	String hname = System.getenv("CONTAINER_NAME");
        	nodeUrl = "http://" + hname + ":" + nodePort;  // Using application name as the node name
        	metaNodeUrl = "http://dfs-meta-node:8080"; // Use container name for requests
        } else {
        	nodeUrl = nodeUrl + ":" + nodePort; // Using value from application.properties
            //metaNodeUrl = "http://localhost:8080"; // Use localhost for requests
        }
        */
    }
    @PostConstruct
    public void postConstruct() {
    	
        if (isRunningInDocker()) {
        	nodePort = System.getenv("HOST_PORT");
        	String hname = System.getenv("CONTAINER_NAME");
        	nodeUrl = "http://" + hname + ":" + nodePort;  // Using application name as the node name
        	metaNodeUrl = "http://dfs-meta-node:8080"; // Use container name for requests
        } else {
        	nodeUrl = nodeUrl + ":" + nodePort; // Using value from application.properties
            //metaNodeUrl = "http://localhost:8080"; // Use localhost for requests
        }

    }

	@Bean
	public RestTemplate restTemplate() {
	    RestTemplate restTemplate = new RestTemplate();
	    restTemplate.getMessageConverters().add(new ByteArrayHttpMessageConverter());
	    return restTemplate;
	}
	
	public String getPort() {
    	return nodePort;
    }
    
    public String getNodeUrl() {
    	return nodeUrl;
    }
    
    public String getMetaNodeUrl() {
    	return metaNodeUrl;
    }
    
    public static String getAppDirectory() {
        try {
            // Get the path of the running class or JAR
            String jarPath = Config.class.getProtectionDomain().getCodeSource().getLocation().toString();
            logger.info("jarPath: {}", jarPath);

            File jarFile;

            // If the path is a URI (starts with "jar:file:" or "file:")
            if (jarPath.startsWith("jar:file:")) {
                jarPath = jarPath.substring(9, jarPath.indexOf('!')); // Extract the JAR path
                jarFile = new File(new URI(jarPath)); // Handle as URI
            } else if (jarPath.startsWith("file:")) {
                jarPath = jarPath.substring(5); // Strip "file:" prefix and handle as file path
                jarFile = new File(jarPath);
            } else {
                // Handle as a regular file path (no URI involved)
                jarFile = new File(jarPath);
            }

            // Call helper method to handle the app directory logic
            return handleAppDirectory(jarFile);

        } catch (URISyntaxException | IllegalArgumentException e) {
            logger.error("Failed to get app directory", e);
            throw new RuntimeException("Failed to get app directory", e);
        }
    }

    private static String handleAppDirectory(File jarFile) {
        // Get the parent directory of the JAR file
        File jarDir = jarFile.getParentFile();

        // Check if we're running from class files in Eclipse (./target/classes)
        if (jarDir != null && jarDir.getName().equals("classes")) {
            // Navigate up two levels to reach ./target
            jarDir = jarDir.getParentFile(); // Go to target
        }

        // Default to a fallback directory if jarDir is null or doesn't exist (e.g., in a container)
        if (jarDir == null || !jarDir.exists()) {
            logger.warn("Jar directory is null or does not exist: {}", jarDir);
            jarDir = new File("/app"); // Fallback to /app in a container environment
        }

        // Create the "dfs" directory within the jarDir
        File dfsDir = new File(jarDir, "dfs");

        // Create the directory if it doesn't exist
        if (!dfsDir.exists()) {
            boolean created = dfsDir.mkdirs();
            if (!created) {
                logger.error("Failed to create directory: {}", dfsDir.getAbsolutePath());
            } else {
                logger.info("Directory created successfully: {}", dfsDir.getAbsolutePath());
            }
        } else {
            logger.info("Directory already exists: {}", dfsDir.getAbsolutePath());
        }

        return dfsDir.getAbsolutePath();
    }

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
