package com.infolink.dfs.metanode;

import com.infolink.dfs.shared.DfsFile;

public class RequestSaveFile {
    private DfsFile dfsFile;
    private String targetDirectory;

    // Getters and Setters
    public DfsFile getDfsFile() {
        return dfsFile;
    }

    public void setDfsFile(DfsFile dfsFile) {
        this.dfsFile = dfsFile;
    }

    public String getTargetDirectory() {
        return targetDirectory;
    }

    public void setTargetDirectory(String targetDirectory) {
        this.targetDirectory = targetDirectory;
    }
}
