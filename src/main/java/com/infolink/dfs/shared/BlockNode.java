package com.infolink.dfs.shared;


import java.util.HashSet;
import java.util.Set;

public class BlockNode {
    private String hash;
    private Set<String> nodeUrls = new HashSet<String>();
    
	public String getHash() {
		return hash;
	}
	public void setHash(String hash) {
		this.hash = hash;
	}
	public Set<String> getNodeUrls() {
		return nodeUrls;
	}
	public void setNodeUrls(Set<String> nodeUrls) {
		this.nodeUrls = nodeUrls;
	}

}