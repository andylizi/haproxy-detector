package net.andylizi.haproxydetector.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.config.ProxyConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import net.andylizi.haproxydetector.MetricsId;
import net.andylizi.haproxydetector.ProxyWhitelist;
import net.andylizi.haproxydetector.ReflectionUtil;
import org.bstats.velocity.Metrics;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.NoSuchElementException;

import static net.andylizi.haproxydetector.ReflectionUtil.sneakyThrow;

@Plugin(id = "haproxy-detector", name = "HAProxyDetector", version = "3.0.2",
    url = "https://github.com/andylizi/haproxy-detector",
    description = "Enables proxied and direct connections both at the same time.",
    authors = {"andylizi"})
public final class VelocityMain {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;

    @Inject
    public VelocityMain(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) throws ReflectiveOperationException, IOException {
        if (!isProxyEnabled()) {
            logger.error("!!! ==============================");
            logger.error("!!! Proxy protocol is not enabled,");
            logger.error("!!! the plugin will not work correctly!");
            logger.error("!!! ==============================");
        }

        ProxyWhitelist whitelist = ProxyWhitelist.loadOrDefault(this.dataDirectory.resolve("whitelist.conf")).orElse(null);
        if (whitelist == null) {
            logger.warn("!!! ==============================");
            logger.warn("!!! Proxy whitelist is disabled in the config.");
            logger.warn("!!! This is EXTREMELY DANGEROUS, don't do this in production!");
            logger.warn("!!! ==============================");
        } else if (whitelist.size() == 0) {
            logger.warn("Proxy whitelist is empty. This will disallow all proxies!");
        }
        ProxyWhitelist.whitelist = whitelist;

        inject();

        try {
            Metrics metrics = metricsFactory.make(this, 14442);
            metrics.addCustomChart(MetricsId.createWhitelistCountChart());
        } catch (Throwable t) {
            logger.warn("Failed to start metrics", t);
        }
    }

    private boolean isProxyEnabled() throws ReflectiveOperationException {
        ProxyConfig config = this.server.getConfiguration();
        Method isProxyProtocol = config.getClass().getMethod("isProxyProtocol");
        return (boolean) isProxyProtocol.invoke(config);
    }

    private void inject() throws ReflectiveOperationException {
        Class<?> cmType = Class.forName("com.velocitypowered.proxy.network.ConnectionManager");
        Field cmField = ReflectionUtil.getFirstDeclaringFieldByType(this.server.getClass(), cmType);
        cmField.setAccessible(true);
        Object connectionManager = cmField.get(this.server);

        Object holder = cmType.getMethod("getServerChannelInitializer").invoke(connectionManager);
        Class<?> holderType = holder.getClass();

        @SuppressWarnings("unchecked") ChannelInitializer<Channel> originalInitializer =
            (ChannelInitializer<Channel>) holderType.getMethod("get").invoke(holder);

        DetectorInitializer<Channel> newInitializer =
            new DetectorInitializer<>(logger, originalInitializer);
        MethodHandle set = MethodHandles.lookup().unreflect(holderType.getMethod("set", ChannelInitializer.class));
        try {
            logger.info("Replacing channel initializer; you can safely ignore the following warning.");
            // We use MethodHandle here because it has a cleaner stacktrace
            // for ChannelInitializerHolder.set() to display
            set.invoke(holder, newInitializer);
        } catch (Throwable e) {
            sneakyThrow(e);
        }
    }

    static class DetectorInitializer<C extends Channel> extends ChannelInitializer<C> {
        static final MethodHandle INIT_CHANNEL;

        static {
            MethodHandle handle = null;
            try {
                Method m = ChannelInitializer.class.getDeclaredMethod("initChannel", Channel.class);
                m.setAccessible(true);
                handle = MethodHandles.lookup().unreflect(m);
            } catch (ReflectiveOperationException e) {
                sneakyThrow(e);
            }
            INIT_CHANNEL = handle;
        }

        private final Logger logger;
        private final ChannelInitializer<C> delegate;

        DetectorInitializer(@NotNull Logger logger, @NotNull ChannelInitializer<C> delegate) {
            this.logger = logger;
            this.delegate = delegate;
        }

        @Override
        public void initChannel(C ch) {
            try {
                INIT_CHANNEL.invoke(this.delegate, ch);
            } catch (Throwable e) {
                sneakyThrow(e);
                return;
            }

            ChannelPipeline pipeline = ch.pipeline();
            if (!ch.isOpen() || pipeline.get("haproxy-detector") != null)
                return;

            try {
                HAProxyMessageDecoder decoder = pipeline.get(HAProxyMessageDecoder.class);
                pipeline.replace(decoder, "haproxy-detector", new HAProxyDetectorHandler(logger));
            } catch (NoSuchElementException | NullPointerException e) {
                throw new RuntimeException("HAProxy support is not enabled", e);
            }
        }
    }
}
