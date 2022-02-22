package net.andylizi.haproxydetector;

import org.bstats.charts.CustomChart;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class ConnectionStats {
    private static final ReentrantReadWriteLock connLock = new ReentrantReadWriteLock(false);
    private static final WeakHashMap<InetSocketAddress, Boolean> connTracker = new WeakHashMap<>();

    private static final ReentrantReadWriteLock playerLock = new ReentrantReadWriteLock(false);
    private static final WeakHashMap<InetSocketAddress, Boolean> playerTracker = new WeakHashMap<>();

    public static void trackConnection(SocketAddress addr, boolean isProxy) {
        if (addr instanceof InetSocketAddress) {
            connLock.writeLock().lock();
            try {
                connTracker.put((InetSocketAddress) addr, isProxy);
            } finally {
                connLock.writeLock().unlock();
            }
        }
    }

    public static void trackLogin(SocketAddress addr) {
        if (!(addr instanceof InetSocketAddress)) return;

        Boolean isProxy;
        connLock.readLock().lock();
        try {
            isProxy = connTracker.get(addr);
        } finally {
            connLock.readLock().unlock();
        }

        if (isProxy != null) {
            playerLock.writeLock().lock();
            try {
                playerTracker.put((InetSocketAddress) addr, isProxy);
            } finally {
                playerLock.writeLock().unlock();
            }
        }
    }

    public static void trackDisconnect(SocketAddress addr) {
        if (!(addr instanceof InetSocketAddress)) return;

        playerLock.writeLock().lock();
        try {
            playerTracker.remove(addr);
        } finally {
            playerLock.writeLock().unlock();
        }
    }

    public static int countPlayers(boolean isProxy) {
        playerLock.readLock().lock();
        try {
            return (int) playerTracker.values().stream().filter(b -> b == isProxy).count();
        } finally {
            playerLock.readLock().unlock();
        }
    }

    public static List<CustomChart> createCharts() {
        return Arrays.asList(
            new SimplePie("whitelist_count", () ->
                Integer.toString(ProxyWhitelist.whitelist == null ? 0 : ProxyWhitelist.whitelist.size())),
            new SingleLineChart("direct_players",
                () -> ConnectionStats.countPlayers(false)),
            new SingleLineChart("proxied_players",
                () -> ConnectionStats.countPlayers(true))
        );
    }
}
