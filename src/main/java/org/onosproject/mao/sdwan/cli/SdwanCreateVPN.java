package org.onosproject.mao.sdwan.cli;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onlab.packet.EthType;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.mao.sdwan.api.SdwanService;

import static org.onosproject.mao.sdwan.api.SdwanTunnel.INVALID_NETWORK_PROTOCOL;

/**
 * Created by mao on 17-4-17.
 */
@Command(scope = "onos",
        name = "sdwan-create-vpn",
        description = "Create MPLS/SR VPN tunnel",
        detailedDescription = "Create MPLS/SR VPN tunnel")
public class SdwanCreateVPN extends AbstractShellCommand {

    private static final String ARP = "arp";
    private static final String IPV4 = "ipv4";
    private static final String IPV6 = "ipv6";
    private static final String MPLS = "mpls";


    @Argument(index = 0, name = "TenantName",
            description = "The name of tenant",
            required = true, multiValued = false)
    String tenantName = null;

    @Argument(index = 1, name = "Source-Site",
            description = "The source site of VPN",
            required = true, multiValued = false)
    String srcSite = null;

    @Argument(index = 2, name = "Destination-Site",
            description = "The destination site of VPN",
            required = true, multiValued = false)
    String dstSite = null;

    /**
     * The type of Network protocol.
     *
     * e.g. Known protocol: arp, ipv4, ipv6, mpls, etc.
     * e.g. Novel protocol: 0x0800, 0x88cc, etc.
     */
    @Argument(index = 3, name = "Network-Protocol-type",
            description = "The type of Network protocol",
            required = true, multiValued = false)
    String networkProtocol = null;

    @Override
    protected void execute() {

        SdwanService sdwanService = getService(SdwanService.class);

        int etherType;
        switch (networkProtocol.trim().toLowerCase()) {
            case ARP:
                etherType = EthType.EtherType.ARP.ethType().toShort();
                break;
            case IPV4:
                etherType = EthType.EtherType.IPV4.ethType().toShort();
                break;
            case IPV6:
                etherType = EthType.EtherType.IPV6.ethType().toShort();
                break;
            case MPLS:
                etherType = EthType.EtherType.MPLS_UNICAST.ethType().toShort();
                break;
            default:
                etherType = Integer.valueOf(networkProtocol);
                print("Novel protocol, etherType: [{}], tenant: {}", etherType, tenantName);
        }

        boolean result = sdwanService.createVPN(tenantName, srcSite, dstSite, etherType);

        print("Create VPN {}! you can check logs", result ? "OK" : "Fail");
    }
}
