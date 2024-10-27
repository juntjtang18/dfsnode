package com.infolink.dfs.shared;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Date;
import java.util.Objects;

public class FileMeta implements Serializable {
    private static final long serialVersionUID = 1L; // Unique identifier for serialization
    private String userid;		
    private String filename;   	// full path
    private long size;      // Size of the file
    private Date createTime;   
    private Date lastModifiedTime;
    private String hash;

    // Constructor
    public FileMeta(String fileName, long fileSize, String timestamp) {
        this.filename = fileName;
        this.size = fileSize;
        this.createTime = new Date();
        this.setLastModifiedTime(new Date());
    }
    
    public FileMeta() {
        this.filename = null;
        this.size = 0;
        this.createTime = new Date();
        this.setLastModifiedTime(new Date());
    }

    // Getters and setters
    public String getFilename() {
        return filename;
    }

    public long getSize() {
        return size;
    }

    public Date getCreateTime() {
        return createTime;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileMeta)) return false;
        FileMeta fileMeta = (FileMeta) o;
        return size == fileMeta.size &&
               Objects.equals(filename, fileMeta.filename) &&
               Objects.equals(createTime, fileMeta.createTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filename, size, createTime);
    }

    public static Comparator<FileMeta> byFileName() {
        return Comparator.comparing(FileMeta::getFilename);
    }

    public static Comparator<FileMeta> byFileSize() {
        return Comparator.comparingLong(FileMeta::getSize);
    }

    public static Comparator<FileMeta> byCreateTime() {
        return Comparator.comparing(FileMeta::getCreateTime);
    }
    // toString method
    @Override
    public String toString() {
        return "FileMeta{" +
                "fileName='" + filename + '\'' +
                ", fileSize=" + size +
                ", timestamp='" + createTime + '\'' +
                '}';
    }

	public String getUserid() {
		return userid;
	}

	public void setUserid(String userid) {
		this.userid = userid;
	}

	public Date getLastModifiedTime() {
		return lastModifiedTime;
	}

	public void setLastModifiedTime(Date lastModifiedTime) {
		this.lastModifiedTime = lastModifiedTime;
	}

	public String getHash() {
		return hash;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}
}
