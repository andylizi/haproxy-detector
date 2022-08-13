package net.andylizi.haproxydetector.bukkit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.comphenix.protocol.ProtocolLibrary;

import net.andylizi.haproxydetector.ProxyWhitelist;
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

        if (ReflectionUtil.hasClass("com.comphenix.protocol.injector.netty.ProtocolInjector")) {
            injectionStrategy = new InjectionStrategy1();
        } else {
            throw new UnsupportedOperationException(
                    "unsupported ProtocolLib version " + ProtocolLibrary.getPlugin().getDescription().getVersion());
        }

        try {
            injectionStrategy.inject();
        } catch (ReflectiveOperationException e) {
            sneakyThrow(e);
        }

        try {
            new Metrics(this, 12604);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Failed to start metrics", t);
        }
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
}
