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

    @Test
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
    
    @Test
    public void testDedupeSaveFile_Success() throws Exception {
        // Prepare a mock file for upload
        String fileContent = "This is a test file for deduplication service.";
        InputStream inputStream = new ByteArrayInputStream(fileContent.getBytes());
        MultipartFile file = new MockMultipartFile(
                "file", 
                "testfile.txt", 
                MediaType.TEXT_PLAIN_VALUE, 
                inputStream
        );

        // Define test parameters
        String user = "testUser";
        String targetDir = "/testDirectory";

        // Call the dedupeSaveFile method
        String fileHash = dedupeFileService.dedupeSaveFile(file, user, targetDir);

        // Verify that the file hash is not null or empty
        assertNotNull(fileHash, "File hash should not be null");
        assertFalse(fileHash.isEmpty(), "File hash should not be empty");

        // Fetch the saved file metadata from the MetaNode to verify the save
        String fileUrl = config.getMetaNodeUrl() + "/metadata/file/" + fileHash;
        DfsFile savedDfsFile = restTemplate.getForObject(fileUrl, DfsFile.class);
        assertNotNull(savedDfsFile, "DfsFile should be fetched successfully from MetaNode");

        // Verify the file metadata
        assertEquals(fileHash, savedDfsFile.getHash(), "The hash of the saved DfsFile should match the calculated hash");
        assertEquals(user, savedDfsFile.getOwner(), "The owner of the saved DfsFile should match the provided user");
        assertEquals(targetDir + "/testfile.txt", savedDfsFile.getPath(), "The file path should be correct");
        assertEquals(file.getSize(), savedDfsFile.getSize(), "The file size should match the uploaded file size");
    }

    @Test
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
    public void testGetDfsFileByPath_Success() {
        // Assume a file has already been stored and we have its path
    	String username = "user";
        String filePath = "/testDirectory/testfile.txt";
        
        // Fetch the file metadata by its path
        DfsFile dfsFile = dedupeFileService.getDfsFileByPath(username, filePath);

        // Verify that the DfsFile object is not null
        assertNotNull(dfsFile, "DfsFile should be fetched successfully");
        
        // Verify that the path matches the requested path
        assertEquals(filePath, dfsFile.getPath(), "The path of the DfsFile should match the requested path");
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
}
