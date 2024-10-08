package com.fdu.msacs.dfs.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
