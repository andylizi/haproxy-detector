package net.andylizi.haproxydetector.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.config.ProxyConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import net.andylizi.haproxydetector.ReflectionUtil;
import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.NoSuchElementException;

import static net.andylizi.haproxydetector.ReflectionUtil.sneakyThrow;

@Plugin(id = "haproxy-detector", name = "HAProxyDetector", version = "2.2.0",
    url = "https://github.com/andylizi/haproxy-detector",
    description = "Enables proxied and direct connections both at the same time.",
    authors = {"andylizi"})
public final class VelocityMain {
    private final ProxyServer server;
    private final Logger logger;
    private final Metrics.Factory metricsFactory;

    @Inject
    public VelocityMain(ProxyServer server, Logger logger, Metrics.Factory metricsFactory) {
        this.server = server;
        this.logger = logger;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) throws ReflectiveOperationException {
        if (!isProxyEnabled()) {
            logger.error("!!! ==============================");
            logger.error("!!! Proxy protocol is not enabled, the plugin will not work correctly!");
            logger.error("!!! ==============================");
        }

        metricsFactory.make(this, 14442);
        inject();
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

        logger.info("Replacing channel initializers; you can safely ignore the following warning.");
        HAProxyDetectorInitializer<Channel> newInitializer = new HAProxyDetectorInitializer<>(originalInitializer);
        holderType.getMethod("set", ChannelInitializer.class).invoke(holder, newInitializer);
    }

    static class HAProxyDetectorInitializer<C extends Channel> extends ChannelInitializer<C> {
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

        private final ChannelInitializer<C> delegate;

        HAProxyDetectorInitializer(ChannelInitializer<C> delegate) {
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
                pipeline.replace(decoder, "haproxy-detector", new HAProxyDetectorHandler());
            } catch (NoSuchElementException e) {
                throw new RuntimeException("HAProxy support is not enabled", e);
            }
        }
    }
}
