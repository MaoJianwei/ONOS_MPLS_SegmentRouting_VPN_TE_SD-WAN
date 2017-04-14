package org.onosproject.mao.sdwan.api;

import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultPath;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Path;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by mao on 17-4-14.
 */
public class SdwanTunnel {

    public static final int INVALID_LABEL = -1;
    public static final int MIN_LABEL = 0;
    public static final int MAX_LABEL = 1048575;


    private String tenantName;
    private ConnectPoint srcSite;
    private ConnectPoint dstSite;
    private List<DeviceId> forwardDevices;
    private List<DeviceId> backwardDevices;
    private Path forwardPath;
    private Path backwardPath;
    private int forwardLabel; // MPLS now
    private int backwardLabel; // MPLS now



    SdwanTunnel(String tenantName, ConnectPoint srcSite, ConnectPoint dstSite,
                List<DeviceId> forwardDevices, List<DeviceId> backwardDevices,
                Path forwardPath, Path backwardPath,
                int forwardLabel, int backwardLabel){

        checkNotNull(tenantName, "Tenant name must be set");
        checkNotNull(srcSite, "Src site must be set");
        checkNotNull(dstSite, "Dst site must be set");
        checkArgument(forwardLabel != INVALID_LABEL,
                "Forward label must be set");
        checkArgument(forwardLabel >= MIN_LABEL && forwardLabel <= MAX_LABEL,
                "Forward label is out of scope %s ~ %s", MIN_LABEL, MAX_LABEL);
        checkArgument(backwardLabel != INVALID_LABEL,
                "Backward label must be set");
        checkArgument(backwardLabel >= MIN_LABEL && backwardLabel <= MAX_LABEL,
                "Backward label is out of scope %s ~ %s", MIN_LABEL, MAX_LABEL);

        this.tenantName = tenantName;
        this.srcSite = srcSite;
        this.dstSite = dstSite;
        this.forwardDevices = forwardDevices == null ? new ArrayList<>() : forwardDevices;
        this.backwardDevices = backwardDevices == null ? new ArrayList<>() : backwardDevices;
        this.forwardPath = forwardPath;
        this.backwardPath = backwardPath;
        this.forwardLabel = forwardLabel;
        this.backwardLabel = backwardLabel;
    }

    

    public String getTenantName() {
        return tenantName;
    }

    public ConnectPoint getSrcSite() {
        return srcSite;
    }

    public ConnectPoint getDstSite() {
        return dstSite;
    }

    public List<DeviceId> getForwardDevices() {
        return forwardDevices;
    }

    public List<DeviceId> getBackwardDevices() {
        return backwardDevices;
    }

    public Path getForwardPath() {
        return forwardPath;
    }

    public Path getBackwardPath() {
        return backwardPath;
    }

    public int getForwardLabel() {
        return forwardLabel;
    }

    public int getBackwardLabel() {
        return backwardLabel;
    }



    public void updateForwardDevices(List<DeviceId> devices) {
        checkNotNull(devices, "devices cannot be null");
        this.forwardDevices = devices;
    }

    public void updateBackwardDevices(List<DeviceId> devices) {
        checkNotNull(devices, "devices cannot be null");
        this.backwardDevices = devices;
    }

    public void updateForwardPath(Path path) {
        checkNotNull(path, "path cannot be null");
        this.forwardPath = path;
    }

    public void updateBackwardPath(Path path) {
        checkNotNull(path, "path cannot be null");
        this.backwardPath = path;
    }

    public void updateForwardLabel(int label) {
        checkArgument(label >= MIN_LABEL && label <= MAX_LABEL,
                "label is out of scope %s ~ %s", MIN_LABEL, MAX_LABEL);
        this.forwardLabel = label;
    }

    public void updateBackwardLabel(int label) {
        checkArgument(label >= MIN_LABEL && label <= MAX_LABEL,
                "label is out of scope %s ~ %s", MIN_LABEL, MAX_LABEL);
        this.backwardLabel = label;
    }



    public static Builder builder() { return new Builder(); }

    public static final class Builder {

        private String tenantName;
        private ConnectPoint srcSite;
        private ConnectPoint dstSite;
        private List<DeviceId> forwardDevices;
        private List<DeviceId> backwardDevices;
        private Path forwardPath;
        private Path backwardPath;
        private int forwardLabel; // MPLS now
        private int backwardLabel; // MPLS now

        Builder(){
            forwardLabel = INVALID_LABEL;
            backwardLabel = INVALID_LABEL;
        }

        public Builder tenant(String name) {
            this.tenantName = name;
            return this;
        }

        public Builder src(ConnectPoint srcSite) {
            this.srcSite = srcSite;
            return this;
        }

        public Builder dst(ConnectPoint dstSite) {
            this.dstSite = dstSite;
            return this;
        }

        //MPLS now
        public Builder forwardLabel(int label) {
            this.forwardLabel = label;
            return this;
        }

        //MPLS now
        public Builder backwardLabel(int label) {
            this.backwardLabel = label;
            return this;
        }

        //optional
        public Builder forwardDevices(List<DeviceId> devices) {
            this.forwardDevices = devices;
            return this;
        }

        //optional
        public Builder backwardDevices(List<DeviceId> devices) {
            this.backwardDevices = devices;
            return this;
        }

        //optional
        public Builder forwardPath(Path path) {
            this.forwardPath = path;
            return this;
        }

        //optional
        public Builder backwardPath(Path path) {
            this.backwardPath = path;
            return this;
        }

        public SdwanTunnel build() {
            return new SdwanTunnel(tenantName, srcSite, dstSite,
                    forwardDevices, backwardDevices, forwardPath, backwardPath,
                    forwardLabel, backwardLabel);
        }
    }
}
