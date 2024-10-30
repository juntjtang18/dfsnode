package com.infolink.dfs;

import java.util.Arrays;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import com.infolink.dfs.bfs.BlockStorage;
import com.infolink.dfs.bfs.EncryptorAES;

import jakarta.annotation.PostConstruct;

@Configuration

public class AppBeanConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppBeanConfig.class);
	
    private final KeyStoreManager keyStoreManager;
    private String redisHost;
    
    @PostConstruct
    public void init() {
        // Set MongoDB URI dynamically based on environment
        String effectiveMongoUri = EnvironmentUtils.isInDocker() 
            ? "mongodb://mongodb:27017" 
            : "mongodb://localhost:27017";
        System.setProperty("spring.data.mongodb.uri", effectiveMongoUri);
        
        // Set Redis host dynamically based on environment
        this.redisHost = EnvironmentUtils.isInDocker() ? "redis" : "localhost";
        System.setProperty("spring.redis.host", redisHost);
        
        logger.info("MongoDB URI set to: {}", effectiveMongoUri);
        logger.info("Redis host set to: {}", redisHost);
    }

    public AppBeanConfig(KeyStoreManager keyStoreManager) {
        this.keyStoreManager = keyStoreManager; // Inject KeyStoreManager
    }
    
    @Bean
    public SecretKey secretKey() throws Exception {
        return keyStoreManager.loadOrCreateSecretKey(); // Create SecretKey bean
    }

    @Bean
    public EncryptorAES encryptorAES(SecretKey secretKey) {
        return new EncryptorAES(secretKey); // Create EncryptorAES bean with injected SecretKey
    }
    
    @Bean
    public BlockStorage blockStorage(EncryptorAES encryptor) {
    	String rootdir = Config.getAppDir();
    	return new BlockStorage(encryptor, rootdir);
    }
    
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // Use the dynamically set redisHost here
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redisHost, 6379);
        return connectionFactory;
    }
    
    @Bean
    public RedisTemplate<String, Long> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Long> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Long.class));
        return template;
    }
    
    //@Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
        return args -> {
            String[] beanNames = ctx.getBeanDefinitionNames();
            Arrays.sort(beanNames);
            for (String beanName : beanNames) {
                System.out.println(beanName);
            }
        };
    }
}
