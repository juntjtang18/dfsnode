package com.fdu.msacs.dfs.metanode;

public class DfsNode {
    private String containerUrl;
    private String localUrl;

    public DfsNode() {
        this.containerUrl = "";
        this.localUrl = "";
    }

    public DfsNode(String containerUrl) {
        this.containerUrl = containerUrl;
        this.localUrl = "";
    }

    public DfsNode(String containerUrl, String localUrl) {
        this.containerUrl = containerUrl;
        this.localUrl = localUrl;
    }

    public String getContainerUrl() {
        return containerUrl;
    }

    public void setContainerUrl(String containerUrl) {
        this.containerUrl = containerUrl;
    }

    public String getLocalUrl() {
        return localUrl;
    }

    public void setLocalUrl(String localUrl) {
        this.localUrl = localUrl;
    }

    @Override
    public String toString() {
        return "DfsNode{" +
                "nodeUrl='" + containerUrl + '\'' +
                ", hostUrl='" + localUrl + '\'' +
                '}';
    }
}
