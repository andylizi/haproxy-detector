package net.andylizi.haproxydetector;

import org.bstats.charts.CustomChart;
import org.bstats.charts.SimplePie;

public final class MetricsId {
    public static String KEY_WHITELIST_COUNT = "whitelist_count";
    public static String KEY_PROTOCOLLIB_VERSION = "protocollib_version";

    public static CustomChart createWhitelistCountChart() {
        return new SimplePie(KEY_WHITELIST_COUNT,
                () -> ProxyWhitelist.whitelist == null ? "0" : Integer.toString(ProxyWhitelist.whitelist.size()));
    }

    private MetricsId() {throw new AssertionError();}
}
