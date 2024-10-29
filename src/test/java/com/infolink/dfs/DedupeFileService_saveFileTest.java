package com.infolink.dfs;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.http.HttpStatus.OK;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.infolink.dfs.shared.DfsNode;

@SpringBootTest
public class DedupeFileService_saveFileTest {
    private   String TEST_FILE_PATH = "testFile.txt";
    private   String USER = "testUser";
    private   String TARGET_DIR = "testTargetDir";
    
    @Autowired
    private DedupeFileService fileService;
    
    @Autowired
    private BlockService blockService;
    
    @Autowired
    private Config config;
    
    private  String metaNodeUrl;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @BeforeEach
    public void setUp() throws IOException {
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

        // Create the target directory for storing files
        File targetDirectory = new File(TARGET_DIR);
        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs();
        }

        // Create a temporary test file with specific content
        createTestFile(TEST_FILE_PATH);
        String containerUrl = config.getContainerName();
        String localUrl = config.getLocalUrl();
        if (containerUrl==null) containerUrl = localUrl;
        System.out.println("ContainerUrl=" + containerUrl + "  localUrl=" + localUrl);
        
        clearBlockFilesOnAllNodes();
    }
    
    void clearBlockFilesOnAllNodes() {
    	blockService.clearBlockFiles();
        ResponseEntity<List<DfsNode>> response = restTemplate.exchange(
                metaNodeUrl + "/metadata/get-registered-nodes",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<DfsNode>>() {}
        );
        List<DfsNode> dfsNodes = response.getBody();
    	for(DfsNode node : dfsNodes) {
    		clearBlockFiles(node.getLocalUrl());
    	}
    }
    
    public String clearBlockFiles(String nodeUrl) {
    	try {
	        String url = nodeUrl + "/dfs/block/clear-files";
	        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, null, String.class);
	        return response.getBody();
    	} catch(Exception e) {
    		return e.getMessage();
    	}
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
    public  void tearDown() {
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
    
    @Test
    public void testDedupeSaveFile() throws IOException, NoSuchAlgorithmException {
        // Create a real MultipartFile from the test file
        //byte[] fileContent = Files.readAllBytes(new File(TEST_FILE_PATH).toPath());
        File file = new File(TEST_FILE_PATH);
        CustomMultipartFile multipartFile = new CustomMultipartFile(file);

        // Call the dedupeSaveFile method
        String fileHash = fileService.dedupeSaveFile(multipartFile, USER, TARGET_DIR);

        // Validate the result
        assertNotNull(fileHash, "File hash should not be null");
        
        ResponseEntity<Resource> response = fileService.downloadFile(fileHash);
        // Assert
        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().contentLength()).isGreaterThan(0);

        // Check the content of the downloaded file
        String downloadedContent = new String(response.getBody().getInputStream().readAllBytes());
        
        String originalContent = Files.readString(file.toPath());

        assertThat(downloadedContent).isEqualTo(originalContent);
        
    }
}
