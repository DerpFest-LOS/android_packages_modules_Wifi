/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.server.wifi.util.InformationElementUtil.BssLoad.CHANNEL_UTILIZATION_SCALE;

import android.content.Context;
import android.os.Handler;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.util.ArraySet;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wifi.flags.FeatureFlags;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * This class allows getting all configurable flags from DeviceConfig.
 */
public class DeviceConfigFacade {
    private final Context mContext;
    private final WifiMetrics mWifiMetrics;

    private static final String NAMESPACE = "wifi";
    private final FeatureFlags mFeatureFlags;

    // Default values of fields
    @VisibleForTesting
    protected static final int DEFAULT_ABNORMAL_CONNECTION_DURATION_MS =
            (int) TimeUnit.SECONDS.toMillis(30);
    // Default duration for evaluating Wifi condition to trigger a data stall
    // measured in milliseconds
    public static final int DEFAULT_DATA_STALL_DURATION_MS = 1500;
    // Default threshold of Tx throughput below which to trigger a data stall measured in Kbps
    public static final int DEFAULT_DATA_STALL_TX_TPUT_THR_KBPS = 2000;
    // Default threshold of Rx throughput below which to trigger a data stall measured in Kbps
    public static final int DEFAULT_DATA_STALL_RX_TPUT_THR_KBPS = 2000;
    // Default threshold of Tx packet error rate above which to trigger a data stall in percentage
    public static final int DEFAULT_DATA_STALL_TX_PER_THR = 90;
    // Default threshold of CCA level above which to trigger a data stall
    public static final int DEFAULT_DATA_STALL_CCA_LEVEL_THR = CHANNEL_UTILIZATION_SCALE;
    // Default low threshold of L2 sufficient Tx throughput in Kbps
    public static final int DEFAULT_TX_TPUT_SUFFICIENT_THR_LOW_KBPS = 2000;
    // Default high threshold of L2 sufficient Tx throughput in Kbps
    public static final int DEFAULT_TX_TPUT_SUFFICIENT_THR_HIGH_KBPS = 8000;
    // Default low threshold of L2 sufficient Rx throughput in Kbps
    public static final int DEFAULT_RX_TPUT_SUFFICIENT_THR_LOW_KBPS = 2000;
    // Default high threshold of L2 sufficient Rx throughput in Kbps
    public static final int DEFAULT_RX_TPUT_SUFFICIENT_THR_HIGH_KBPS = 8000;
    // Numerator part of default threshold of L2 throughput over L3 throughput ratio
    public static final int DEFAULT_TPUT_SUFFICIENT_RATIO_THR_NUM = 2;
    // Denominator part of default threshold of L2 throughput over L3 throughput ratio
    public static final int DEFAULT_TPUT_SUFFICIENT_RATIO_THR_DEN = 1;
    // Default threshold of Tx packet per second
    public static final int DEFAULT_TX_PACKET_PER_SECOND_THR = 2;
    // Default threshold of Rx packet per second
    public static final int DEFAULT_RX_PACKET_PER_SECOND_THR = 2;
    // Default high threshold values for various connection/disconnection cases
    // All of them are in percent with respect to connection attempts
    static final int DEFAULT_CONNECTION_FAILURE_HIGH_THR_PERCENT = 40;
    static final int DEFAULT_CONNECTION_FAILURE_DISCONNECTION_HIGH_THR_PERCENT = 30;
    static final int DEFAULT_ASSOC_REJECTION_HIGH_THR_PERCENT = 30;
    static final int DEFAULT_ASSOC_TIMEOUT_HIGH_THR_PERCENT = 30;
    static final int DEFAULT_AUTH_FAILURE_HIGH_THR_PERCENT = 30;
    static final int DEFAULT_SHORT_CONNECTION_NONLOCAL_HIGH_THR_PERCENT = 20;
    static final int DEFAULT_DISCONNECTION_NONLOCAL_HIGH_THR_PERCENT = 25;
    // Default health monitor abnormal count minimum for various cases
    static final int DEFAULT_CONNECTION_FAILURE_COUNT_MIN = 6;
    static final int DEFAULT_CONNECTION_FAILURE_DISCONNECTION_COUNT_MIN = 5;
    static final int DEFAULT_ASSOC_REJECTION_COUNT_MIN  = 3;
    static final int DEFAULT_ASSOC_TIMEOUT_COUNT_MIN  = 3;
    static final int DEFAULT_AUTH_FAILURE_COUNT_MIN  = 3;
    static final int DEFAULT_SHORT_CONNECTION_NONLOCAL_COUNT_MIN  = 3;
    static final int DEFAULT_DISCONNECTION_NONLOCAL_COUNT_MIN  = 3;
    // Numerator part of default ratio threshold values for all cases
    static final int DEFAULT_HEALTH_MONITOR_RATIO_THR_NUMERATOR = 4;
    // Denominator part of ratio threshold for all cases
    static final int HEALTH_MONITOR_RATIO_THR_DENOMINATOR = 2;
    // Minimum RSSI in dBm for connection stats collection
    // Connection or disconnection events with RSSI below this threshold are not
    // included in connection stats collection.
    static final int DEFAULT_HEALTH_MONITOR_MIN_RSSI_THR_DBM = -68;
    // Default minimum number of connection attempts to qualify daily detection
    static final int DEFAULT_HEALTH_MONITOR_MIN_NUM_CONNECTION_ATTEMPT = 10;
    // Default minimum wait time between two bug report captures
    static final int DEFAULT_BUG_REPORT_MIN_WINDOW_MS = 3_600_000;
    // Default report-high threshold to take-bug-report threshold ratio.
    // It should be larger than 1 since the bar to take bugreport should be higher.
    static final int DEFAULT_BUG_REPORT_THRESHOLD_EXTRA_RATIO = 2;
    // Default overlapping connection duration threshold in ms to trigger bug report
    static final int DEFAULT_OVERLAPPING_CONNECTION_DURATION_THRESHOLD_MS = 75_000;
    // At low traffic, Tx link speed values below the following threshold
    // are ignored because it could be due to low rate management frames
    static final int DEFAULT_TX_LINK_SPEED_LOW_THRESHOLD_MBPS = 9;
    // At low traffic, Rx link speed values below the following threshold
    // are ignored because it could be due to low rate management frames
    static final int DEFAULT_RX_LINK_SPEED_LOW_THRESHOLD_MBPS = 9;
    // Default health monitor short connection duration threshold in ms
    static final int DEFAULT_HEALTH_MONITOR_SHORT_CONNECTION_DURATION_THR_MS = 20_000;

