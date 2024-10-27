package com.infolink.dfs.metanode;

import java.util.ArrayList;
import java.util.List;

import com.infolink.dfs.shared.DfsNode;

public class ResponseNodesForBlock {
    public static enum Status {
        SUCCESS,
        NO_NODES_AVAILABLE,
        ALREADY_ENOUGH_COPIES
    }

    private Status status;
    private List<DfsNode> nodes; // List of selected nodes
    
    public ResponseNodesForBlock() {
    	this.status = Status.SUCCESS;
    	this.nodes = new ArrayList<DfsNode>();
    }
    
    public ResponseNodesForBlock(Status status, List<DfsNode> nodes) {
    	this.status = status;
    	this.nodes = nodes;
    }
	public List<DfsNode> getNodes() 		{		return nodes;		}
	public void setNodes(List<DfsNode> nodes) {			this.nodes = nodes;		}
	public Status getStatus() 				{			return status;		}
	public void setStatus(Status status) 	{			this.status = status;		}
}
