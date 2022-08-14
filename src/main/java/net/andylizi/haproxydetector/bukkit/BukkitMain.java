package net.andylizi.haproxydetector.bukkit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.comphenix.protocol.ProtocolLibrary;

import com.comphenix.protocol.utility.MinecraftReflection;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import net.andylizi.haproxydetector.MetricsId;
import net.andylizi.haproxydetector.ProxyWhitelist;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;

import net.andylizi.haproxydetector.ReflectionUtil;

import static net.andylizi.haproxydetector.ReflectionUtil.sneakyThrow;

public final class BukkitMain extends JavaPlugin {
    static Logger logger;

    private InjectionStrategy injectionStrategy;

    @Override
    public void onLoad() {
        logger = getLogger();
    }

    @Override
    public void onEnable() {
        try {
            Path path = this.getDataFolder().toPath().resolve("whitelist.conf");
            ProxyWhitelist whitelist = ProxyWhitelist.loadOrDefault(path).orElse(null);
            if (whitelist == null) {
                logger.warning("!!! ==============================");
                logger.warning("!!! Proxy whitelist is disabled in the config.");
                logger.warning("!!! This is EXTREMELY DANGEROUS, don't do this in production!");
                logger.warning("!!! ==============================");
            } else if (whitelist.size() == 0) {
                logger.warning("Proxy whitelist is empty. This will disallow all proxies!");
            }
            ProxyWhitelist.whitelist = whitelist;
        } catch (IOException e) {
            throw new RuntimeException("failed to load proxy whitelist", e);
        }

        if (!ProtocolLibrary.getPlugin().isEnabled()) {
            logger.severe("Required dependency ProtocolLib is not enabled, exiting");
            this.setEnabled(false);
            return;
        }
        String plVersion = ProtocolLibrary.getPlugin().getDescription().getVersion();

        try {
            if (ReflectionUtil.hasClass("com.comphenix.protocol.injector.netty.ProtocolInjector")) {
                injectionStrategy = createInjectionStrategy1();
            } else if (ReflectionUtil.hasClass(
                    "com.comphenix.protocol.injector.netty.manager.NetworkManagerInjector")) {
                injectionStrategy = createInjectionStrategy2();
            } else {
                throw new UnsupportedOperationException("unsupported ProtocolLib version " + plVersion);
            }

            injectionStrategy.inject();
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            throw new UnsupportedOperationException("unsupported ProtocolLib version " + plVersion, e);
        } catch (ReflectiveOperationException e) {
            sneakyThrow(e);
        }

        try {
            Metrics metrics = new Metrics(this, 12604);
            metrics.addCustomChart(MetricsId.createWhitelistCountChart());
            metrics.addCustomChart(new SimplePie(MetricsId.KEY_PROTOCOLLIB_VERSION,
                    () -> ProtocolLibrary.getPlugin().getDescription().getVersion()));
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Failed to start metrics", t);
        }
    }

    // Use separated methods to make sure the strategy classes won't be loaded
    // until they're actually used.
    private static InjectionStrategy createInjectionStrategy1() throws ReflectiveOperationException {
        return new InjectionStrategy1(logger);
    }

    private static InjectionStrategy createInjectionStrategy2() throws ReflectiveOperationException {
        return new InjectionStrategy2(logger);
    }

    @Override
    public void onDisable() {
        if (injectionStrategy != null) {
            try {
                injectionStrategy.uninject();
            } catch (Throwable ignored) {
            }
        }
    }

    @SuppressWarnings("unchecked")
    static ChannelHandler getNetworkManager(ChannelPipeline pipeline) {
        Class<? extends ChannelHandler> networkManagerClass = (Class<? extends ChannelHandler>) MinecraftReflection.getNetworkManagerClass();
        ChannelHandler networkManager = null;
        for (Map.Entry<String, ChannelHandler> entry : pipeline) {
            if (networkManagerClass.isAssignableFrom(entry.getValue().getClass())) {
                networkManager = entry.getValue();
                break;
            }
        }

        if (networkManager == null) {
            throw new IllegalArgumentException("NetworkManager not found in channel pipeline " + pipeline.names());
        }

        return networkManager;
    }
}
