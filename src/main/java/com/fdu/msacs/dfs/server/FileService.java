package com.fdu.msacs.dfs.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
public class FileService {
    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    private Path rootDir;

    private final Map<String, byte[]> fileStorage = new HashMap<>();
    
    public FileService() throws IOException {
    	this.rootDir = Paths.get(Config.getAppDirectory(), "file-storage");
        if (!Files.exists(rootDir)) {
            Files.createDirectory(rootDir);
        }
    }

    public void saveFile(MultipartFile file) throws IOException {
    	logger.info("saveFile(...) called...");
    	
        String filename = file.getOriginalFilename();
        Path filePath = rootDir.resolve(filename);
        file.transferTo(filePath.toFile());

        // Storing file in memory for simplicity
        fileStorage.put(filename, Files.readAllBytes(filePath));
    }

    public byte[] getFile(String filename) {
        return fileStorage.get(filename);
    }
}
