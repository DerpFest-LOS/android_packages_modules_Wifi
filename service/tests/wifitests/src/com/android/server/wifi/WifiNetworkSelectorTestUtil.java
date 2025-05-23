/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi;

import static com.android.server.wifi.WifiConfigurationTestUtil.SECURITY_PSK;
import static com.android.server.wifi.WifiConfigurationTestUtil.SECURITY_SAE;
import static com.android.server.wifi.WifiConfigurationTestUtil.SECURITY_WAPI_PSK;
import static com.android.server.wifi.WifiConfigurationTestUtil.generateWifiConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiSsid;
import android.net.wifi.util.ScanResultUtil;
import android.text.TextUtils;

import com.android.server.wifi.hotspot2.NetworkDetail;
import com.android.server.wifi.util.InformationElementUtil;
import com.android.server.wifi.util.NativeUtil;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper for WifiNetworkSelector unit tests.
 */
public class WifiNetworkSelectorTestUtil {
    private static final String TAG = "WifiNetworkSelectorTestUtil";

    /**
     * A class that holds a list of scanDetail and their associated WifiConfiguration.
     */
    public static class ScanDetailsAndWifiConfigs {
        List<ScanDetail> mScanDetails;
        WifiConfiguration[] mWifiConfigs;

        ScanDetailsAndWifiConfigs(List<ScanDetail> scanDetails, WifiConfiguration[] configs) {
            mScanDetails = scanDetails;
            mWifiConfigs = configs;
        }

        List<ScanDetail> getScanDetails() {
            return mScanDetails;
        }

        WifiConfiguration[] getWifiConfigs() {
            return mWifiConfigs;
        }
    }

    /**
     * Build a list of ScanDetail based on the caller supplied network SSID, BSSID,
     * frequency, capability and RSSI level information. Create the corresponding
     * WifiConfiguration for these networks and set up the mocked WifiConfigManager.
     *
     * @param ssids an array of SSIDs
     * @param bssids an array of BSSIDs
     * @param freqs an array of the network's frequency
     * @param caps an array of the network's capability
     * @param levels an array of the network's RSSI levels
     * @param securities an array of the network's security setting
     * @param wifiConfigManager the mocked WifiConfigManager
     * @return the constructed ScanDetail list and WifiConfiguration array
     */
    public static ScanDetailsAndWifiConfigs setupScanDetailsAndConfigStore(String[] ssids,
                String[] bssids, int[] freqs, String[] caps, int[] levels, int[] securities,
                WifiConfigManager wifiConfigManager, Clock clock) {
        List<ScanDetail> scanDetails = buildScanDetails(ssids, bssids, freqs, caps, levels, clock);

        WifiConfiguration[] savedConfigs = generateWifiConfigurations(ssids, securities);

        addWifiConfigAndLinkScanResult(wifiConfigManager, savedConfigs, scanDetails);

        return new ScanDetailsAndWifiConfigs(scanDetails, savedConfigs);
    }

    public static ScanDetailsAndWifiConfigs setupScanDetailsAndConfigStore(String[] ssids,
                String[] bssids, int[] freqs, String[] caps, int[] levels,
                int[] securities, WifiConfigManager wifiConfigManager, Clock clock,
                byte[][] iesByteStream) {

        if (iesByteStream == null) {
            throw new IllegalArgumentException("Null ies");
        }

        List<ScanDetail> scanDetails = buildScanDetailsWithNetworkDetails(ssids, bssids, freqs,
                caps, levels, iesByteStream, clock);

        WifiConfiguration[] savedConfigs = generateWifiConfigurations(ssids, securities);

        addWifiConfigAndLinkScanResult(wifiConfigManager, savedConfigs, scanDetails);

        return new ScanDetailsAndWifiConfigs(scanDetails, savedConfigs);
    }

