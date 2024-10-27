package com.infolink.dfs.shared;

import java.io.IOException;
import java.io.RandomAccessFile;

public class BlockSchema {
    private static final int HASH_LENGTH = 64; // SHA-256 hash length in bytes
    private static final int TIMESTAMP_LENGTH = Long.BYTES; // Length of long
    private static final int REFERENCE_COUNT_LENGTH = Integer.BYTES; // Length of int
    private static final int SIZE_LENGTH = Integer.BYTES; // Length of int for size
    private static final int ENCRYPTED_LENGTH = 1; // Length of boolean

    private String hash;              // Hash of the block data
    private long offset;              // Offset of the block in storage
    private int referenceCount;       // Count of references to this block
    private int size;                 // Size of the block data
    private boolean encrypted;        // Flag indicating if the block is encrypted

	private long createdTimestamp;    // Timestamp for block creation
    private long modifiedTimestamp;   // Timestamp for last modification

    // Constructor
    public BlockSchema(String hash, long offset, int referenceCount, int size, boolean encrypted, long createdTimestamp, long modifiedTimestamp) {
        this.hash = hash;
        this.offset = offset;
        this.referenceCount = referenceCount;
        this.size = size;
        this.encrypted = encrypted;
        this.createdTimestamp = createdTimestamp;
        this.modifiedTimestamp = modifiedTimestamp;
    }

    // Getters
    public String getHash() {
        return hash;
    }

    public long getOffset() {
        return offset;
    }

    public int getReferenceCount() {
        return referenceCount;
    }

    public int getSize() {
        return size;
    }

    public boolean isEncrypted() {
        return encrypted;
    }
    public void setEncrypted(boolean encrypted) {
		this.encrypted = encrypted;
	}

    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    public long getModifiedTimestamp() {
        return modifiedTimestamp;
    }

    public void writeTo(RandomAccessFile raf) throws IOException {
        // Write the fixed-length hash
        byte[] hashBytes = new byte[HASH_LENGTH]; // Create a fixed-length byte array for the hash
        byte[] currentHashBytes = hash.getBytes(); // Get the current hash bytes

        // Fill the hashBytes with the current hash, ensuring it's fixed length
        System.arraycopy(currentHashBytes, 0, hashBytes, 0, Math.min(currentHashBytes.length, HASH_LENGTH));

        // Write the fixed-length hash to the file
        raf.write(hashBytes); // Write the fixed-length hash

        // Write other fields
        raf.writeLong(offset); // Write the offset
        raf.writeInt(referenceCount); // Write the reference count
        raf.writeInt(size); // Write the size of the block data
        raf.writeBoolean(encrypted); // Write the encryption flag
        raf.writeLong(createdTimestamp); // Write the created timestamp
        raf.writeLong(modifiedTimestamp); // Write the modified timestamp
    }

    public static BlockSchema readFrom(RandomAccessFile raf) throws IOException {
        // Read the fixed-length hash
        byte[] hashBytes = new byte[HASH_LENGTH]; // Create an array for the fixed-length hash
        raf.readFully(hashBytes); // Read the fixed-length hash from the file
        
        // Convert the hash bytes back to a string
        String hash = new String(hashBytes);
        hash = hash.trim();
        // Read the other fields
        long offset = raf.readLong(); // Read the offset
        int referenceCount = raf.readInt(); // Read the reference count
        int size = raf.readInt(); // Read the size of the block data
        boolean isEncrypted = raf.readBoolean(); // Read the encryption flag
        long createdTimestamp = raf.readLong(); // Read the created timestamp
        long modifiedTimestamp = raf.readLong(); // Read the modified timestamp

        return new BlockSchema(hash, offset, referenceCount, size, isEncrypted, createdTimestamp, modifiedTimestamp);
    }

    // Method to get the size of the serialized schema in bytes
    public static int getSerializedSize() {
        return HASH_LENGTH + Long.BYTES + REFERENCE_COUNT_LENGTH + SIZE_LENGTH +
               ENCRYPTED_LENGTH + TIMESTAMP_LENGTH * 2; // 2 timestamps
    }

	public void setSize(int length) {
		this.size = length;
	}

	public void setReferenceCount(int i) {
		this.referenceCount = i;
		
	}

	public void setOffset(long offset) {
		this.offset = offset;
	}
}
