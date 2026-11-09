package com.dji.sdk.cloudapi.device;

import java.util.List;

public class DockWirelessLinkTopo {
    public List<Integer> secretCode;

    public CenterNode centerNode;

    public List<LeafNodes> leafNodes;

    @Override
    public String toString() {
        return "DockWirelessLinkTopo{" +
                "secretCode=" + secretCode +
                ", centerNode=" + centerNode +
                ", leafNodes=" + leafNodes +
                '}';
    }

    public List<Integer> getSecretCode() {
        return secretCode;
    }

    public void setSecretCode(List<Integer> secretCode) {
        this.secretCode = secretCode;
    }

    public CenterNode getCenterNode() {
        return centerNode;
    }

    public void setCenterNode(CenterNode centerNode) {
        this.centerNode = centerNode;
    }

    public List<LeafNodes> getLeafNodes() {
        return leafNodes;
    }

    public void setLeafNodes(List<LeafNodes> leafNodes) {
        this.leafNodes = leafNodes;
    }
}

