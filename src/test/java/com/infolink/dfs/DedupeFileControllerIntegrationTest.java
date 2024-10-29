package com.infolink.dfs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DedupeFileControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;

    @BeforeEach
    public void setUp() {
        baseUrl = "http://localhost:" + port + "/dfs/file";
    }

    //@Test
    public void testUploadFile() throws IOException {
        // Create a temporary file with some content.
        File tempFile = File.createTempFile("testfile", ".txt");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write("This is a test file for upload.".getBytes());
        }

        // Prepare the multipart request.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // Create a resource from the file content.
        Resource resource = new ByteArrayResource(Files.readAllBytes(tempFile.toPath())) {
            @Override
            public String getFilename() {
                return tempFile.getName();
            }
        };

        // Create the body of the request with the file and additional form data.
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        body.add("user", "testUser");
        body.add("targetDir", "/test/dir");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        String uploadUrl = baseUrl + "/upload";

        // Send the request to upload the file.
        ResponseEntity<String> response = restTemplate.postForEntity(
                uploadUrl,
                requestEntity,
                String.class
        );

        // Validate the response.
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        System.out.println("Upload Response: " + response.getBody());

        // Clean up the temporary file.
        tempFile.delete();
    }
}
