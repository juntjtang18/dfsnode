package com.infolink.dfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.infolink.dfs.metanode.RequestSaveFile;
import com.infolink.dfs.shared.DfsFile;
import com.infolink.dfs.shared.HashUtil;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
public class DedupeFileServiceIntegrationTest {

    @Autowired
    private DedupeFileService dedupeFileService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private Config config;
    private String baseUrl;
    
    public DedupeFileServiceIntegrationTest() {

    }
    
    @BeforeEach
    public void setup() {
        this.baseUrl = config.getMetaNodeUrl();
        // Clear all DFS file data before each test case
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/metadata/file/clear-all-data", 
                    HttpMethod.DELETE, 
                    null, 
                    String.class
            );
        } catch (Exception e) {
        	e.printStackTrace();
        }

        // Additional setup can be performed here if needed
        assertNotNull(config.getMetaNodeUrl(), "MetaNode URL should be configured for testing.");
    }
    
    private ResponseEntity<String> saveTestFile(DfsFile dfsFile, String targetDirectory) {
        // Create the request object
        RequestSaveFile request = new RequestSaveFile();
        request.setDfsFile(dfsFile);
        request.setTargetDirectory(targetDirectory);

        // Create the headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RequestSaveFile> requestEntity = new HttpEntity<>(request, headers);

        // Send the POST request to save the file
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/metadata/file/save", requestEntity, String.class);

        // Verify that the file is saved successfully
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response;
    }

    //@Test
    public void testSaveFile() {
        // Create a DfsFile object to send in the request body
        DfsFile dfsFile = new DfsFile();
        dfsFile.setHash("hash1234");
        dfsFile.setName("example2.txt");
        dfsFile.setOwner("testOwner");
        dfsFile.setPath("/test-directory/example.txt");
        dfsFile.setSize(1024);
        dfsFile.setDirectory(false);
        dfsFile.setParentHash("parentHash123");
        dfsFile.setBlockHashes(Collections.singletonList("blockHash1"));
        dfsFile.setCreateTime(new Date());
        dfsFile.setLastModifiedTime(new Date());

        // Define the target directory
        String targetDirectory = "/test-directory";

        // Save the test file
        //saveTestFile(dfsFile, targetDirectory);

        // Verify the response status and body
        ResponseEntity<String> response = saveTestFile(dfsFile, targetDirectory);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo("File saved successfully.");
    }
    
    //@Test
    public void testGetDfsFileByHash_Success() {
        DfsFile dfsFile = new DfsFile();
        dfsFile.setHash("hash1234");
        dfsFile.setName("example2.txt");
        dfsFile.setOwner("testOwner");
        dfsFile.setPath("/test-directory/example.txt");
        dfsFile.setSize(1024);
        dfsFile.setDirectory(false);
        dfsFile.setParentHash("parentHash123");
        dfsFile.setBlockHashes(Collections.singletonList("blockHash1"));
        dfsFile.setCreateTime(new Date());
        dfsFile.setLastModifiedTime(new Date());

        // Define the target directory
        String targetDirectory = "/test-directory";

        // Save the test file
        //saveTestFile(dfsFile, targetDirectory);

        // Verify the response status and body
        ResponseEntity<String> response = saveTestFile(dfsFile, targetDirectory);
    	
        String fileHash = dfsFile.getHash();
        
        // Fetch the file metadata by its hash
        DfsFile dfsFile2 = dedupeFileService.getDfsFileByHash(fileHash);

        // Verify that the DfsFile object is not null
        assertNotNull(dfsFile2, "DfsFile should be fetched successfully");
        
        // Verify additional properties of the DfsFile if needed
        assertEquals(fileHash, dfsFile2.getHash(), "The hash of the DfsFile should match the requested hash");
    }

    //@Test
    public void testCalculateFileHash_Success() throws NoSuchAlgorithmException {
        // Create a list of block hashes to simulate the blocks of a file
        List<String> blockHashes = List.of(
                HashUtil.calculateHash("block1".getBytes()),
                HashUtil.calculateHash("block2".getBytes()),
                HashUtil.calculateHash("block3".getBytes())
        );

        // Calculate the file hash from the block hashes
        String fileHash = dedupeFileService.calculateFileHash(blockHashes);

        // Verify that the calculated file hash is not null or empty
        assertNotNull(fileHash, "Calculated file hash should not be null");
        assertFalse(fileHash.isEmpty(), "Calculated file hash should not be empty");
    }
    
    @Test
    public void testGetDfsFileByPath_Success() {
        // Set up a file for testing
        DfsFile dfsFile = new DfsFile();
        dfsFile.setHash("hash5678");
        dfsFile.setName("example_success.txt");
        dfsFile.setOwner("testUser");
        dfsFile.setPath("/test-directory/example_success.txt");
        dfsFile.setSize(2048);
        dfsFile.setDirectory(false);
        dfsFile.setParentHash("parentHash456");
        dfsFile.setBlockHashes(Collections.singletonList("blockHash2"));
        dfsFile.setCreateTime(new Date());
        dfsFile.setLastModifiedTime(new Date());

        // Define the target directory
        String targetDirectory = "/test-directory";

        // Save the test file
        ResponseEntity<String> response = saveTestFile(dfsFile, targetDirectory);

        // Check that file was saved successfully
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo("File saved successfully.");

        // Fetch the file metadata by its path
        DfsFile fetchedFile = dedupeFileService.getDfsFileByPath("testUser", dfsFile.getPath());

        // Verify that the fetched DfsFile object is not null
        assertNotNull(fetchedFile, "DfsFile should be fetched successfully");
        
        // Verify additional properties of the DfsFile to match the expected values
        assertEquals(dfsFile.getPath(), fetchedFile.getPath(), "The path of the fetched DfsFile should match the requested path");
        assertEquals(dfsFile.getName(), fetchedFile.getName(), "The name of the fetched DfsFile should match the expected name");
        assertEquals(dfsFile.getOwner(), fetchedFile.getOwner(), "The owner of the fetched DfsFile should match the expected owner");
    }

    @Test
    public void testGetDfsFileByPath_FileNotFound() {
        // Attempt to fetch a file metadata by a path that does not exist
        String username = "testUser";
        String nonExistentFilePath = "/non-existent-directory/non-existent-file.txt";
        DfsFile fetchedFile = dedupeFileService.getDfsFileByPath(username, nonExistentFilePath);

        // Verify that the fetched DfsFile object is null since the file does not exist
        assertNull(fetchedFile, "DfsFile should be null when the file does not exist");
    }

}
