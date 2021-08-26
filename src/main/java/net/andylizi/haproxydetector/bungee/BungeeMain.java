/*
 * Copyright (C) 2020 Andy Li
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Lesser Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
*/
package net.andylizi.haproxydetector.bungee;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.NoSuchElementException;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.util.AttributeKey;
import net.andylizi.haproxydetector.HAProxyDetectorHandler;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;

import static net.andylizi.haproxydetector.HAProxyDetectorHandler.sneakyThrow;

public final class BungeeMain extends Plugin implements Listener {
    static Logger logger;
    static Field serverChildField;
    static MethodHandle proxyProtocolChecker;
    static AttributeKey<ListenerInfo> listenerAttr;
    ChannelInitializer<Channel> originalChildInitializer;

    @Override
    public void onLoad() {
        logger = getLogger();

        try {
            proxyProtocolChecker = MethodHandles.lookup().findVirtual(ListenerInfo.class, "isProxyProtocol",
                    MethodType.methodType(boolean.class));
        } catch (NoSuchMethodException ignored) {
            proxyProtocolChecker = null;
        } catch (ReflectiveOperationException e) {
            sneakyThrow(e);
        }
    }

    @Override
    @SuppressWarnings({ "unchecked", "deprecation" })
    public void onEnable() {
        try {
            Class<?> pipelineUtilsClass = Class.forName("net.md_5.bungee.netty.PipelineUtils", true,
                    Thread.currentThread().getContextClassLoader());
            listenerAttr = (AttributeKey<ListenerInfo>) pipelineUtilsClass.getField("LISTENER").get(null);

            serverChildField = pipelineUtilsClass.getField("SERVER_CHILD");
            serverChildField.setAccessible(true);

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(serverChildField, serverChildField.getModifiers() & ~Modifier.FINAL);

            originalChildInitializer = (ChannelInitializer<Channel>) serverChildField.get(null);
            serverChildField.set(null, new HAProxyDetectorInitializer<Channel>(originalChildInitializer));
        } catch (ReflectiveOperationException e) {
            sneakyThrow(e);
            return;
        }

        if (proxyProtocolChecker != null) {
            if (Stream.concat(getProxy().getConfigurationAdapter().getListeners().stream(), getProxy().getConfig().getListeners().stream())
                    .noneMatch(l -> {
                        try {
                            return (boolean) proxyProtocolChecker.invokeExact(l);
                        } catch (Throwable e) {
                            sneakyThrow(e);
                            return false;
                        }
                    })) {
                logger.warning("Proxy protocol is disabled, the plugin may not work correctly!");
            }
        }
    }

    @Override
    public void onDisable() {
        if (serverChildField != null && originalChildInitializer != null) {
            try {
                serverChildField.set(null, originalChildInitializer);
                originalChildInitializer = null;
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    static class HAProxyDetectorInitializer<C extends Channel> extends ChannelInitializer<C> {
        static MethodHandle initChannelHandle;

        static {
            try {
                Method m = ChannelInitializer.class.getDeclaredMethod("initChannel", Channel.class);
                m.setAccessible(true);
                initChannelHandle = MethodHandles.lookup().unreflect(m);
            } catch (ReflectiveOperationException e) {
                sneakyThrow(e);
            }
        }

        private final MethodHandle delegateInitHandle;

        public HAProxyDetectorInitializer(ChannelInitializer<C> delegateInitializer) {
            delegateInitHandle = initChannelHandle.bindTo(delegateInitializer);
        }

        @Override
        public void initChannel(C ch) throws Exception {
            try {
                delegateInitHandle.invoke(ch);
            } catch (Throwable e) {
                sneakyThrow(e);
                return;
            }

            if (proxyProtocolChecker != null) {
                ListenerInfo listener = ch.attr(listenerAttr).get();

                boolean isProxyProtocol;
                try {
                    isProxyProtocol = (boolean) proxyProtocolChecker.invokeExact(listener);
                } catch (Throwable e) {
                    sneakyThrow(e);
                    return;
                }
                if (!isProxyProtocol) {
                    return; // only proceed if listener has proxy protocol enabled
                }
            }

            ChannelPipeline pipeline = ch.pipeline();
            if (!ch.isOpen() || pipeline.get("haproxy-detector") != null)
                return;

            HAProxyDetectorHandler detectorHandler = new HAProxyDetectorHandler(logger, null);
            ChannelHandler oldHandler;
            if ((oldHandler = pipeline.get("haproxy-decoder")) != null || 
                    (oldHandler = pipeline.get(HAProxyMessageDecoder.class)) != null) {
                pipeline.replace(oldHandler, "haproxy-detector", detectorHandler);
            } else {
                throw new NoSuchElementException("HAProxy support not enabled");
            }
        }
    }
}
