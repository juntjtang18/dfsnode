package com.infolink.dfs;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.infolink.dfs.shared.DfsNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DedupeFileControllerTest {
    private final String TEST_FILE_PATH = "testFile.txt";
    private final String USER = "testUser";
    private final String TARGET_DIR = "testTargetDir";

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private Config config;
    
    private String baseUrl;
    @LocalServerPort
    private int port;
    private  String metaNodeUrl;

    @BeforeEach
    public void setUp() throws IOException {
        // Set the base URL for the controller endpoints
        this.baseUrl = "http://localhost:" + port;

        // Create a temporary test file with specific content
        createTestFile(TEST_FILE_PATH);
    	this.metaNodeUrl = config.getMetaNodeUrl();
        ResponseEntity<String> response = restTemplate.exchange(
        		metaNodeUrl + "/metadata/block/clear-all-block-nodes-mapping",
                HttpMethod.DELETE,
                null,
                String.class);
        
        ResponseEntity<String> response2 = restTemplate.exchange(
        		metaNodeUrl + "/metadata/file/clear-all-data", 
                HttpMethod.DELETE, 
                null, 
                String.class
        );
        String containerUrl = config.getContainerName();
        String localUrl = config.getLocalUrl();
        if (containerUrl==null) containerUrl = localUrl;
        System.out.println("ContainerUrl=" + containerUrl + "  localUrl=" + localUrl);
    }


    private void createTestFile(String filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            for (char c = 'A'; c <= 'F'; c++) {
                writer.write(c);
                writer.write("012345678"); // Repeat pattern for each character
            }
            writer.write('G');
            writer.write("01234");
        }
    }

    @AfterEach
    public void tearDown() {
        // Clean up the test file
        File testFile = new File(TEST_FILE_PATH);
        if (testFile.exists()) {
            testFile.delete();
        }

        // Clean up the target directory after test
        File targetDirectory = new File(TARGET_DIR);
        if (targetDirectory.exists()) {
            for (File file : targetDirectory.listFiles()) {
                file.delete();
            }
            targetDirectory.delete();
        }
    }

    //@Test
    public void testUploadFile() throws IOException, NoSuchAlgorithmException {
        // Create a real MultipartFile from the test file
        File file = new File(TEST_FILE_PATH);
        CustomMultipartFile multipartFile = new CustomMultipartFile(file);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // Prepare request entity with file and parameters
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));
        body.add("user", USER);
        body.add("targetDir", TARGET_DIR);
        
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Call the upload endpoint
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/dfs/file/upload",
            HttpMethod.POST,
            requestEntity,
            String.class
        );

        // Validate the response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertNotNull(response.getBody(), "Response body should not be null");
    }

    @Test
    public void testDownloadFileByHash() throws IOException, NoSuchAlgorithmException {
        // First, upload the file to get a hash
        String fileHash = uploadFileAndGetHash();

        // Now, prepare to download the file using the hash
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("filepath", TARGET_DIR + "/" + TEST_FILE_PATH); // Assuming this maps to the file path
        requestBody.put("username", USER); // Assuming this maps to the username

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody);

        // Call the download endpoint
        ResponseEntity<Resource> response = restTemplate.exchange(
            baseUrl + "/dfs/file/download",  // Ensure this matches the actual endpoint URL
            HttpMethod.POST,  // Use POST since the endpoint is annotated with @PostMapping
            requestEntity,
            Resource.class
        );

        // Validate the response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        // Check the content of the downloaded file
        String downloadedContent = new String(response.getBody().getInputStream().readAllBytes());
        File file = new File(TEST_FILE_PATH);
        String originalContent = Files.readString(file.toPath());

        assertThat(downloadedContent).isEqualTo(originalContent);
    }

    private String uploadFileAndGetHash() throws IOException, NoSuchAlgorithmException {
        // Create a real MultipartFile from the test file
        File file = new File(TEST_FILE_PATH);
        CustomMultipartFile multipartFile = new CustomMultipartFile(file);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // Prepare request entity with file and parameters
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(file));
        body.add("user", USER);
        body.add("targetDir", TARGET_DIR);
        
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Call the upload endpoint
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl + "/dfs/file/upload",
            HttpMethod.POST,
            requestEntity,
            String.class
        );

        // Extract the file hash from the response (assumes response is the hash)
        return response.getBody(); // Adjust if the response format is different
    }
}