    /**
     * Build a list of ScanDetail based on the caller supplied network SSID, BSSID,
     * frequency and RSSI level information. Create the EAP-SIM authticated
     * WifiConfiguration for these networks and set up the mocked WifiConfigManager.
     *
     * @param ssids an array of SSIDs
     * @param bssids an array of BSSIDs
     * @param freqs an array of the network's frequency
     * @param levels an array of the network's RSSI levels
     * @param wifiConfigManager the mocked WifiConfigManager
     * @return the constructed ScanDetail list and WifiConfiguration array
     */
    public static ScanDetailsAndWifiConfigs setupScanDetailsAndConfigForEapSimNetwork(
            String[] ssids,
            String[] bssids, int[] freqs, int[] levels,
            WifiConfigManager wifiConfigManager, Clock clock) {
        assertNotNull(ssids);
        String[] caps = new String[ssids.length];
        for (int i = 0; i < ssids.length; i++) {
            caps[i] = "[EAP/SHA1][ESS]";
        }
        List<ScanDetail> scanDetails = buildScanDetails(ssids, bssids, freqs, caps, levels, clock);
        WifiConfiguration[] savedConfigs = new WifiConfiguration[ssids.length];
        Set<String> ssidSet = new HashSet<>();
        for (int i = 0; i < ssids.length; i++) {
            // do not allow duplicated ssid
            assertFalse(ssidSet.contains(ssids[i]));
            ssidSet.add(ssids[i]);
            savedConfigs[i] = WifiConfigurationTestUtil.createEapNetwork(
                    WifiEnterpriseConfig.Eap.SIM, WifiEnterpriseConfig.Phase2.NONE);
            savedConfigs[i].SSID = ssids[i];
            savedConfigs[i].networkId = i;
        }

        addWifiConfigAndLinkScanResult(wifiConfigManager, savedConfigs, scanDetails);

        return new ScanDetailsAndWifiConfigs(scanDetails, savedConfigs);
    }

    private static void addWifiConfigAndLinkScanResult(WifiConfigManager wifiConfigManager,
            WifiConfiguration[] configs, List<ScanDetail> scanDetails) {
        checkConsistencyOfScanDetailsAndWifiConfigs(scanDetails, configs);
        prepareConfigStore(wifiConfigManager, configs);
        scanResultLinkConfiguration(wifiConfigManager, configs, scanDetails);
    }

    private static void checkConsistencyOfScanDetailsAndWifiConfigs(
            List<ScanDetail> scanDetails,
            WifiConfiguration[] savedConfigs) {
        assertEquals(scanDetails.size(), savedConfigs.length);
        for (int i = 0; i < savedConfigs.length; i++) {
            ScanResult scanResult = scanDetails.get(i).getScanResult();
            WifiConfiguration config = savedConfigs[i];
            // Can check this only for configs with a single security type
            if (config.getSecurityParamsList().size() < 2) {
                assertEquals("Problem in entry " + i,
                        ScanResultMatchInfo.fromScanResult(scanResult),
                        ScanResultMatchInfo.fromWifiConfiguration(config));
            }
        }
    }

    /**
     * Verify whether the WifiConfiguration chosen by WifiNetworkSelector matches
     * with the chosen scan result.
     *
     * @param chosenScanResult the chosen scan result
     * @param chosenCandidate  the chosen configuration
     */
    public static void verifySelectedScanResult(WifiConfigManager wifiConfigManager,
            ScanResult chosenScanResult, WifiConfiguration chosenCandidate) {
        verify(wifiConfigManager, atLeastOnce()).setNetworkCandidateScanResult(
                eq(chosenCandidate.networkId), eq(chosenScanResult), anyInt(),
                eq(chosenCandidate.getSecurityParamsList().get(0)));
    }


