package com.infolink.dfs;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import org.springframework.stereotype.Component;

@Component
public class KeyStoreManager {
    private final String keystoreFilePath;
    private final String keyAlias; // Alias for the secret key
    private final String keystorePassword; // Keystore password
    private final String keyPassword; // Password for the secret key

    // Constructor that accepts a Config instance
    public KeyStoreManager(Config config) {
        this.keystoreFilePath = config.getKeyStoreFilePath(); // Retrieve keystore path from config
        this.keyAlias = config.getKeyAlias(); // Retrieve key alias from config
        this.keystorePassword = config.getKeyStorePassword(); // Get keystore password from config
        this.keyPassword = config.getKeyPassword(); // Get key password from config
    }

    public SecretKey loadOrCreateSecretKey() throws Exception {
        File keystoreFile = new File(keystoreFilePath);
        KeyStore keyStore = KeyStore.getInstance("JCEKS"); // Change to "PKCS12" if needed

        if (keystoreFile.exists()) {
            // Load existing keystore
            try (FileInputStream fis = new FileInputStream(keystoreFile)) {
                keyStore.load(fis, keystorePassword.toCharArray());
            }
            // Retrieve the secret key from the keystore
            KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(keyAlias,
                    new KeyStore.PasswordProtection(keyPassword.toCharArray()));
            return secretKeyEntry.getSecretKey();
        } else {
            File parentDir = keystoreFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs(); // Create parent directories if they don't exist
            }
            keystoreFile.createNewFile(); // Create the keystore file
            
            // Create a new keystore and generate a secret key
            keyStore.load(null, null); // Initialize the keystore

            // Generate a new secret key
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128); // Specify the key size
            SecretKey secretKey = keyGen.generateKey();

            // Store the generated secret key in the keystore
            KeyStore.SecretKeyEntry secretKeyEntry = new KeyStore.SecretKeyEntry(secretKey);
            keyStore.setEntry(keyAlias, secretKeyEntry, new KeyStore.PasswordProtection(keyPassword.toCharArray()));

            // Save the keystore to the specified file
            try (FileOutputStream fos = new FileOutputStream(keystoreFile)) {
                keyStore.store(fos, keystorePassword.toCharArray());
            }

            return secretKey;
        }
    }

}
