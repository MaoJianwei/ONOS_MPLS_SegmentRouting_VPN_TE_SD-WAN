package org.onosproject.mao.sdwan.api;

/**
 * Created by mao on 17-4-14.
 */
public interface SdwanService {

    /**
     * Creates one VPN tunnel.
     *
     * @param tenantName name of tenant
     * @param srcSite dpid and port no. of source device, e.g. of:0000000000000001/1
     * @param dstSite dpid and port no. of destination device, e.g. of:0000000000000001/1
     * @return
     */
    boolean createVPN(String tenantName, String srcSite, String dstSite);
}
