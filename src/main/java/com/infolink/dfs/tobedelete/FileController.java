package com.infolink.dfs.tobedelete;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


public class FileController {
    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    // Upload a file (client-side upload)
    @PostMapping("/dfs/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        logger.info("/dfs/upload requested.");
        try {
            // Save file locally and replicate
            fileService.saveFileAndReplicate(file);
            return ResponseEntity.ok("File uploaded successfully");
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("File upload failed");
        }
    }
    
    @PostMapping("/dfs/dedupe-upload")
    public ResponseEntity<String> dedupeUploadFile(@RequestParam("file") MultipartFile file) {
        logger.info("/dfs/upload requested.");
        try {
            // Save file locally and replicate
            fileService.saveFileAndReplicate(file);
            return ResponseEntity.ok("File uploaded successfully");
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("File upload failed");
        }
    }
    
    // Replicate file between nodes (node-to-node replication)
    @PostMapping("/dfs/replicate")
    public ResponseEntity<String> repFile(@RequestParam("file") MultipartFile file) {
        logger.info("/dfs/replicate requested.");
        try {
            // Save file locally and replicate
            fileService.saveFile(file);
            return ResponseEntity.ok("File replicated successfully");
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("File upload failed");
        }
    }

    @GetMapping("/dfs/getfile/{filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        logger.info("Request to download file: {}", filename);

        try {
            Path filePath = fileService.getFilePath(filename);
            
            // Check if the filePath is null or if the file does not exist
            if (filePath == null || !Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            // Use Resource to serve the file as a stream
            Resource resource = new UrlResource(filePath.toUri());

            // Return the response with the binary file
            return ResponseEntity.ok()
                    .contentLength(Files.size(filePath))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM) // Treating the file as binary
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filePath.getFileName().toString() + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            logger.error("Invalid URL for file: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        } catch (IOException e) {
            logger.error("Error reading file: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    
    // Get list of files
    @GetMapping("/dfs/file-list")
    public ResponseEntity<List<String>> getFileList() {
        logger.info("/dfs/file-list requested.");
        try {
            List<String> files = fileService.getFileList();
            return ResponseEntity.ok(files);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
