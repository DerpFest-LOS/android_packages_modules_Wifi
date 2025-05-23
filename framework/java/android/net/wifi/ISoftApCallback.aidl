/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.wifi;

import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApInfo;
import android.net.wifi.SoftApState;
import android.net.wifi.WifiClient;

/**
 * Interface for Soft AP callback.
 *
 * @hide
 */
oneway interface ISoftApCallback {
    /**
     * Service to manager callback providing current soft AP state. The possible
     * parameter values listed are defined in WifiManager.java
     *
     * @param new SoftApState
     */
    void onStateChanged(in SoftApState state);

    /**
     * Service to manager callback providing informations of softap.
     *
     * @param infos The currently {@link SoftApInfo} in each AP instance.
     * @param clients The currently connected clients in each AP instance.
     * @param isBridged whether or not the current AP enabled on bridged mode.
     * @param isRegistration whether or not the callbackk was triggered when register.
     */
    void onConnectedClientsOrInfoChanged(in Map<String, SoftApInfo> infos,
            in Map<String, List<WifiClient>> clients, boolean isBridged, boolean isRegistration);

    /**
     * Service to manager callback providing capability of softap.
     *
     * @param capability is the softap capability. {@link SoftApCapability}
     */
    void onCapabilityChanged(in SoftApCapability capability);

    /**
     * Service to manager callback providing blocked client of softap with specific reason code.
     *
     * @param client the currently blocked client.
     * @param blockedReason one of blocked reason from {@link WifiManager.SapClientBlockedReason}
     */
    void onBlockedClientConnecting(in WifiClient client, int blockedReason);

    /**
     * Service to manager callback providing clients that disconnected from the softap.
     *
     * @param info information about the AP instance
     * @param clients the disconnected clients of the AP instance
     */
    void onClientsDisconnected(in SoftApInfo info, in List<WifiClient> clients);
}
