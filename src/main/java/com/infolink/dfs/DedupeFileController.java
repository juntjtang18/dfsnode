package com.infolink.dfs;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.infolink.dfs.shared.DfsFile;


@RestController
public class DedupeFileController {
    private static final Logger logger = LoggerFactory.getLogger(DedupeFileController.class);

    @Autowired
    private DedupeFileService dedupeFileService;

    @PostMapping("/dfs/file/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file,
                                             @RequestParam("user") String user,
                                             @RequestParam("targetDir") String targetDir) {
        try {
            String result = dedupeFileService.dedupeSaveFile(file, user, targetDir);
            return ResponseEntity.ok(result);
        } catch (IOException | NoSuchAlgorithmException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error uploading file: " + e.getMessage());
        }
    }

    @PostMapping("/dfs/file/download")
    public ResponseEntity<StreamingResponseBody> downloadFile(@RequestBody Map<String, String> request) {
        String filepath = request.get("filepath");
        String username = request.get("username");

        logger.info("Received download request for filepath: {} by user: {}", filepath, username);

        try {
            // Fetch the DfsFile metadata from the metanode.
            DfsFile dfsFile = dedupeFileService.getDfsFileByPath(username, filepath);
            if (dfsFile == null) {
                logger.warn("No content found for filepath: {} by user: {}", filepath, username);
                return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
            }

            logger.info("File metadata retrieved successfully for filepath: {} by user: {}", filepath, username);

            // Set the response headers for file download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData(dfsFile.getName(), dfsFile.getName());
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            logger.info("Initiating file download for file hash: {}", dfsFile.getHash());

            // Write the file content to the response output stream
            ResponseEntity<StreamingResponseBody> response = dedupeFileService.downloadFile(dfsFile.getHash());

            logger.info("File download response created successfully for file hash: {}", dfsFile.getHash());

            return response;
        } catch (Exception e) {
            logger.error("Error occurred during file download for filepath: {} by user: {}", filepath, username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @CrossOrigin(origins = "*")
    @GetMapping("/dfs/file/downloadByHash")
    public ResponseEntity<StreamingResponseBody> downloadFileByHash(@RequestParam("hash") String hash) {
        logger.debug("downloadFileByHash() called with hash: {}", hash);

        if (hash == null || hash.isEmpty()) {
            logger.warn("Hash parameter is null or empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        // Fetch the file content as a stream based on the provided hash
        ResponseEntity<StreamingResponseBody> response = dedupeFileService.downloadFile(hash);

        // Handle unsuccessful response or missing content
        if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            logger.info("No content found for hash: {}", hash);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
        }

        // Return the streaming response to the client
        return response;
    }
  
}
