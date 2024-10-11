package com.fdu.msacs.dfs.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FileControllerTest {
    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Autowired
    private RestTemplate restTemplate;

    private static final String BASE_URL = "http://localhost:8081/dfs"; // Adjust port if needed
    private static final String BASE_DIR = "test-files"; // Directory for test files
    private static final String TEST_FILENAME = "testFile.txt";
    private File testFile;

    @Autowired
    private ApplicationContext context;

    @Test
    public void logRegisteredEndpoints() {
        RequestMappingHandlerMapping requestMappingHandlerMapping = context.getBean(RequestMappingHandlerMapping.class);
        requestMappingHandlerMapping.getHandlerMethods().forEach((key, value) -> logger.info(key.toString() + " : " + value.toString()));
    }    
    
    @BeforeEach
    public void setUp() throws IOException {
        // Create a test directory if it doesn't exist
        Path basePath = Paths.get(BASE_DIR);
        Files.createDirectories(basePath);

        // Create a test file for uploading
        testFile = new File(BASE_DIR, TEST_FILENAME);
        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            fos.write("This is a test file.".getBytes());
        }
    }

    @Test
    public void testUploadFile() throws Exception {
        // Prepare the file as a MultiValueMap
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(testFile)); // Use FileSystemResource to represent the file

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // Create the request entity
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Upload the file
        String response = restTemplate.postForObject(BASE_URL + "/upload", requestEntity, String.class);

        assertEquals("File uploaded successfully", response);
    }

    @Test
    public void testDownloadFile() throws Exception {
        // Upload the test file first
        testUploadFile();

        // Download the file
        ResponseEntity<Resource> responseEntity = restTemplate.exchange(
                BASE_URL + "/getfile/" + TEST_FILENAME, HttpMethod.GET, null, Resource.class);

        assertTrue(responseEntity.getBody().exists());
        assertEquals(TEST_FILENAME, responseEntity.getBody().getFilename());
    }

    @Test
    public void testReplicateFile() throws Exception {
        // Prepare the file as a MultiValueMap
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(testFile)); // Use FileSystemResource to represent the file

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // Create the request entity
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Upload the file
        String response = restTemplate.postForObject(BASE_URL + "/rep", requestEntity, String.class);
        //assertEquals("File uploaded successfully", response);

        assertEquals("File replicated successfully", response);
    }

    @Test
    public void testGetFileList() throws Exception {
        // Upload the test file first
        testUploadFile();

        String response = restTemplate.getForObject(BASE_URL + "/file-list", String.class);

        assertTrue(response.contains(TEST_FILENAME));
    }

    @AfterEach
    public void tearDown() throws IOException {
        // Clean up the test file and directory
        if (testFile.exists()) {
            Files.delete(testFile.toPath());
        }
        Path basePath = Paths.get(BASE_DIR);
        if (Files.exists(basePath)) {
            Files.delete(basePath);
        }
    }
}
