package com.infolink.dfs;

public class RequestReplicationNodes {
    private String filename;
    private String requestingNodeUrl;

    // Getters and Setters
    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getRequestingNodeUrl() {
        return requestingNodeUrl;
    }

    public void setRequestingNodeUrl(String requestingNodeUrl) {
        this.requestingNodeUrl = requestingNodeUrl;
    }
}