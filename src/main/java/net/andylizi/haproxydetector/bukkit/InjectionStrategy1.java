package net.andylizi.haproxydetector.bukkit;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.error.ErrorReporter;
import com.comphenix.protocol.injector.netty.ChannelListener;
import com.comphenix.protocol.injector.netty.Injector;
import com.comphenix.protocol.injector.netty.channel.InjectionFactory;
import com.comphenix.protocol.injector.netty.manager.NetworkManagerInjector;
import com.comphenix.protocol.injector.temporary.TemporaryPlayerFactory;
import com.comphenix.protocol.reflect.FuzzyReflection;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import net.andylizi.haproxydetector.HAProxyDetectorHandler;
import net.andylizi.haproxydetector.ReflectionUtil;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

public class InjectionStrategy1 implements InjectionStrategy {
    private final Logger logger;

    private Field injectorFactoryField;
    private NetworkManagerInjector injector;
    private InjectionFactory oldFactory;

    public InjectionStrategy1(Logger logger) {this.logger = logger;}

    @Override
    public void inject() throws ReflectiveOperationException {
        try {
            this.uninject();
        } catch (Throwable ignored) {
        }

        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        Field injectorField = FuzzyReflection.fromObject(pm, true)
                .getFieldByType("nettyInjector", NetworkManagerInjector.class);
        injectorField.setAccessible(true);
        injector = (NetworkManagerInjector) injectorField.get(pm);

        injectorFactoryField = FuzzyReflection.fromObject(injector, true)
                .getFieldByType("factory", InjectionFactory.class);
        injectorFactoryField.setAccessible(true);

        oldFactory = (InjectionFactory) injectorFactoryField.get(injector);
        InjectionFactory newFactory = new HAProxyInjectorFactory(oldFactory.getPlugin(), oldFactory.getPlugin().getServer(), injector.getReporter());
        ReflectionUtil.copyState(InjectionFactory.class, oldFactory, newFactory);
        injectorFactoryField.set(injector, newFactory);
    }

    @Override
    public void uninject() throws ReflectiveOperationException {
        if (injectorFactoryField != null && injector != null && oldFactory != null) {
            InjectionFactory currentFactory = (InjectionFactory) injectorFactoryField.get(injector);
            ReflectionUtil.copyState(InjectionFactory.class, currentFactory, oldFactory);
            injectorFactoryField.set(injector, oldFactory);
            oldFactory = null;
        }
    }

    static class HAProxyInjectorFactory extends InjectionFactory {
        public HAProxyInjectorFactory(Plugin plugin, Server server, ErrorReporter errorReporter ) {
            super(plugin, server, errorReporter);
        }

        @Override
        public @NotNull Injector fromChannel(Channel channel,
                                             ChannelListener listener,
                                             TemporaryPlayerFactory playerFactory) {
            ChannelPipeline pipeline = channel.pipeline();
            if (channel.isOpen() && pipeline.get("haproxy-detector") == null) {
                ChannelHandler networkManager = BukkitMain.getNetworkManager(pipeline);
                inject(pipeline, networkManager);
            }

            return super.fromChannel(channel, listener, playerFactory);
        }

        private static void inject(ChannelPipeline pipeline, ChannelHandler networkManager) {
            synchronized (networkManager) {
                HAProxyDetectorHandler detectorHandler = new HAProxyDetectorHandler(BukkitMain.logger,
                        new HAProxyMessageHandler(networkManager));
                try {
                    pipeline.addAfter("timeout", "haproxy-detector", detectorHandler);
                } catch (NoSuchElementException e) {
                    pipeline.addFirst("haproxy-detector", detectorHandler);
                }
            }
        }
    }
}
