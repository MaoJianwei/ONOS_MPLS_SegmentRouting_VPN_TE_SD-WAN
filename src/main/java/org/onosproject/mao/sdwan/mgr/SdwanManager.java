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

import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.felix.scr.annotations.*;
import org.onlab.packet.EthType;
import org.onlab.packet.MplsLabel;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.mao.sdwan.api.SdwanService;
import org.onosproject.mao.sdwan.api.SdwanTunnel;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.onlab.packet.MplsLabel.mplsLabel;
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
    private final int FLOWRULE_PRIORITY = 33333;


    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    private ApplicationId appId;
    private MultiMap vpnDB = new MultiValueMap();

    private Random labelGenerator = new Random();
    private Set<Integer> usingLabels = new HashSet<>();


    @Activate
    protected void activate() {

        appId = coreService.registerApplication("Mao.SDWAN");

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {

        flowRuleService.removeFlowRulesById(appId);

        vpnDB.clear();
        usingLabels.clear();

        log.info("Stopped");
    }


    @Override
    public boolean createVPN(String tenantName, String srcSite, String dstSite, int ethertypeCode) {

        SdwanTunnel tunnel = generateTunnel(tenantName, srcSite, dstSite, ethertypeCode);
        if (tunnel == null) {
            return false;
        }

        if(!setupTunnelConnectivity(tunnel)) {
            return false;
        }

        vpnDB.put(tunnel.getTenantName(), tunnel);

        return true;
    }

    private SdwanTunnel generateTunnel(String tenantName, String srcSite, String dstSite, int ethertypeCode) {

        SdwanTunnel.Builder tunnelBuilder = SdwanTunnel.builder().tenant(tenantName).networkProtocol(ethertypeCode);



        ConnectPoint src = generateConnectPoint(srcSite);
        ConnectPoint dst = generateConnectPoint(dstSite);
        if(src == null || dst == null) {
            log.warn("Source or Destination site doesn't match the scheme, e.g. of:0000000000000001/1");
            return null;
        }

        tunnelBuilder.src(src).dst(dst);



        Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(),
                src.deviceId(), dst.deviceId());
        if(paths.isEmpty()) {
            log.warn("Can't find a route from {} to {}", src.deviceId(), dst.deviceId());
            return null;
        }

        // TODO - make a decision that should we store Path or List<DeviceId> ?
        // TODO - and just do it !
        Path path = paths.iterator().next();
        Iterator<Link> links = path.links().iterator();
        Link firstLink = links.next();

        List<DeviceId> forwardDevices = new ArrayList<>();
        List<DeviceId> backwardDevices = new ArrayList<>();

        forwardDevices.add(firstLink.src().deviceId());
        forwardDevices.add(firstLink.dst().deviceId());
        links.forEachRemaining(link -> forwardDevices.add(link.dst().deviceId()));
        forwardDevices.forEach(dpid -> backwardDevices.add(0, dpid));

        tunnelBuilder.forwardDevices(forwardDevices).backwardDevices(backwardDevices);
        tunnelBuilder.forwardPath(path).backwardPath(path);



        // In present, use same route for both unidirectional tunnels.
        int forwardLabel = newLabel();
        int backwardLabel = newLabel();

        if(forwardLabel == INVALID_LABEL || backwardLabel == INVALID_LABEL) {
            log.warn("Fail to attempt to find a no-conflict tunnel label, please retry.");
            return null;
        }

        tunnelBuilder.forwardLabel(forwardLabel).backwardLabel(backwardLabel);



        return tunnelBuilder.build();
    }

    private boolean setupTunnelConnectivity(SdwanTunnel tunnel) {

        if(!setupForwardPathConnectivity(tunnel)) {
            // TODO - withdraw changes
            log.warn("Fail to setup Forward path!\nTunnel: {}", tunnel.toString());
            return false;
        }

        if(!setupBackwardPathConnectivity(tunnel)) {
            // TODO - withdraw changes
            log.warn("Fail to setup Backward path!\nTunnel: {}", tunnel.toString());
            return false;
        }

        log.info("Succeed to setup Forward path :)\nTunnel: {}", tunnel.toString());
        return true;
    }

    private boolean setupForwardPathConnectivity(SdwanTunnel tunnel) {

        List<FlowRule> tunnelFlows = new ArrayList<>();

        //TODO - check - deal with [empty, 1, n] pathLinks

        List<Link> pathLinks = tunnel.getForwardPath().links();
        if(pathLinks.isEmpty()) {

            if(tunnel.getSrcSite().deviceId().equals(tunnel.getDstSite().deviceId())) {
                log.warn("path is empty, but src and dst is not the identical ones! \n{}\nforward tunnel is not installed", tunnel.toString());
                return false;
            }

            TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchInPort(tunnel.getSrcSite().port())
                    .matchEthType((short)(tunnel.getNetworkProtocol() & 0xFFFF))
                    .build();

            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .immediate()
                    .setOutput(tunnel.getDstSite().port())
                    .build();

            FlowRule oneHopFlow = DefaultFlowRule.builder()
                    .forDevice(tunnel.getSrcSite().deviceId())
                    .forTable(0)
                    .fromApp(appId)
                    .makePermanent()
                    .withCookie(tunnel.getForwardLabel())
                    .withPriority(FLOWRULE_PRIORITY)
                    .withSelector(selector)
                    .withTreatment(treatment)
                    .build();

            flowRuleService.applyFlowRules(oneHopFlow);

            return true;
        }



        // ------------

        ConnectPoint entryFirstHop = tunnel.getSrcSite();
        ConnectPoint leaveFirstHop = pathLinks.get(0).src();


        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(entryFirstHop.port())
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .immediate()
                .pushMpls().setMpls(mplsLabel(tunnel.getForwardLabel()))
                .setOutput(leaveFirstHop.port())
                .build();

        FlowRule entryFlow = DefaultFlowRule.builder()
                .forDevice(entryFirstHop.deviceId())
                .forTable(0)
                .fromApp(appId)
                .makePermanent()
                .withCookie(tunnel.getForwardLabel())
                .withPriority(FLOWRULE_PRIORITY)
                .withSelector(selector)
                .withTreatment(treatment)
                .build();

        tunnelFlows.add(entryFlow);



        // ------------

        if(pathLinks.size() != 1) {
            selector = DefaultTrafficSelector.builder()
                    .matchEthType(EthType.EtherType.MPLS_UNICAST.ethType().toShort())
                    .matchMplsLabel(mplsLabel(tunnel.getForwardLabel()))
                    .build();

            for (int i = 1; i < pathLinks.size(); i++) {

                ConnectPoint cp = pathLinks.get(i).src();

                treatment = DefaultTrafficTreatment.builder()
                        .immediate()
                        .setOutput(cp.port())
                        .build();

                FlowRule intermediateFlow = DefaultFlowRule.builder()
                        .forDevice(cp.deviceId())
                        .forTable(0)
                        .fromApp(appId)
                        .makePermanent()
                        .withCookie(tunnel.getForwardLabel())
                        .withPriority(FLOWRULE_PRIORITY)
                        .withSelector(selector)
                        .withTreatment(treatment)
                        .build();

                tunnelFlows.add(intermediateFlow);
            }
        }



        // ------------

        ConnectPoint leaveLastHop = tunnel.getDstSite();

        selector = DefaultTrafficSelector.builder()
                .matchEthType(EthType.EtherType.MPLS_UNICAST.ethType().toShort())
                .matchMplsLabel(mplsLabel(tunnel.getForwardLabel()))
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .immediate()
                .popMpls(new EthType(tunnel.getNetworkProtocol()))
                .setOutput(leaveLastHop.port())
                .build();

        FlowRule leaveFlow = DefaultFlowRule.builder()
                .forDevice(leaveLastHop.deviceId())
                .forTable(0)
                .fromApp(appId)
                .makePermanent()
                .withCookie(tunnel.getForwardLabel())
                .withPriority(FLOWRULE_PRIORITY)
                .withSelector(selector)
                .withTreatment(treatment)
                .build();

        tunnelFlows.add(leaveFlow);



        // ------------

        FlowRule [] flows = (FlowRule []) tunnelFlows.toArray();

        flowRuleService.applyFlowRules(flows);

        return true;
    }

    private boolean setupBackwardPathConnectivity(SdwanTunnel tunnel) {


        List<FlowRule> tunnelFlows = new ArrayList<>();

        //TODO - check - deal with [empty, 1, n] pathLinks

        List<Link> pathLinks = tunnel.getBackwardPath().links();
        if(pathLinks.isEmpty()) {

            if(tunnel.getDstSite().deviceId().equals(tunnel.getSrcSite().deviceId())) {
                log.warn("path is empty, but dst and src is not the identical ones! \n{}\nbackward tunnel is not installed", tunnel.toString());
                return false;
            }

            TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchInPort(tunnel.getDstSite().port())
                    .matchEthType((short)(tunnel.getNetworkProtocol() & 0xFFFF))
                    .build();

            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .immediate()
                    .setOutput(tunnel.getSrcSite().port())
                    .build();

            FlowRule oneHopFlow = DefaultFlowRule.builder()
                    .forDevice(tunnel.getDstSite().deviceId())
                    .forTable(0)
                    .fromApp(appId)
                    .makePermanent()
                    .withCookie(tunnel.getBackwardLabel())
                    .withPriority(FLOWRULE_PRIORITY)
                    .withSelector(selector)
                    .withTreatment(treatment)
                    .build();

            flowRuleService.applyFlowRules(oneHopFlow);

            return true;
        }



        // ------------

        // TODO - path is the same one with forward path !!!

        ConnectPoint entryFirstHop = tunnel.getDstSite();
        ConnectPoint leaveFirstHop = pathLinks.get(pathLinks.size()-1).dst();


        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(entryFirstHop.port())
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .immediate()
                .pushMpls().setMpls(mplsLabel(tunnel.getBackwardLabel()))
                .setOutput(leaveFirstHop.port())
                .build();

        FlowRule entryFlow = DefaultFlowRule.builder()
                .forDevice(entryFirstHop.deviceId())
                .forTable(0)
                .fromApp(appId)
                .makePermanent()
                .withCookie(tunnel.getBackwardLabel())
                .withPriority(FLOWRULE_PRIORITY)
                .withSelector(selector)
                .withTreatment(treatment)
                .build();

        tunnelFlows.add(entryFlow);



        // ------------

        if(pathLinks.size() != 1) {
            selector = DefaultTrafficSelector.builder()
                    .matchEthType(EthType.EtherType.MPLS_UNICAST.ethType().toShort())
                    .matchMplsLabel(mplsLabel(tunnel.getBackwardLabel()))
                    .build();

            //TODO - check "for"
            for (int i = pathLinks.size() - 2; i >= 0; i--) {

                ConnectPoint cp = pathLinks.get(i).dst();

                treatment = DefaultTrafficTreatment.builder()
                        .immediate()
                        .setOutput(cp.port())
                        .build();

                FlowRule intermediateFlow = DefaultFlowRule.builder()
                        .forDevice(cp.deviceId())
                        .forTable(0)
                        .fromApp(appId)
                        .makePermanent()
                        .withCookie(tunnel.getBackwardLabel())
                        .withPriority(FLOWRULE_PRIORITY)
                        .withSelector(selector)
                        .withTreatment(treatment)
                        .build();

                tunnelFlows.add(intermediateFlow);
            }
        }



        // ------------

        ConnectPoint leaveLastHop = tunnel.getSrcSite();

        selector = DefaultTrafficSelector.builder()
                .matchEthType(EthType.EtherType.MPLS_UNICAST.ethType().toShort())
                .matchMplsLabel(mplsLabel(tunnel.getBackwardLabel()))
                .build();

        treatment = DefaultTrafficTreatment.builder()
                .immediate()
                .popMpls(new EthType(tunnel.getNetworkProtocol()))
                .setOutput(leaveLastHop.port())
                .build();

        FlowRule leaveFlow = DefaultFlowRule.builder()
                .forDevice(leaveLastHop.deviceId())
                .forTable(0)
                .fromApp(appId)
                .makePermanent()
                .withCookie(tunnel.getBackwardLabel())
                .withPriority(FLOWRULE_PRIORITY)
                .withSelector(selector)
                .withTreatment(treatment)
                .build();

        tunnelFlows.add(leaveFlow);



        // ------------

        FlowRule [] flows = (FlowRule []) tunnelFlows.toArray();

        flowRuleService.applyFlowRules(flows);

        return true;
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
