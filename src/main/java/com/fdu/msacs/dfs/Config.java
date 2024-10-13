package com.fdu.msacs.dfs;

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
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;

@Configuration
public class Config {
    private static Logger logger = LoggerFactory.getLogger(Config.class);
    private String rootDir;
	private String keyStoreFilePath;
	private String keyStorePassword;
	private String keyPassword;
	private String keyAlias;
    
    //@Value("${meta.node.url}")
    private String metaNodeUrl;
	private String containerUrl;
    private String localUrl;
    @Value("${server.port}")
    private String containerPort;			// the port defined in application.properties or passed in setting. the node will listen to this port
    private String hostPort;			// The port that map to localhost/docker
    private String containerName;
    
    public Config() {
    }
    
    @PostConstruct
    public void postConstruct() {
    	this.setContainerName("");
    	this.setHostPort("");
    	
        if (isRunningInDocker()) {
        	String containerName = System.getenv("CONTAINER_NAME");
        	String hostPort = System.getenv("HOST_PORT");
        	this.containerUrl = "http://" + containerName + ":" + containerPort;  // Using application name as the node name
        	this.metaNodeUrl = "http://dfs-meta-node:8080"; // Use container name for requests
        	this.setLocalUrl("http://localhost:" + hostPort);
        	this.hostPort = hostPort;
        } else {
        	this.containerUrl = "http://localhost:" + containerPort;
        	this.metaNodeUrl  = "http://localhost:8080";
        	this.setLocalUrl("http://localhost:" + containerPort); // Using value from application.properties
        	this.hostPort     = containerPort;
        }
        this.rootDir = getAppDirectory() + "/file-storage";
        this.keyStoreFilePath = Paths.get(getAppDirectory(),"config","keystore.ks").toString();
        this.keyStorePassword = "password";
        this.keyPassword      = "password";
        this.keyAlias         = "alias";
    }

	@Bean
	public RestTemplate restTemplate() {
	    RestTemplate restTemplate = new RestTemplate();
	    restTemplate.getMessageConverters().add(new ByteArrayHttpMessageConverter());
	    return restTemplate;
	}
	
	public String getContainerPort() {
    	return containerPort;
    }
    
    public String getContainerUrl() {
    	return containerUrl;
    }
    
    public String getMetaNodeUrl() {
    	return metaNodeUrl;
    }
    
    public String getRootDir() {
    	return this.rootDir;
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

	public String getKeyStoreFilePath() {
		return this.keyStoreFilePath;
	}

	public String getKeyAlias() {
		return this.keyAlias;
	}

	public String getKeyStorePassword() {
		return this.keyStorePassword;
	}

	public String getKeyPassword() {
		return this.keyPassword;
	}

	public String getContainerName() {
		return containerName;
	}

	public void setContainerName(String containerName) {
		this.containerName = containerName;
	}

	public String getHostPort() {
		return hostPort;
	}

	public void setHostPort(String hostPort) {
		this.hostPort = hostPort;
	}

	public String getLocalUrl() {
		return localUrl;
	}

	public void setLocalUrl(String localUrl) {
		this.localUrl = localUrl;
	}


}
