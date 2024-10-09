package com.fdu.msacs.dfs.server;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.io.File;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI; 
@Service
@Lazy
public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    private static Config instance;

    // Provide a global point of access to the instance
    public static synchronized Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
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



}
