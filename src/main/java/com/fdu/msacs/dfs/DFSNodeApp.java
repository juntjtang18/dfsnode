package com.fdu.msacs.dfs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import com.fdu.msacs.dfs.server.Config;
import jakarta.annotation.PostConstruct;

@SpringBootApplication
public class DFSNodeApp {
    private static final Logger logger = LoggerFactory.getLogger(DFSNodeApp.class);
    private RestTemplate restTemplate = new RestTemplate();
    
    @Value("${metadata.service.url}")
    private String metaServerUrl;
    @Value("${dfs.node.address}")
    private String nodeUrl;
	public static void main(String[] args) {
		SpringApplication.run(DFSNodeApp.class, args);
	}

	@PostConstruct
	public void registerNodeWithMetadataService() {
		Config config = new Config();
	    String nodeAddress = nodeUrl + ":" + config.getPort();  // Adjust to the actual node address and port
        logger.info("Registering node {} to MetaNode at: {}", nodeAddress, metaServerUrl);
        try {
            HttpEntity<String> request = new HttpEntity<>(nodeAddress);
            ResponseEntity<String> response = restTemplate.exchange(metaServerUrl + "/metadata/register-node", HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Node successfully registered. Server response: {}", response.getBody());
                return ;
            } else {
                logger.error("Failed to register node. Server returned status: {}", response.getStatusCode());
                return ;
            }
        } catch (Exception e) {
            logger.error("An error occurred during node registration", e);
            return ;
        }
	}

}
