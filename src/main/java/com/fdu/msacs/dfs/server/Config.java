package com.fdu.msacs.dfs.server;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.io.File;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            String os = System.getProperty("os.name").toLowerCase();
            String jarPath = new File(Config.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
            File jarDir;
            
            logger.debug("getAppDirectory() --- os={}  jarPath={} ", os, jarPath);
            
            if (os.contains("win")) {
                jarDir = new File(jarPath).getParentFile();
            } else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
                jarDir = new File(jarPath).getParentFile().getParentFile().getParentFile();
            } else {
                throw new IllegalStateException("Unsupported OS");
            }
            //logger.debug("Config::getAppDirectory() --- jarDir={}", jarDir);
            
            // Check if the system property 'appDir' is set
            String appDir = System.getProperty("appDir");
            
            //logger.debug(" System.getProperty(appDir)={}", appDir);
            
            if (appDir != null) {
                return appDir;
            }

            // Default to jar directory if 'appDir' is not set
            return jarDir.getAbsolutePath();
            
        } catch (URISyntaxException e) {
            logger.error("Failed to get app directory", e);
            throw new RuntimeException("Failed to get app directory", e);
        }
    }


}
