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
        logger.info("Original filename: {}", filename); // Log the original filename

        // Sanitize the filename
        if (filename != null) {
            filename = filename.replace("'", ""); // Remove single quotes
            filename = filename.trim(); // Trim leading/trailing spaces
            // Optionally replace other unwanted characters
            filename = filename.replaceAll("[^a-zA-Z0-9.\\-]", "_"); // Replace with underscores
        }

        Path filePath = rootDir.resolve(filename);
        logger.info("Saving file to path: {}", filePath); // Log the path where the file will be saved
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
