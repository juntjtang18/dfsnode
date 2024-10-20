package com.fdu.msacs.dfs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

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
public class Config {
    private static Logger logger = LoggerFactory.getLogger(Config.class);
    @Autowired
    private Environment environment;
    private String rootDir;
	private String keyStoreFilePath;
	private String keyStorePassword;
	private String keyPassword;
	private String keyAlias;
    private String metaNodeUrl;
	private String containerUrl;
    private String localUrl;
    private String containerName;
    @Value("${server.port:8081}") // Default to 8081 if not set
    private String serverPort;
    private Boolean runningInDocker;
    
    public Config() {
    }
    
    @PostConstruct
    public void postConstruct() {
    	logger.info("postCostruct of Cofig...");
    	
        String runtimeMode = System.getenv("RUNTIME_MODE");
        String containerName = System.getenv("CONTAINER_NAME");
        String hostPort = System.getenv("HOST_PORT");

        // Check if `local.server.port` is available (indicating a test environment with a random port)
        String testPort = environment.getProperty("local.server.port");
    	
        runningInDocker = false;

        if ("PRODUCT".equalsIgnoreCase(runtimeMode)) {
        	runningInDocker = true;
            // Production mode: Running in a container
            metaNodeUrl = "http://dfs-meta-node:8080";
            containerUrl = "http://" + (containerName != null ? containerName : "localhost") + ":" + serverPort;
            localUrl = "http://localhost:" + (hostPort != null ? hostPort : serverPort);
        } else if (testPort != null) {
            // Running in test with a random port assigned, use `local.server.port`
            metaNodeUrl = "http://localhost:8080";
            containerUrl = "http://localhost:" + testPort;
            localUrl = "http://localhost:" + testPort;
        } else {
            // Default mode: Running locally or in other scenarios
            metaNodeUrl = "http://localhost:8080";
            containerUrl = "http://localhost:8081";
            localUrl = "http://localhost:8081";
        }


        logger.info("RUNTIME_MODE: {}", runtimeMode);
        logger.info("MetaNodeUrl: {}", metaNodeUrl);
        logger.info("ContainerUrl: {}", containerUrl);
        logger.info("LocalUrl: {}", localUrl);
    	
        this.rootDir = getAppDir() + "/file-storage";
        this.keyStoreFilePath = Paths.get(getAppDir(),"config","keystore.ks").toString();
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
	
	public String getServerPort() {
    	return serverPort;
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
    
    public static String getAppDir() {
        try {
            // Get the path of the running class or JAR
            String jarPath = Config.class.getProtectionDomain().getCodeSource().getLocation().toString();
            logger.info("jarPath: {}", jarPath);

            File jarFile;

            if (jarPath.startsWith("jar:file:")) {
                jarPath = jarPath.substring(9, jarPath.indexOf('!')); // Extract the JAR path
                jarFile = new File(new URI(jarPath)); // Handle as URI
            } else if (jarPath.startsWith("file:")) {
                jarPath = jarPath.substring(5); // Strip "file:" prefix and handle as file path
                jarFile = new File(jarPath);
            } else {
                jarFile = new File(jarPath);
            }

            return handleAppDir(jarFile);

        } catch (URISyntaxException | IllegalArgumentException e) {
            logger.error("Failed to get app directory", e);
            throw new RuntimeException("Failed to get app directory", e);
        }
    }

    private static String handleAppDir(File jarFile) {
        File jarDir = jarFile.getParentFile();

        if (jarDir != null && jarDir.getName().equals("classes")) {
            jarDir = jarDir.getParentFile(); // Go to target
        }

        if (jarDir == null || !jarDir.exists()) {
            logger.warn("Jar directory is null or does not exist: {}", jarDir);
            jarDir = new File("/app"); 
        }

        File dfsDir = new File(jarDir, "dfs");

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

	public boolean isRunningInDocker() {
        return runningInDocker;
	}
		/*
        if (runningInDocker != null) {
            return runningInDocker; // Return cached result if available
        }

        String cgroup = "";
        try {
            cgroup = new String(Files.readAllBytes(Paths.get("/proc/1/cgroup")));
        } catch (IOException e) {
            logger.info("Could not read cgroup file.");
        }
        
        runningInDocker = cgroup.contains("docker") || cgroup.contains("kubepods"); // Cache the result
        return runningInDocker;
	}
*/
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

	public String getLocalUrl() {
		return localUrl;
	}
}
