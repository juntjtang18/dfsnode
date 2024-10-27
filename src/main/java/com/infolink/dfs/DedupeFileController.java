package com.infolink.dfs;

import com.infolink.dfs.shared.DfsFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class DedupeFileController {

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

        try {
            // Fetch the DfsFile metadata from the metanode.
            DfsFile dfsFile = dedupeFileService.getDfsFileByPath(username, filepath);
            if (dfsFile == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Set the response headers for file download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData(dfsFile.getName(), dfsFile.getName()); // Set disposition with file name
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            // Write the file content to the response output stream
            StreamingResponseBody responseBody = dedupeFileService.downloadFileContent(dfsFile);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(responseBody);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    

    @GetMapping("/dfs/file/downloadByHash")
    public ResponseEntity<byte[]> downloadFileByHash(@RequestParam("hash") String hash) {
        try {
            // Fetch the DfsFile metadata from the metanode using its hash.
            DfsFile dfsFile = null;
            if (dfsFile == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
            byte[] fileContent = fetchFileContent(dfsFile);
            return buildFileResponse(fileContent, dfsFile.getName());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }
    
    private byte[] fetchFileContent(DfsFile dfsFile) throws IOException {
        List<byte[]> blocks = new ArrayList<>();

        // Iterate through block hashes and fetch each block's data from available nodes.
        for (String blockHash : dfsFile.getBlockHashes()) {
            List<String> nodeUrls = null;

            // Loop through available nodes and attempt to retrieve the block.
            byte[] blockData = null;
            for (String nodeUrl : nodeUrls) {
                blockData = null;
                if (blockData != null) {
                    break; // Stop if block is successfully retrieved.
                }
            }

            if (blockData == null) {
                throw new IOException("Unable to retrieve block with hash: " + blockHash);
            }

            blocks.add(blockData);
        }

        // Concatenate all blocks into a single byte array to reconstruct the file.
        return concatenateBlocks(blocks);
    }

    private byte[] concatenateBlocks(List<byte[]> blocks) {
        int totalSize = blocks.stream().mapToInt(block -> block.length).sum();
        byte[] fileContent = new byte[totalSize];
        int currentPosition = 0;

        for (byte[] block : blocks) {
            System.arraycopy(block, 0, fileContent, currentPosition, block.length);
            currentPosition += block.length;
        }

        return fileContent;
    }

    private ResponseEntity<byte[]> buildFileResponse(byte[] fileContent, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .body(fileContent);
    }
}
