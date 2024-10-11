package com.fdu.msacs.dfs.server;

public class RequestFileLocation {
    private String filename;
    private String nodeUrl;

    // Default constructor for serialization/deserialization
    public RequestFileLocation() {
    }

    // Parameterized constructor
    public RequestFileLocation(String filename, String nodeUrl) {
        this.filename = filename;
        this.nodeUrl = nodeUrl;
    }

    // Getters and setters
    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getNodeUrl() {
        return nodeUrl;
    }

    public void setNodeUrl(String nodeUrl) {
        this.nodeUrl = nodeUrl;
    }

    @Override
    public String toString() {
        return "FileLocationRequest{" +
               "filename='" + filename + '\'' +
               ", nodeUrl='" + nodeUrl + '\'' +
               '}';
    }
}
