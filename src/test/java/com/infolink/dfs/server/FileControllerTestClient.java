package com.infolink.dfs.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FileControllerTestClient {

    
    @LocalServerPort
    private int port;

    @Autowired
    private RestTemplate restTemplate;
    
    private String getBaseUrl() {
        return "http://localhost:" + port;
    }


    
    @Test
    public void testReplicateFile() throws IOException {
        // Define the path for the test file
        String testFilePath = "D:\\develop\\testfile\\testfile.txt";

        // Create the directory if it doesn't exist
        File directory = new File("D:\\develop\\testfile");
        if (!directory.exists()) {
            directory.mkdirs(); // Create the directory
        }

        // Create a test file and write some content to it
        File testFile = new File(testFilePath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(testFile))) {
            writer.write("This is a test file content for replication.");
        }

        // Prepare the replicate URL
        String replicateUrl = getBaseUrl() + "/dfs/replicate";

        // Create a FileSystemResource for the test file
        FileSystemResource resource = new FileSystemResource(testFile);

        // Prepare the body for the multipart request
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        // Set headers for the request
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // Create the HttpEntity for the request
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Perform the replication
        ResponseEntity<String> response = restTemplate.postForEntity(replicateUrl, requestEntity, String.class);

        // Assert the response status and body
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("File replicated successfully", response.getBody());

        // Clean up by deleting the test file
        testFile.delete();
    }

    @Test
    public void testDownloadFile() {
        String downloadUrl = getBaseUrl() + "/dfs/getfile/testfile.txt";

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_OCTET_STREAM));

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<byte[]> response = restTemplate.exchange(downloadUrl, HttpMethod.GET, entity, byte[].class);

        // Assert that the response contains the file data
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    public void testGetFileList() {
        String fileListUrl = getBaseUrl() + "/dfs/file-list";

        ResponseEntity<List> response = restTemplate.getForEntity(fileListUrl, List.class);
        
        // Assert that the response contains a non-empty list
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty());
    }
}