    // Default mask for abnormal disconnection reason codes.
    // Each bit of mask corresponds to a reason code defined in 802.11 standard section 9.4.1.7
    // For example, b0 for reason code 0, b1 for reason code 1, etc.
    // Bits below are abnormal disconnection reasons and thus are set to 1
    // b0: reserved (e.g., STA heartbeat failure)
    // b2: invalid auth
    // b4: disassociated due to inactivity
    // b6 and b7: invalid class 2 and 3 frames
    // b34: disassociated due to missing ACKs
    static final long DEFAULT_ABNORMAL_DISCONNECTION_REASON_CODE_MASK = 0x4_0000_00d5L;
    // Default maximum interval between last RSSI poll and disconnection
    static final int DEFAULT_HEALTH_MONITOR_RSSI_POLL_VALID_TIME_MS = 2_100;
    // Default maximum interval between scan and connection attempt in non-stationary state
    static final int DEFAULT_NONSTATIONARY_SCAN_RSSI_VALID_TIME_MS = 5_000;
    // Default maximum interval between scan and connection attempt in stationary state
    static final int DEFAULT_STATIONARY_SCAN_RSSI_VALID_TIME_MS = 8_000;
    // Default health monitor firmware alert valid time.
    // -1 disables firmware alert time check
    static final int DEFAULT_HEALTH_MONITOR_FW_ALERT_VALID_TIME_MS = -1;
    // Default minimum confirmation duration for sending network score to connectivity service
    // when score breaches low. The actual confirmation duration is longer in general and it
    // depends on the score evaluation period normally controlled by
    // 'com.android.wifi.resources.R' config_wifiPollRssiIntervalMilliseconds.
    static final int DEFAULT_MIN_CONFIRMATION_DURATION_SEND_LOW_SCORE_MS = 5000;
    // Default minimum confirmation duration for sending network score to connectivity service
    // when score breaches high. The actual confirmation duration is longer in general and it
    // depends on the score evaluation period normally controlled by
    // 'com.android.wifi.resources.R' config_wifiPollRssiIntervalMilliseconds.
    static final int DEFAULT_MIN_CONFIRMATION_DURATION_SEND_HIGH_SCORE_MS = 0;
    // Default RSSI threshold in dBm above which low score is not sent to connectivity service
    // when external scorer takes action.
    static final int DEFAULT_RSSI_THRESHOLD_NOT_SEND_LOW_SCORE_TO_CS_DBM = -67;
    // Maximum traffic stats threshold for link bandwidth estimator
    static final int DEFAULT_TRAFFIC_STATS_THRESHOLD_MAX_KB = 8000;
    static final int DEFAULT_BANDWIDTH_ESTIMATOR_TIME_CONSTANT_LARGE_SEC = 6;
    static final String DEFAULT_DRY_RUN_SCORER_PKG_NAME = "";
    // Cached values of fields updated via updateDeviceConfigFlags()
    private boolean mIsAbnormalConnectionBugreportEnabled;
    private int mAbnormalConnectionDurationMs;
    private int mDataStallDurationMs;
    private int mDataStallTxTputThrKbps;
    private int mDataStallRxTputThrKbps;
    private int mDataStallTxPerThr;
    private int mDataStallCcaLevelThr;
    private int mTxTputSufficientLowThrKbps;
    private int mTxTputSufficientHighThrKbps;
    private int mRxTputSufficientLowThrKbps;
    private int mRxTputSufficientHighThrKbps;
    private int mTputSufficientRatioThrNum;
    private int mTputSufficientRatioThrDen;
    private int mTxPktPerSecondThr;
    private int mRxPktPerSecondThr;
    private int mConnectionFailureHighThrPercent;
    private int mConnectionFailureCountMin;
    private int mConnectionFailureDisconnectionHighThrPercent;
    private int mConnectionFailureDisconnectionCountMin;
    private int mAssocRejectionHighThrPercent;
    private int mAssocRejectionCountMin;
    private int mAssocTimeoutHighThrPercent;
    private int mAssocTimeoutCountMin;
    private int mAuthFailureHighThrPercent;
    private int mAuthFailureCountMin;
    private int mShortConnectionNonlocalHighThrPercent;
    private int mShortConnectionNonlocalCountMin;
    private int mDisconnectionNonlocalHighThrPercent;
    private int mDisconnectionNonlocalCountMin;
    private int mHealthMonitorRatioThrNumerator;
    private int mHealthMonitorMinRssiThrDbm;
    private Set<String> mRandomizationFlakySsidHotlist;
    private Set<String> mNonPersistentMacRandomizationSsidAllowlist;
    private Set<String> mNonPersistentMacRandomizationSsidBlocklist;
    private boolean mIsAbnormalConnectionFailureBugreportEnabled;
    private boolean mIsAbnormalDisconnectionBugreportEnabled;
    private int mHealthMonitorMinNumConnectionAttempt;
    private int mBugReportMinWindowMs;
    private int mBugReportThresholdExtraRatio;
    private boolean mWifiBatterySaverEnabled;
    private boolean mIsOverlappingConnectionBugreportEnabled;
    private int mOverlappingConnectionDurationThresholdMs;
    private int mTxLinkSpeedLowThresholdMbps;
    private int mRxLinkSpeedLowThresholdMbps;
    private int mHealthMonitorShortConnectionDurationThrMs;
    private long mAbnormalDisconnectionReasonCodeMask;
    private int mHealthMonitorRssiPollValidTimeMs;
    private int mNonstationaryScanRssiValidTimeMs;
    private int mStationaryScanRssiValidTimeMs;
    private int mHealthMonitorFwAlertValidTimeMs;
    private int mMinConfirmationDurationSendLowScoreMs;
    private int mMinConfirmationDurationSendHighScoreMs;
    private int mRssiThresholdNotSendLowScoreToCsDbm;
    private int mTrafficStatsThresholdMaxKbyte;
    private int mBandwidthEstimatorLargeTimeConstantSec;
    private boolean mInterfaceFailureBugreportEnabled;
    private boolean mP2pFailureBugreportEnabled;
    private boolean mApmEnhancementEnabled;
    private boolean mAwareSuspensionEnabled;
    private boolean mHighPerfLockDeprecated;
    private Optional<Boolean> mOobPseudonymEnabled = Optional.empty();
    private Consumer<Boolean> mOobPseudonymFeatureFlagChangedListener = null;
    private String mDryRunScorerPkgName;
    private Consumer<String> mDryRunScorerPkgNameChangedListener = null;
    private boolean mApplicationQosPolicyApiEnabled;
    private boolean mAdjustPollRssiIntervalEnabled;
    private boolean mSoftwarePnoEnabled;
    private boolean mIncludePasspointSsidsInPnoScans;
    private boolean mHandleRssiOrganicKernelFailuresEnabled;
    private Set<String> mDisabledAutoBugreports = Collections.EMPTY_SET;