    /**
     * Build a list of scanDetails based on the caller supplied network SSID, BSSID,
     * frequency, capability and RSSI level information.
     *
     * @param ssids an array of SSIDs
     * @param bssids an array of BSSIDs
     * @param freqs an array of the network's frequency
     * @param caps an array of the network's capability
     * @param levels an array of the network's RSSI levels
     * @return the constructed list of ScanDetail
     */
    public static List<ScanDetail> buildScanDetails(String[] ssids, String[] bssids, int[] freqs,
                                            String[] caps, int[] levels, Clock clock) {
        List<ScanDetail> scanDetailList = new ArrayList<ScanDetail>();

        long timeStamp = clock.getElapsedSinceBootMillis();
        for (int index = 0; index < ssids.length; index++) {
            byte[] ssid = NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(ssids[index]));
            ScanDetail scanDetail = new ScanDetail(WifiSsid.fromBytes(ssid),
                    bssids[index], caps[index], levels[index], freqs[index], timeStamp, 0);
            scanDetailList.add(scanDetail);
        }
        return scanDetailList;
    }

    /**
     * Build a list of scanDetails along with network details based
     * on the caller supplied network SSID, BSSID, frequency,
     * capability, byte stream of IEs and RSSI level information.
     *
     * @param ssids an array of SSIDs
     * @param bssids an array of BSSIDs
     * @param freqs an array of the network's frequency
     * @param caps an array of the network's capability
     * @param levels an array of the network's RSSI levels
     * @return the constructed list of ScanDetail
     */
    public static List<ScanDetail> buildScanDetailsWithNetworkDetails(String[] ssids,
                String[] bssids, int[] freqs,
                String[] caps, int[] levels, byte[][] iesByteStream, Clock clock) {
        List<ScanDetail> scanDetailList = new ArrayList<ScanDetail>();

        long timeStamp = clock.getElapsedSinceBootMillis();
        for (int index = 0; index < ssids.length; index++) {
            byte[] ssid = NativeUtil.byteArrayFromArrayList(NativeUtil.decodeSsid(ssids[index]));
            ScanResult.InformationElement[] ies =
                InformationElementUtil.parseInformationElements(iesByteStream[index]);
            NetworkDetail nd = new NetworkDetail(bssids[index], ies, new ArrayList<String>(),
                    freqs[index]);
            ScanDetail scanDetail = new ScanDetail(nd, WifiSsid.fromBytes(ssid),
                    bssids[index], caps[index], levels[index], freqs[index], timeStamp,
                    ies, new ArrayList<String>(),
                    ScanResults.generateIERawDatafromScanResultIE(ies));
            scanDetailList.add(scanDetail);
        }
        return scanDetailList;
    }

    /**
     * Generate an array of {@link android.net.wifi.WifiConfiguration} based on the caller
     * supplied network SSID and security information.
     *
     * @param ssids an array of SSIDs
     * @param securities an array of the network's security setting
     * @return the constructed array of {@link android.net.wifi.WifiConfiguration}
     */
    public static WifiConfiguration[] generateWifiConfigurations(String[] ssids,
                int[] securities) {
        if (ssids == null || securities == null || ssids.length != securities.length) {
            throw new IllegalArgumentException();
        }

        BitSet supportedFeaturesAll = new BitSet();
        supportedFeaturesAll.set(0, 63); // mark all features as supported

        Map<String, Integer> netIdMap = new HashMap<>();
        int netId = 0;

        WifiConfiguration[] configs = new WifiConfiguration[ssids.length];
        for (int index = 0; index < ssids.length; index++) {
            String configKey = ssids[index] + securities[index];
            Integer id = netIdMap.get(configKey);
            if (id == null) {
                id = netId;
                netIdMap.put(configKey, id);
                netId++;
            }

            configs[index] = generateWifiConfig(id, 0, ssids[index], false, true, null,
                    null, securities[index]);
            if ((securities[index] & SECURITY_PSK) != 0 || (securities[index] & SECURITY_SAE) != 0
                    || (securities[index] & SECURITY_WAPI_PSK) != 0) {
                configs[index].preSharedKey = "\"PA55W0RD\""; // needed to validate with PSK
            }
            if (!WifiConfigurationUtil.validate(configs[index], supportedFeaturesAll, true)) {
                throw new IllegalArgumentException("Invalid generated config: " + configs[index]);
            }
        }

        return configs;
    }

    /**
     * Add the Configurations to WifiConfigManager (WifiConfigureStore can take them out according
     * to the networkd ID) and setup the WifiConfigManager mocks for these networks.
     * This simulates the WifiConfigManager class behaviour.
     *
     * @param wifiConfigManager the mocked WifiConfigManager
     * @param configs input configuration need to be added to WifiConfigureStore
     */
    public static void prepareConfigStore(final WifiConfigManager wifiConfigManager,
                final WifiConfiguration[] configs) {
        when(wifiConfigManager.getSavedNetworkForScanDetail(any(ScanDetail.class)))
                .then(new AnswerWithArguments() {
                    public WifiConfiguration answer(ScanDetail scanDetail) {
                        for (WifiConfiguration config : configs) {
                            if (TextUtils.equals(config.SSID,
                                    ScanResultUtil.createQuotedSsid(scanDetail.getSSID()))) {
                                return config;
                            }
                        }
                        return null;
                    }
                });
        when(wifiConfigManager.getConfiguredNetwork(anyInt()))
                .then(new AnswerWithArguments() {
                    public WifiConfiguration answer(int netId) {
                        for (WifiConfiguration config : configs) {
                            if (netId == config.networkId) {
                                return new WifiConfiguration(config);
                            }
                        }
                        return null;
                    }
                });
        when(wifiConfigManager.getConfiguredNetworkWithPassword(anyInt()))
                .then(new AnswerWithArguments() {
                    public WifiConfiguration answer(int netId) {
                        for (WifiConfiguration config : configs) {
                            if (netId == config.networkId) {
                                return new WifiConfiguration(config);
                            }
                        }
                        return null;
                    }
                });
        when(wifiConfigManager.getConfiguredNetwork(anyString()))
                .then(new AnswerWithArguments() {
                    public WifiConfiguration answer(String configKey) {
                        for (WifiConfiguration config : configs) {
                            if (TextUtils.equals(config.getProfileKey(), configKey)) {
                                return new WifiConfiguration(config);
                            }
                        }
                        return null;
                    }
                });
        when(wifiConfigManager.getConfiguredNetworks())
                .then(new AnswerWithArguments() {
                    public List<WifiConfiguration> answer() {
                        List<WifiConfiguration> savedNetworks = new ArrayList<>();
                        for (int netId = 0; netId < configs.length; netId++) {
                            savedNetworks.add(new WifiConfiguration(configs[netId]));
                        }
                        return savedNetworks;
                    }
                });
        when(wifiConfigManager.clearNetworkCandidateScanResult(anyInt()))
                .then(new AnswerWithArguments() {
                    public boolean answer(int netId) {
                        if (netId >= 0 && netId < configs.length) {
                            configs[netId].getNetworkSelectionStatus().setCandidate(null);
                            configs[netId].getNetworkSelectionStatus()
                                    .setCandidateScore(Integer.MIN_VALUE);
                            configs[netId].getNetworkSelectionStatus()
                                .setCandidateSecurityParams(null);
                            configs[netId].getNetworkSelectionStatus()
                                    .setSeenInLastQualifiedNetworkSelection(false);
                            return true;
                        } else {
                            return false;
                        }
                    }
                });
        when(wifiConfigManager.setNetworkCandidateScanResult(
                anyInt(), any(ScanResult.class), anyInt(), any()))
                .then(new AnswerWithArguments() {
                    public boolean answer(int netId, ScanResult scanResult, int score,
                            SecurityParams params) {
                        if (netId >= 0 && netId < configs.length) {
                            configs[netId].getNetworkSelectionStatus().setCandidate(scanResult);
                            configs[netId].getNetworkSelectionStatus().setCandidateScore(score);
                            configs[netId].getNetworkSelectionStatus()
                                    .setCandidateSecurityParams(params);
                            configs[netId].getNetworkSelectionStatus()
                                    .setSeenInLastQualifiedNetworkSelection(true);
                            return true;
                        } else {
                            return false;
                        }
                    }
                });
    }


    /**
     * Link scan results to the saved configurations.
     *
     * The shorter of the 2 input params will be used to loop over so the inputs don't
     * need to be of equal length. If there are more scan details then configs the remaining scan
     * details will be associated with a NULL config.
     *
     * @param wifiConfigManager the mocked WifiConfigManager
     * @param configs     saved configurations
     * @param scanDetails come in scan results
     */
    private static void scanResultLinkConfiguration(WifiConfigManager wifiConfigManager,
                WifiConfiguration[] configs, List<ScanDetail> scanDetails) {
        if (configs == null || scanDetails == null) {
            return;
        }

        if (scanDetails.size() <= configs.length) {
            for (int i = 0; i < scanDetails.size(); i++) {
                ScanDetail scanDetail = scanDetails.get(i);
                when(wifiConfigManager.getSavedNetworkForScanDetailAndCache(eq(scanDetail)))
                        .thenReturn(configs[i]);
            }
        } else {
            for (int i = 0; i < configs.length; i++) {
                ScanDetail scanDetail = scanDetails.get(i);
                when(wifiConfigManager.getSavedNetworkForScanDetailAndCache(eq(scanDetail)))
                        .thenReturn(configs[i]);
            }

            // associated the remaining scan details with a NULL config.
            for (int i = configs.length; i < scanDetails.size(); i++) {
                when(wifiConfigManager.getSavedNetworkForScanDetailAndCache(
                        eq(scanDetails.get(i)))).thenReturn(null);
            }
        }
    }

    /**
     * Setup WifiConfigManager mock for ephemeral networks.
     *
     * @param wifiConfigManager WifiConfigManager mock
     * @param networkId         ID of the ephemeral network
     * @param scanDetail        scanDetail of the ephemeral network
     * @param meteredHint       flag to indidate if the network has meteredHint
     */
    public static WifiConfiguration setupEphemeralNetwork(WifiConfigManager wifiConfigManager,
            int networkId, ScanDetail scanDetail, boolean meteredHint) {
        // Return the correct networkID for ephemeral network addition.
        when(wifiConfigManager.addOrUpdateNetwork(any(WifiConfiguration.class), anyInt()))
                .thenReturn(new NetworkUpdateResult(networkId));
        final WifiConfiguration config =
                ScanResultUtil.createNetworkFromScanResult(scanDetail.getScanResult());
        config.ephemeral = true;
        config.trusted = false;
        config.networkId = networkId;
        config.meteredHint = meteredHint;

        when(wifiConfigManager.getSavedNetworkForScanDetailAndCache(eq(scanDetail)))
                .thenReturn(new WifiConfiguration(config));
        when(wifiConfigManager.getConfiguredNetwork(eq(networkId)))
                .then(new AnswerWithArguments() {
                    public WifiConfiguration answer(int netId) {
                        return new WifiConfiguration(config);
                    }
                });
        when(wifiConfigManager.setNetworkCandidateScanResult(
                eq(networkId), any(ScanResult.class), anyInt(), any()))
                .then(new AnswerWithArguments() {
                    public boolean answer(int netId, ScanResult scanResult, int score,
                            SecurityParams params) {
                        config.getNetworkSelectionStatus().setCandidate(scanResult);
                        config.getNetworkSelectionStatus().setCandidateScore(score);
                        config.getNetworkSelectionStatus()
                                .setCandidateSecurityParams(params);
                        config.getNetworkSelectionStatus()
                                .setSeenInLastQualifiedNetworkSelection(true);
                        return true;
                    }
                });
        when(wifiConfigManager.updateNetworkSelectionStatus(eq(networkId),
                eq(WifiConfiguration.NetworkSelectionStatus.DISABLED_NONE)))
                .then(new AnswerWithArguments() {
                    public boolean answer(int netId, int status) {
                        config.getNetworkSelectionStatus().setNetworkSelectionStatus(
                                WifiConfiguration.NetworkSelectionStatus.DISABLED_NONE);
                        return true;
                    }
                });
        return config;
    }
}
