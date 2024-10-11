package com.fdu.msacs.dfs.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import com.fdu.msacs.dfs.DFSNodeApp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = DFSNodeApp.class)
public class FileServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(FileServiceTest.class);

    @Autowired
    private FileService fileService;

    @Autowired
    private TestRestTemplate restTemplate;
    private final String metadataBaseUrl = "http://localhost:8080/metadata";

    @BeforeEach
    public void setUp() throws IOException {
        // Clear registered nodes before each test
        restTemplate.postForEntity(metadataBaseUrl + "/clear-registered-nodes", null, String.class);
        restTemplate.postForEntity(metadataBaseUrl + "/clear-cache", null, String.class);
    }

    @AfterEach
    public void tearDown() throws IOException {
        // Optionally clean up the file storage directory after tests
        Path rootDir = fileService.getRootDir(); // Assuming you have a getter for rootDir
        Files.walk(rootDir)
                .sorted((o1, o2) -> -o1.compareTo(o2)) // Sort in reverse order to delete files first
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }

    @Test
    public void testSaveFile() throws IOException {
        // Define a path for a real test file
        Path testFilePath = Paths.get("D:/develop/dfs/target/dfs/file-storage", "testfile.txt");

        // Ensure the parent directories exist
        if (!Files.exists(testFilePath.getParent())) {
            Files.createDirectories(testFilePath.getParent());
        }

        // Write content to the file
        Files.write(testFilePath, "Hello World".getBytes());

        // Convert the real file into a MultipartFile
        MultipartFile multipartFile = createMultipartFile(testFilePath);

        // Save the file
        fileService.saveFile(multipartFile);

        // Verify the file is saved
        Path savedFilePath = fileService.getFilePath("testfile.txt");
        assertNotNull(savedFilePath);
        assertTrue(Files.exists(savedFilePath));
        assertEquals("Hello World", new String(Files.readAllBytes(savedFilePath)));

        // Clean up the file after test
        Files.deleteIfExists(testFilePath);
    }
    @Test
    public void testSaveFileAndReplicate() throws IOException {
        // Register a node for replication
        restTemplate.postForEntity(metadataBaseUrl + "/register-node", "http://localhost:8081", String.class); // Simulating another node

        // Create a real file on the local file system
        Path testFilePath = Paths.get("D:/develop/dfs/target/dfs/file-storage", "replicate_test.txt");
        if (!Files.exists(testFilePath.getParent())) {
            Files.createDirectories(testFilePath.getParent()); // Ensure the directory exists
        }
        Files.write(testFilePath, "Replicate Me".getBytes());

        // Create a MultipartFile from the real file
        MultipartFile multipartFile = createMultipartFile(testFilePath);

        // Save the file and start replication
        fileService.saveFileAndReplicate(multipartFile);

        // Verify the file is saved
        Path savedFilePath = fileService.getFilePath("replicate_test.txt");
        assertNotNull(savedFilePath);
        assertTrue(Files.exists(savedFilePath));
        assertEquals("Replicate Me", new String(Files.readAllBytes(savedFilePath)));

        // Verify the file is registered with the metadata node
        ResponseEntity<List<String>> response = restTemplate.exchange(metadataBaseUrl + "/get-file-node-mapping/replicate_test.txt",
                HttpMethod.GET, null, new ParameterizedTypeReference<List<String>>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("http://localhost:8081")); // Check if replication was initiated

        // Clean up the file after test
        Files.deleteIfExists(testFilePath);
    }

    private MultipartFile createMultipartFile(Path filePath) throws IOException {
        return new MultipartFile() {
            @Override
            public String getName() {
                return filePath.getFileName().toString();
            }

            @Override
            public String getOriginalFilename() {
                return filePath.getFileName().toString();
            }

            @Override
            public String getContentType() {
                try {
                    return Files.probeContentType(filePath);
                } catch (IOException e) {
                    return "application/octet-stream";
                }
            }

            @Override
            public boolean isEmpty() {
                return filePath.toFile().length() == 0;
            }

            @Override
            public long getSize() {
                return filePath.toFile().length();
            }

            @Override
            public byte[] getBytes() throws IOException {
                try (FileInputStream inputStream = new FileInputStream(filePath.toFile())) {
                    return inputStream.readAllBytes();
                }
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return new FileInputStream(filePath.toFile());
            }

            @Override
            public void transferTo(File dest) throws IOException, IllegalStateException {
                Files.copy(filePath, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        };
    }


    @Test
    public void testGetFile() throws IOException {
        // Create and save a real file
        String testFilePath = "D:\\develop\\dfs\\target\\dfs\\file-storage\\retrieve_test.txt";
        Files.createDirectories(Paths.get(testFilePath).getParent()); // Ensure the directory exists
        Files.write(Paths.get(testFilePath), "Retrieve Me".getBytes());

        // Create MultipartFile from the real file
        MultipartFile multipartFile = createMultipartFile(Paths.get(testFilePath));

        // Save the file using the actual fileService method
        fileService.saveFile(multipartFile);

        // Retrieve the file
        byte[] fileContent = fileService.getFile("retrieve_test.txt");
        assertNotNull(fileContent);
        assertEquals("Retrieve Me", new String(fileContent));

        // Clean up the file after the test
        Files.deleteIfExists(Paths.get(testFilePath));
    }

    @Test
    public void testGetFileList() throws IOException {
        // Create the necessary directories for the test files
        Path testDirectory = Paths.get("D:\\develop\\dfs\\target\\dfs-test\\file-storage");
        Files.createDirectories(testDirectory); // Ensure the directory exists

        // Create two real files in the filesystem
        Path filePath1 = testDirectory.resolve("file1.txt");
        Path filePath2 = testDirectory.resolve("file2.txt");

        // Write content to the files
        Files.write(filePath1, "File 1".getBytes());
        Files.write(filePath2, "File 2".getBytes());

        // Create MultipartFile objects from the real files
        MultipartFile multipartFile1 = createMultipartFile(filePath1);
        MultipartFile multipartFile2 = createMultipartFile(filePath2);

        // Save the files using the service
        fileService.saveFile(multipartFile1);
        fileService.saveFile(multipartFile2);

        // Retrieve the list of files
        List<String> fileList = fileService.getFileList();

        // Verify the file list contains the expected files
        assertNotNull(fileList);
        assertTrue(fileList.contains("file1.txt"));
        assertTrue(fileList.contains("file2.txt"));

        // Clean up: delete the test files after the test
        Files.deleteIfExists(filePath1);
        Files.deleteIfExists(filePath2);
        
        // Clean up the directory
        deleteDirectory(testDirectory);
    }

    public void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            // First, delete all files in the directory
            try (Stream<Path> files = Files.list(path)) {
                files.forEach(file -> {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        logger.error("Failed to delete file: {}", file, e);
                    }
                });
            }

            // Then, delete the directory itself
            Files.delete(path);
        }
    }
}
