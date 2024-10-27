package com.infolink.dfs.bfs;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.NoSuchAlgorithmException;

public class Block {
    private BlockSchema schema;
    private byte[] data;

    public Block(BlockSchema schema, byte[] data) {
        this.schema = schema;
        this.data = data;
    }
    
    public Block(byte[] data, boolean encrypt, String hash) throws NoSuchAlgorithmException {
        this.data = data;
        this.schema = new BlockSchema(hash, 0, 1, this.data.length, encrypt, System.currentTimeMillis(), System.currentTimeMillis());
    }

    public BlockSchema getSchema() {
        return schema;
    }

    public byte[] getData() {
        return data;
    }

    public boolean isEncrypted() {
        return schema.isEncrypted();
    }

    public int getSize() {
        return schema.getSize();
    }

    public void encrypt(Encryptor encryptor) throws IOException {
        if (schema.isEncrypted()) {
            data = encryptor.encrypt(data);
            schema.setSize(data.length); // Update size after encryption
        }
    }

    public void decrypt(Encryptor encryptor) throws IOException {
        if (schema.isEncrypted()) {
            data = encryptor.decrypt(data);
            schema.setSize(data.length);
        }
    }

    public void writeTo(RandomAccessFile file) throws IOException {
        file.write(data);
    }
    
    public void readFromFile(RandomAccessFile file, Encryptor encryptor) throws IOException, NoSuchAlgorithmException {
        byte[] blockData = new byte[schema.getSize()];
        file.readFully(blockData);
        //if (schema.isEncrypted()) {
        //    blockData = encryptor.decrypt(blockData);
        //    schema.setSize(blockData.length);
        //}
        this.data = blockData;
    }
}