    private final Handler mWifiHandler;

    public DeviceConfigFacade(Context context, Handler handler, WifiMetrics wifiMetrics) {
        mContext = context;
        mWifiMetrics = wifiMetrics;
        mWifiHandler = handler;
        mFeatureFlags = new com.android.wifi.flags.FeatureFlagsImpl();
        updateDeviceConfigFlags();
        DeviceConfig.addOnPropertiesChangedListener(
                NAMESPACE,
                command -> handler.post(command),
                properties -> {
                    updateDeviceConfigFlags();
                });
    }

    private void updateDeviceConfigFlags() {
        mIsAbnormalConnectionBugreportEnabled = DeviceConfig.getBoolean(NAMESPACE,
                "abnormal_connection_bugreport_enabled", false);
        mAbnormalConnectionDurationMs = DeviceConfig.getInt(NAMESPACE,
                "abnormal_connection_duration_ms",
                DEFAULT_ABNORMAL_CONNECTION_DURATION_MS);

        mDataStallDurationMs = DeviceConfig.getInt(NAMESPACE,
                "data_stall_duration_ms", DEFAULT_DATA_STALL_DURATION_MS);
        mDataStallTxTputThrKbps = DeviceConfig.getInt(NAMESPACE,
                "data_stall_tx_tput_thr_kbps", DEFAULT_DATA_STALL_TX_TPUT_THR_KBPS);
        mDataStallRxTputThrKbps = DeviceConfig.getInt(NAMESPACE,
                "data_stall_rx_tput_thr_kbps", DEFAULT_DATA_STALL_RX_TPUT_THR_KBPS);
        mDataStallTxPerThr = DeviceConfig.getInt(NAMESPACE,
                "data_stall_tx_per_thr", DEFAULT_DATA_STALL_TX_PER_THR);
        mDataStallCcaLevelThr = DeviceConfig.getInt(NAMESPACE,
                "data_stall_cca_level_thr", DEFAULT_DATA_STALL_CCA_LEVEL_THR);
        mWifiMetrics.setDataStallDurationMs(mDataStallDurationMs);
        mWifiMetrics.setDataStallTxTputThrKbps(mDataStallTxTputThrKbps);
        mWifiMetrics.setDataStallRxTputThrKbps(mDataStallRxTputThrKbps);
        mWifiMetrics.setDataStallTxPerThr(mDataStallTxPerThr);
        mWifiMetrics.setDataStallCcaLevelThr(mDataStallCcaLevelThr);

        mTxTputSufficientLowThrKbps = DeviceConfig.getInt(NAMESPACE,
                "tput_sufficient_low_thr_kbps", DEFAULT_TX_TPUT_SUFFICIENT_THR_LOW_KBPS);
        mTxTputSufficientHighThrKbps = DeviceConfig.getInt(NAMESPACE,
                "tput_sufficient_high_thr_kbps", DEFAULT_TX_TPUT_SUFFICIENT_THR_HIGH_KBPS);
        mRxTputSufficientLowThrKbps = DeviceConfig.getInt(NAMESPACE,
                "rx_tput_sufficient_low_thr_kbps", DEFAULT_RX_TPUT_SUFFICIENT_THR_LOW_KBPS);
        mRxTputSufficientHighThrKbps = DeviceConfig.getInt(NAMESPACE,
                "rx_tput_sufficient_high_thr_kbps", DEFAULT_RX_TPUT_SUFFICIENT_THR_HIGH_KBPS);
        mTputSufficientRatioThrNum = DeviceConfig.getInt(NAMESPACE,
                "tput_sufficient_ratio_thr_num", DEFAULT_TPUT_SUFFICIENT_RATIO_THR_NUM);
        mTputSufficientRatioThrDen = DeviceConfig.getInt(NAMESPACE,
                "tput_sufficient_ratio_thr_den", DEFAULT_TPUT_SUFFICIENT_RATIO_THR_DEN);
        mTxPktPerSecondThr = DeviceConfig.getInt(NAMESPACE,
                "tx_pkt_per_second_thr", DEFAULT_TX_PACKET_PER_SECOND_THR);
        mRxPktPerSecondThr = DeviceConfig.getInt(NAMESPACE,
                "rx_pkt_per_second_thr", DEFAULT_RX_PACKET_PER_SECOND_THR);

        mConnectionFailureHighThrPercent = DeviceConfig.getInt(NAMESPACE,
                "connection_failure_high_thr_percent",
                DEFAULT_CONNECTION_FAILURE_HIGH_THR_PERCENT);
        mConnectionFailureCountMin = DeviceConfig.getInt(NAMESPACE,
                "connection_failure_count_min",
                DEFAULT_CONNECTION_FAILURE_COUNT_MIN);
        mConnectionFailureDisconnectionHighThrPercent = DeviceConfig.getInt(NAMESPACE,
                "connection_failure_disconnection_high_thr_percent",
                DEFAULT_CONNECTION_FAILURE_DISCONNECTION_HIGH_THR_PERCENT);
        mConnectionFailureDisconnectionCountMin = DeviceConfig.getInt(NAMESPACE,
                "connection_failure_disconnection_count_min",
                DEFAULT_CONNECTION_FAILURE_DISCONNECTION_COUNT_MIN);
        mAssocRejectionHighThrPercent = DeviceConfig.getInt(NAMESPACE,
                "assoc_rejection_high_thr_percent",
                DEFAULT_ASSOC_REJECTION_HIGH_THR_PERCENT);
        mAssocRejectionCountMin = DeviceConfig.getInt(NAMESPACE,
                "assoc_rejection_count_min",
                DEFAULT_ASSOC_REJECTION_COUNT_MIN);
        mAssocTimeoutHighThrPercent = DeviceConfig.getInt(NAMESPACE,
                "assoc_timeout_high_thr_percent",
                DEFAULT_ASSOC_TIMEOUT_HIGH_THR_PERCENT);
        mAssocTimeoutCountMin = DeviceConfig.getInt(NAMESPACE,
                "assoc_timeout_count_min",
                DEFAULT_ASSOC_TIMEOUT_COUNT_MIN);
        mAuthFailureHighThrPercent = DeviceConfig.getInt(NAMESPACE,
                "auth_failure_high_thr_percent",
                DEFAULT_AUTH_FAILURE_HIGH_THR_PERCENT);
        mAuthFailureCountMin = DeviceConfig.getInt(NAMESPACE,
                "auth_failure_count_min",
                DEFAULT_AUTH_FAILURE_COUNT_MIN);
        mShortConnectionNonlocalHighThrPercent = DeviceConfig.getInt(NAMESPACE,
                "short_connection_nonlocal_high_thr_percent",
                DEFAULT_SHORT_CONNECTION_NONLOCAL_HIGH_THR_PERCENT);
        mShortConnectionNonlocalCountMin = DeviceConfig.getInt(NAMESPACE,
                "short_connection_nonlocal_count_min",
                DEFAULT_SHORT_CONNECTION_NONLOCAL_COUNT_MIN);
        mDisconnectionNonlocalHighThrPercent = DeviceConfig.getInt(NAMESPACE,
                "disconnection_nonlocal_high_thr_percent",
                DEFAULT_DISCONNECTION_NONLOCAL_HIGH_THR_PERCENT);
        mDisconnectionNonlocalCountMin = DeviceConfig.getInt(NAMESPACE,
                "disconnection_nonlocal_count_min",
                DEFAULT_DISCONNECTION_NONLOCAL_COUNT_MIN);
        mHealthMonitorRatioThrNumerator = DeviceConfig.getInt(NAMESPACE,
                "health_monitor_ratio_thr_numerator",
                DEFAULT_HEALTH_MONITOR_RATIO_THR_NUMERATOR);
        mHealthMonitorMinRssiThrDbm = DeviceConfig.getInt(NAMESPACE,
                "health_monitor_min_rssi_thr_dbm",
                DEFAULT_HEALTH_MONITOR_MIN_RSSI_THR_DBM);

        mRandomizationFlakySsidHotlist =
                getUnmodifiableSetQuoted("randomization_flaky_ssid_hotlist");
        mNonPersistentMacRandomizationSsidAllowlist =
                getUnmodifiableSetQuoted("aggressive_randomization_ssid_allowlist");
        mNonPersistentMacRandomizationSsidBlocklist =
                getUnmodifiableSetQuoted("aggressive_randomization_ssid_blocklist");

        mIsAbnormalConnectionFailureBugreportEnabled = DeviceConfig.getBoolean(NAMESPACE,
                "abnormal_connection_failure_bugreport_enabled", false);
        mIsAbnormalDisconnectionBugreportEnabled = DeviceConfig.getBoolean(NAMESPACE,
                "abnormal_disconnection_bugreport_enabled", false);
        mHealthMonitorMinNumConnectionAttempt = DeviceConfig.getInt(NAMESPACE,
                "health_monitor_min_num_connection_attempt",
                DEFAULT_HEALTH_MONITOR_MIN_NUM_CONNECTION_ATTEMPT);
        mBugReportMinWindowMs = DeviceConfig.getInt(NAMESPACE,
                "bug_report_min_window_ms",
                DEFAULT_BUG_REPORT_MIN_WINDOW_MS);
        mBugReportThresholdExtraRatio = DeviceConfig.getInt(NAMESPACE,
                "report_bug_report_threshold_extra_ratio",
                DEFAULT_BUG_REPORT_THRESHOLD_EXTRA_RATIO);
        mIsOverlappingConnectionBugreportEnabled = DeviceConfig.getBoolean(NAMESPACE,
                "overlapping_connection_bugreport_enabled", false);
        mOverlappingConnectionDurationThresholdMs = DeviceConfig.getInt(NAMESPACE,
                "overlapping_connection_duration_threshold_ms",
                DEFAULT_OVERLAPPING_CONNECTION_DURATION_THRESHOLD_MS);
        mTxLinkSpeedLowThresholdMbps = DeviceConfig.getInt(NAMESPACE,
                "tx_link_speed_low_threshold_mbps",
                DEFAULT_TX_LINK_SPEED_LOW_THRESHOLD_MBPS);
        mRxLinkSpeedLowThresholdMbps = DeviceConfig.getInt(NAMESPACE,
                "rx_link_speed_low_threshold_mbps",
                DEFAULT_RX_LINK_SPEED_LOW_THRESHOLD_MBPS);
        mWifiBatterySaverEnabled = DeviceConfig.getBoolean(NAMESPACE, "battery_saver_enabled",
                false);
        mHealthMonitorShortConnectionDurationThrMs = DeviceConfig.getInt(NAMESPACE,
                "health_monitor_short_connection_duration_thr_ms",
                DEFAULT_HEALTH_MONITOR_SHORT_CONNECTION_DURATION_THR_MS);
        mAbnormalDisconnectionReasonCodeMask = DeviceConfig.getLong(NAMESPACE,
                "abnormal_disconnection_reason_code_mask",
                DEFAULT_ABNORMAL_DISCONNECTION_REASON_CODE_MASK);
        mHealthMonitorRssiPollValidTimeMs = DeviceConfig.getInt(NAMESPACE,
                "health_monitor_rssi_poll_valid_time_ms",
                DEFAULT_HEALTH_MONITOR_RSSI_POLL_VALID_TIME_MS);
        mNonstationaryScanRssiValidTimeMs = DeviceConfig.getInt(NAMESPACE,
                "nonstationary_scan_rssi_valid_time_ms",
                DEFAULT_NONSTATIONARY_SCAN_RSSI_VALID_TIME_MS);
        mStationaryScanRssiValidTimeMs = DeviceConfig.getInt(NAMESPACE,
                "stationary_scan_rssi_valid_time_ms",
                DEFAULT_STATIONARY_SCAN_RSSI_VALID_TIME_MS);
        mHealthMonitorFwAlertValidTimeMs = DeviceConfig.getInt(NAMESPACE,
                "health_monitor_fw_alert_valid_time_ms",
                DEFAULT_HEALTH_MONITOR_FW_ALERT_VALID_TIME_MS);
        mWifiMetrics.setHealthMonitorRssiPollValidTimeMs(mHealthMonitorRssiPollValidTimeMs);
        mMinConfirmationDurationSendLowScoreMs = DeviceConfig.getInt(NAMESPACE,
                "min_confirmation_duration_send_low_score_ms",
                DEFAULT_MIN_CONFIRMATION_DURATION_SEND_LOW_SCORE_MS);
        mMinConfirmationDurationSendHighScoreMs = DeviceConfig.getInt(NAMESPACE,
                "min_confirmation_duration_send_high_score_ms",
                DEFAULT_MIN_CONFIRMATION_DURATION_SEND_HIGH_SCORE_MS);
        mRssiThresholdNotSendLowScoreToCsDbm = DeviceConfig.getInt(NAMESPACE,
                "rssi_threshold_not_send_low_score_to_cs_dbm",
                DEFAULT_RSSI_THRESHOLD_NOT_SEND_LOW_SCORE_TO_CS_DBM);
        mTrafficStatsThresholdMaxKbyte = DeviceConfig.getInt(NAMESPACE,
                "traffic_stats_threshold_max_kbyte", DEFAULT_TRAFFIC_STATS_THRESHOLD_MAX_KB);
        mBandwidthEstimatorLargeTimeConstantSec = DeviceConfig.getInt(NAMESPACE,
                "bandwidth_estimator_time_constant_large_sec",
                DEFAULT_BANDWIDTH_ESTIMATOR_TIME_CONSTANT_LARGE_SEC);
        mInterfaceFailureBugreportEnabled = DeviceConfig.getBoolean(NAMESPACE,
                "interface_failure_bugreport_enabled", false);
        mP2pFailureBugreportEnabled = DeviceConfig.getBoolean(NAMESPACE,
                "p2p_failure_bugreport_enabled", false);
        mApmEnhancementEnabled = DeviceConfig.getBoolean(NAMESPACE,
                "apm_enhancement_enabled", false);
        mAwareSuspensionEnabled = DeviceConfig.getBoolean(NAMESPACE,
                "aware_suspension_enabled", true);
        mHighPerfLockDeprecated = DeviceConfig.getBoolean(NAMESPACE,
                "high_perf_lock_deprecated", true);
        boolean oobPseudonymEnabled = DeviceConfig.getBoolean(NAMESPACE,
                "oob_pseudonym_enabled", true);
        if (mOobPseudonymEnabled.isPresent()
                && mOobPseudonymEnabled.get() != oobPseudonymEnabled
                && mOobPseudonymFeatureFlagChangedListener != null) {
            mWifiHandler.post(
                    () -> mOobPseudonymFeatureFlagChangedListener.accept(oobPseudonymEnabled));
        }
        mOobPseudonymEnabled = Optional.of(oobPseudonymEnabled);

        String dryRunScorerPkgName = DeviceConfig.getString(NAMESPACE, "dry_run_scorer_pkg_name",
                DEFAULT_DRY_RUN_SCORER_PKG_NAME);
        if (mDryRunScorerPkgNameChangedListener != null
                && !dryRunScorerPkgName.equalsIgnoreCase(mDryRunScorerPkgName)) {
            mWifiHandler.post(
                    () -> mDryRunScorerPkgNameChangedListener.accept(dryRunScorerPkgName));
        }
        mDryRunScorerPkgName = dryRunScorerPkgName;

        mApplicationQosPolicyApiEnabled = DeviceConfig.getBoolean(NAMESPACE,
                "application_qos_policy_api_enabled", true);
        mAdjustPollRssiIntervalEnabled =
                DeviceConfig.getBoolean(NAMESPACE, "adjust_poll_rssi_interval_enabled", false);
        mSoftwarePnoEnabled = DeviceConfig.getBoolean(NAMESPACE,
                "software_pno_enabled", false);
        mIncludePasspointSsidsInPnoScans = DeviceConfig.getBoolean(NAMESPACE,
                "include_passpoint_ssids_in_pno_scans", true);
        mHandleRssiOrganicKernelFailuresEnabled = DeviceConfig.getBoolean(NAMESPACE,
                "handle_rssi_organic_kernel_failures_enabled", true);
        mDisabledAutoBugreports = getDisabledAutoBugreports();
    }

