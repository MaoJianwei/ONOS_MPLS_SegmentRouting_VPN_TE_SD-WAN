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
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Service;
import org.onosproject.mao.sdwan.api.SdwanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class SdwanManager implements SdwanService {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private Map vpnDB = new MultiValueMap();

    @Activate
    protected void activate() {


        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        vpnDB.clear();

        log.info("Stopped");
    }


    @Override
    public boolean createVPN(String tenantName, String srcSite, String dstSite) {



        return false;
    }
}
