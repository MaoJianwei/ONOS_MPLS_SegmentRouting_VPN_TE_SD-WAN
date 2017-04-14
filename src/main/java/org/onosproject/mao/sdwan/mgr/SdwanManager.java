/*
 * Copyright 2017-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.mao.sdwan.mgr;

import org.apache.commons.collections.map.MultiValueMap;
import org.apache.felix.scr.annotations.*;
import org.onosproject.mao.sdwan.api.SdwanService;
import org.onosproject.mao.sdwan.api.SdwanTunnel;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.onosproject.mao.sdwan.api.SdwanTunnel.INVALID_LABEL;
import static org.onosproject.mao.sdwan.api.SdwanTunnel.MAX_LABEL;
import static org.onosproject.net.DeviceId.deviceId;
import static org.onosproject.net.PortNumber.portNumber;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class SdwanManager implements SdwanService {

    private final Logger log = LoggerFactory.getLogger(getClass());


    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    private Map vpnDB = new MultiValueMap();
    private Set<Integer> usingLabels = new HashSet<>();
    private Random labelGenerator = new Random();


    @Activate
    protected void activate() {


        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        vpnDB.clear();
        usingLabels.clear();

        log.info("Stopped");
    }


    @Override
    public boolean createVPN(String tenantName, String srcSite, String dstSite) {

        SdwanTunnel.Builder tunnelBuilder = SdwanTunnel.builder().tenant(tenantName);

        ConnectPoint src = generateConnectPoint(srcSite);
        ConnectPoint dst = generateConnectPoint(dstSite);
        if(src == null || dst == null) {
            log.warn("Source or Destination site doesn't match the scheme, e.g. of:0000000000000001/1");
            return false;
        }

        tunnelBuilder.src(src).dst(dst);

        Set paths = topologyService.getPaths(topologyService.currentTopology(),
                src.deviceId(), dst.deviceId());
        if(paths.isEmpty()) {
            log.warn("Can't find a route from {} to {}", src.deviceId(), dst.deviceId());
            return false;
        }

        // In present, use same route for both unidirectional tunnels.




        return false;
    }

    /**
     * Return new ConnectPoint object.
     *
     * @param dpidPort combine of dpid and port no., e.g. of:0000000000000001/1
     * @return new ConnectPoint object
     */
    private ConnectPoint generateConnectPoint(String dpidPort) {

        String [] dpidAndPort = dpidPort.split("/");

        if(dpidAndPort.length != 2){
            return null;
        }

        return new ConnectPoint(deviceId(dpidAndPort[0]), portNumber(dpidAndPort[1]));
    }

    /**
     * Get new available label number.
     *
     * Adopt random value and incremental mechanism to avoid conflict.
     *
     * Default, try to avoid conflict 3 times.
     *
     * @return new label number.
     */
    private int newLabel() {
        return newLabel(3);
    }

    /**
     * Get new available label number.
     *
     * Adopt random value and incremental mechanism to avoid conflict.
     *
     * Used in a recursive way.
     *
     * @param tryCount times that we find a new random value and try to avoid conflict again
     * @return new label number.
     */
    private int newLabel(int tryCount) {

        if(tryCount <= 0) {
            return INVALID_LABEL;
        }

        int label = labelGenerator.nextInt(MAX_LABEL + 1);
        int freeLabel = label;

        // try to make sure label is not used and available, 10 times.
        while(usingLabels.contains(freeLabel)
                && freeLabel - label < 10
                && freeLabel + 1 <= MAX_LABEL) {
            freeLabel++;
        }

        // 10 tries are all failed, find a new random value and try again.
        if(usingLabels.contains(freeLabel)){
            return newLabel(tryCount - 1);
        }

        return freeLabel;
    }
}
