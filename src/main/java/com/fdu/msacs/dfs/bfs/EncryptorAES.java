package com.fdu.msacs.dfs.bfs;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;

@Service
public class EncryptorAES implements Encryptor {
    private final SecretKey secretKey;

    // Constructor to accept a SecretKey
    public EncryptorAES(SecretKey secretKey) {
        this.secretKey = secretKey;
    }

    // Method to encrypt data
    @Override
    public byte[] encrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting data", e);
        }
    }

    // Method to decrypt data
    @Override
    public byte[] decrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting data", e);
        }
    }

    // Static method to generate a new SecretKey
    public static SecretKey generateSecretKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128); // You can choose 128, 192, or 256 bits
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating secret key", e);
        }
    }

    // Static method to create an AESEncryptor with a new SecretKey
    public static EncryptorAES createWithNewKey() {
        return new EncryptorAES(generateSecretKey());
    }

    // Static method to create an AESEncryptor with an existing SecretKey
    public static EncryptorAES createWithKey(byte[] keyBytes) {
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        return new EncryptorAES(keySpec);
    }
}
