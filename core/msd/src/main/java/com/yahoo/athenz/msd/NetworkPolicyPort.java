//
// This file generated by rdl 1.5.2. Do not modify!
//

package com.yahoo.athenz.msd;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yahoo.rdl.*;

//
// NetworkPolicyPort - network policy port.
//
@JsonIgnoreProperties(ignoreUnknown = true)
public class NetworkPolicyPort {
    public int port;
    public int endPort;
    public TransportPolicyProtocol protocol;

    public NetworkPolicyPort setPort(int port) {
        this.port = port;
        return this;
    }
    public int getPort() {
        return port;
    }
    public NetworkPolicyPort setEndPort(int endPort) {
        this.endPort = endPort;
        return this;
    }
    public int getEndPort() {
        return endPort;
    }
    public NetworkPolicyPort setProtocol(TransportPolicyProtocol protocol) {
        this.protocol = protocol;
        return this;
    }
    public TransportPolicyProtocol getProtocol() {
        return protocol;
    }

    @Override
    public boolean equals(Object another) {
        if (this != another) {
            if (another == null || another.getClass() != NetworkPolicyPort.class) {
                return false;
            }
            NetworkPolicyPort a = (NetworkPolicyPort) another;
            if (port != a.port) {
                return false;
            }
            if (endPort != a.endPort) {
                return false;
            }
            if (protocol == null ? a.protocol != null : !protocol.equals(a.protocol)) {
                return false;
            }
        }
        return true;
    }
}