    private Set<String> getDisabledAutoBugreports() {
        String rawList = DeviceConfig.getString(NAMESPACE,
                "disabled_auto_bugreport_title_and_description", null);
        if (rawList == null || rawList.isEmpty()) {
            return Collections.EMPTY_SET;
        }
        Set<String> result = new ArraySet<>();
        String[] list = rawList.split(",");
        for (String cur : list) {
            if (cur.length() == 0) {
                continue;
            }
            result.add(cur);
        }
        return Collections.unmodifiableSet(result);
    }

    private Set<String> getUnmodifiableSetQuoted(String key) {
        String rawList = DeviceConfig.getString(NAMESPACE, key, "");
        Set<String> result = new ArraySet<>();
        String[] list = rawList.split(",");
        for (String cur : list) {
            if (cur.length() == 0) {
                continue;
            }
            result.add("\"" + cur + "\"");
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Gets the feature flag for reporting abnormally long connections.
     */
    public boolean isAbnormalConnectionBugreportEnabled() {
        return mIsAbnormalConnectionBugreportEnabled;
    }

    /**
     * Gets the threshold for classifying abnormally long connections.
     */
    public int getAbnormalConnectionDurationMs() {
        return mAbnormalConnectionDurationMs;
    }

    /**
     * Gets the duration of evaluating Wifi condition to trigger a data stall.
     */
    public int getDataStallDurationMs() {
        return mDataStallDurationMs;
    }

    /**
     * Gets the threshold of Tx throughput below which to trigger a data stall.
     */
    public int getDataStallTxTputThrKbps() {
        return mDataStallTxTputThrKbps;
    }

    /**
     * Gets the threshold of Rx throughput below which to trigger a data stall.
     */
    public int getDataStallRxTputThrKbps() {
        return mDataStallRxTputThrKbps;
    }

    /**
     * Gets the threshold of Tx packet error rate above which to trigger a data stall.
     */
    public int getDataStallTxPerThr() {
        return mDataStallTxPerThr;
    }

    /**
     * Gets the threshold of CCA level above which to trigger a data stall.
     */
    public int getDataStallCcaLevelThr() {
        return mDataStallCcaLevelThr;
    }

    /**
     * Gets the low threshold of L2 throughput below which L2 throughput is always insufficient
     */
    public int getTxTputSufficientLowThrKbps() {
        return mTxTputSufficientLowThrKbps;
    }

    /**
     * Gets the high threshold of L2 throughput above which L2 throughput is always sufficient
     */
    public int getTxTputSufficientHighThrKbps() {
        return mTxTputSufficientHighThrKbps;
    }

    /**
     * Gets the low threshold of L2 throughput below which L2 Rx throughput is always insufficient
     */
    public int getRxTputSufficientLowThrKbps() {
        return mRxTputSufficientLowThrKbps;
    }

    /**
     * Gets the high threshold of L2 throughput above which L2 Rx throughput is always sufficient
     */
    public int getRxTputSufficientHighThrKbps() {
        return mRxTputSufficientHighThrKbps;
    }

    /**
     * Gets the numerator part of L2 throughput over L3 throughput ratio sufficiency threshold
     * above which L2 throughput is sufficient
     */
    public int getTputSufficientRatioThrNum() {
        return mTputSufficientRatioThrNum;
    }

    /**
     * Gets the denominator part of L2 throughput over L3 throughput ratio sufficiency threshold
     * above which L2 throughput is sufficient
     */
    public int getTputSufficientRatioThrDen() {
        return mTputSufficientRatioThrDen;
    }

    /**
     * Gets the threshold of Tx packet per second
     * below which Tx throughput sufficiency check will always pass
     */
    public int getTxPktPerSecondThr() {
        return mTxPktPerSecondThr;
    }

    /**
     * Gets the threshold of Rx packet per second
     * below which Rx throughput sufficiency check will always pass
     */
    public int getRxPktPerSecondThr() {
        return mRxPktPerSecondThr;
    }

    /**
     * Gets the high threshold of connection failure rate in percent
     */
    public int getConnectionFailureHighThrPercent() {
        return mConnectionFailureHighThrPercent;
    }

    /**
     * Gets connection-failure-due-to-disconnection min count
     */
    public int getConnectionFailureDisconnectionCountMin() {
        return mConnectionFailureDisconnectionCountMin;
    }

    /**
     * Gets the high threshold of connection-failure-due-to-disconnection rate in percent
     */
    public int getConnectionFailureDisconnectionHighThrPercent() {
        return mConnectionFailureDisconnectionHighThrPercent;
    }

    /**
     * Gets connection failure min count
     */
    public int getConnectionFailureCountMin() {
        return mConnectionFailureCountMin;
    }

    /**
     * Gets the high threshold of association rejection rate in percent
     */
    public int getAssocRejectionHighThrPercent() {
        return mAssocRejectionHighThrPercent;
    }

    /**
     * Gets association rejection min count
     */
    public int getAssocRejectionCountMin() {
        return mAssocRejectionCountMin;
    }

    /**
     * Gets the high threshold of association timeout rate in percent
     */
    public int getAssocTimeoutHighThrPercent() {
        return mAssocTimeoutHighThrPercent;
    }

    /**
     * Gets association timeout min count
     */
    public int getAssocTimeoutCountMin() {
        return mAssocTimeoutCountMin;
    }


    /**
     * Gets the high threshold of authentication failure rate in percent
     */
    public int getAuthFailureHighThrPercent() {
        return mAuthFailureHighThrPercent;
    }

    /**
     * Gets authentication failure min count
     */
    public int getAuthFailureCountMin() {
        return mAuthFailureCountMin;
    }

    /**
     * Gets the high threshold of nonlocal short connection rate in percent
     */
    public int getShortConnectionNonlocalHighThrPercent() {
        return mShortConnectionNonlocalHighThrPercent;
    }

    /**
     * Gets nonlocal short connection min count
     */
    public int getShortConnectionNonlocalCountMin() {
        return mShortConnectionNonlocalCountMin;
    }

    /**
     * Gets the high threshold of nonlocal disconnection rate in percent
     */
    public int getDisconnectionNonlocalHighThrPercent() {
        return mDisconnectionNonlocalHighThrPercent;
    }

    /**
     * Gets nonlocal disconnection min count
     */
    public int getDisconnectionNonlocalCountMin() {
        return mDisconnectionNonlocalCountMin;
    }

    /**
     * Gets health monitor ratio threshold, numerator part
     */
    public int getHealthMonitorRatioThrNumerator() {
        return mHealthMonitorRatioThrNumerator;
    }

    /**
     * Gets health monitor min RSSI threshold in dBm
     */
    public int getHealthMonitorMinRssiThrDbm() {
        return mHealthMonitorMinRssiThrDbm;
    }

    /**
     * Gets the Set of SSIDs in the flaky SSID hotlist.
     */
    public Set<String> getRandomizationFlakySsidHotlist() {
        return mRandomizationFlakySsidHotlist;
    }

    /**
     * Gets the list of SSIDs for non-persistent MAC randomization.
     */
    public Set<String> getNonPersistentMacRandomizationSsidAllowlist() {
        return mNonPersistentMacRandomizationSsidAllowlist;
    }

    /**
     * Gets the list of SSIDs that non-persistent MAC randomization should not be used for.
     */
    public Set<String> getNonPersistentMacRandomizationSsidBlocklist() {
        return mNonPersistentMacRandomizationSsidBlocklist;
    }
    /**
     * Gets the feature flag for reporting abnormal connection failure.
     */
    public boolean isAbnormalConnectionFailureBugreportEnabled() {
        return mIsAbnormalConnectionFailureBugreportEnabled;
    }

    /**
     * Gets the feature flag for reporting abnormal disconnection.
     */
    public boolean isAbnormalDisconnectionBugreportEnabled() {
        return mIsAbnormalDisconnectionBugreportEnabled;
    }

    /**
     * Gets health monitor min number of connection attempt threshold
     */
    public int getHealthMonitorMinNumConnectionAttempt() {
        return mHealthMonitorMinNumConnectionAttempt;
    }

    /**
     * Gets minimum wait time between two bug report captures
     */
    public int getBugReportMinWindowMs() {
        return mBugReportMinWindowMs;
    }

    /**
     * Gets the extra ratio of threshold to trigger bug report.
     */
    public int getBugReportThresholdExtraRatio() {
        return mBugReportThresholdExtraRatio;
    }

    /**
     * Gets the feature flag for reporting overlapping connection.
     */
    public boolean isOverlappingConnectionBugreportEnabled() {
        return mIsOverlappingConnectionBugreportEnabled;
    }

    /**
     * Gets overlapping connection duration threshold in ms
     */
    public int getOverlappingConnectionDurationThresholdMs() {
        return mOverlappingConnectionDurationThresholdMs;
    }

    /**
     * Gets the threshold of link speed below which Tx link speed is ignored at low traffic
     */
    public int getTxLinkSpeedLowThresholdMbps() {
        return mTxLinkSpeedLowThresholdMbps;
    }

    /**
     * Gets the threshold of link speed below which Rx link speed is ignored at low traffic
     */
    public int getRxLinkSpeedLowThresholdMbps() {
        return mRxLinkSpeedLowThresholdMbps;
    }

    /**
     * Gets the feature flag for Wifi battery saver.
     */
    public boolean isWifiBatterySaverEnabled() {
        return mWifiBatterySaverEnabled;
    }

    /**
     * Gets health monitor short connection duration threshold in ms
     */
    public int getHealthMonitorShortConnectionDurationThrMs() {
        return mHealthMonitorShortConnectionDurationThrMs;
    }

    /**
     * Gets abnormal disconnection reason code mask
     */
    public long getAbnormalDisconnectionReasonCodeMask() {
        return mAbnormalDisconnectionReasonCodeMask;
    }

    /**
     * Gets health monitor RSSI poll valid time in ms
     */
    public int getHealthMonitorRssiPollValidTimeMs() {
        return mHealthMonitorRssiPollValidTimeMs;
    }

    /**
     * Gets scan rssi valid time in ms when device is in non-stationary state
     */
    public int getNonstationaryScanRssiValidTimeMs() {
        return mNonstationaryScanRssiValidTimeMs;
    }

    /**
     * Gets scan rssi valid time in ms when device is in stationary state
     */
    public int getStationaryScanRssiValidTimeMs() {
        return mStationaryScanRssiValidTimeMs;
    }

    /**
     * Gets health monitor firmware alert valid time in ms,
     * -1 disables firmware alert time check
     */
    public int getHealthMonitorFwAlertValidTimeMs() {
        return mHealthMonitorFwAlertValidTimeMs;
    }

    /**
     * Gets the minimum confirmation duration for sending network score to connectivity service
     * when score breaches low.
     */
    public int getMinConfirmationDurationSendLowScoreMs() {
        return mMinConfirmationDurationSendLowScoreMs;
    }

    /**
     * Gets the minimum confirmation duration for sending network score to connectivity service
     * when score breaches high.
     */
    public int getMinConfirmationDurationSendHighScoreMs() {
        return mMinConfirmationDurationSendHighScoreMs;
    }

    /**
     * Gets the RSSI threshold above which low score is not sent to connectivity service when
     * external scorer takes action.
     */
    public int getRssiThresholdNotSendLowScoreToCsDbm() {
        return mRssiThresholdNotSendLowScoreToCsDbm;
    }

    /**
     * Gets traffic stats maximum threshold in KByte
     */
    public int getTrafficStatsThresholdMaxKbyte() {
        return mTrafficStatsThresholdMaxKbyte;
    }

    /**
     * Gets bandwidth estimator large time constant in second
     */
    public int getBandwidthEstimatorLargeTimeConstantSec() {
        return mBandwidthEstimatorLargeTimeConstantSec;
    }

    /**
     * Gets the feature flag for reporting interface setup failure
     */
    public boolean isInterfaceFailureBugreportEnabled() {
        return mInterfaceFailureBugreportEnabled;
    }

    /**
     * Gets the feature flag for reporting p2p setup failure
     */
    public boolean isP2pFailureBugreportEnabled() {
        return mP2pFailureBugreportEnabled;
    }

    /**
     * Gets the feature flag for APM enhancement
     */
    public boolean isApmEnhancementEnabled() {
        // reads the value set by Bluetooth device config for APM enhancement feature flag
        return Settings.Global.getInt(
                mContext.getContentResolver(), "apm_enhancement_enabled", 0) == 1;
    }

    /**
     * Gets the feature flag for Aware suspension
     */
    public boolean isAwareSuspensionEnabled() {
        return mAwareSuspensionEnabled;
    }

    /**
     * Gets the feature flag for High Perf lock deprecation
     */
    public boolean isHighPerfLockDeprecated() {
        return mHighPerfLockDeprecated;
    }

    /**
     * Gets the feature flag for the OOB pseudonym of EAP-SIM/AKA/AKA'
     */
    public boolean isOobPseudonymEnabled() {
        return mOobPseudonymEnabled.isPresent() && mOobPseudonymEnabled.get();
    }

    /**
     * Gets the feature flag indicating whether the application QoS policy API is enabled.
     */
    public boolean isApplicationQosPolicyApiEnabled() {
        return mApplicationQosPolicyApiEnabled;
    }

    /**
     * Gets the feature flag for adjusting link layer stats and RSSI polling interval
     */
    public boolean isAdjustPollRssiIntervalEnabled() {
        return mAdjustPollRssiIntervalEnabled;
    }

    /**
     * Gets the feature flag for Software PNO
     */
    public boolean isSoftwarePnoEnabled() {
        return mSoftwarePnoEnabled;
    }

    /**
     * Gets the feature flag indicating whether Passpoint SSIDs should be included in PNO scans.
     */
    public boolean includePasspointSsidsInPnoScans() {
        return mIncludePasspointSsidsInPnoScans;
    }

    /**
     * Gets the feature flag indicating whether handling IP reachability failures triggered from
     * Wi-Fi RSSI polling or organic kernel probes the same as failure post roaming.
     */
    public boolean isHandleRssiOrganicKernelFailuresEnabled() {
        return mHandleRssiOrganicKernelFailuresEnabled;
    }

    /*
     * Sets the listener to be notified when the OOB Pseudonym feature is enabled;
     * Only 1 listener is accepted.
     */
    public void setOobPseudonymFeatureFlagChangedListener(
            Consumer<Boolean> listener) {
        mOobPseudonymFeatureFlagChangedListener = listener;
    }

    /*
     * Sets the listener to be notified when the DryRunScorerPkgName is changed.
     * Only 1 listener is accepted.
     */
    public void setDryRunScorerPkgNameChangedListener(Consumer<String> listener) {
        mDryRunScorerPkgNameChangedListener = listener;
    }

    public String getDryRunScorerPkgName() {
        return mDryRunScorerPkgName;
    }

    /**
     * Get the set of bugreports that are explicitly disabled.
     * @return A Set of String to indicate disabled auto-bugreports trigger points.
     */
    public Set<String> getDisabledAutoBugreportTitleAndDetails() {
        return mDisabledAutoBugreports;
    }

    public FeatureFlags getFeatureFlags() {
        return mFeatureFlags;
    }
}
