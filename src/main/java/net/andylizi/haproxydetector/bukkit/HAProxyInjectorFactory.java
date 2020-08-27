package net.andylizi.haproxydetector.bukkit;

import java.util.Map;
import java.util.NoSuchElementException;

import com.comphenix.protocol.injector.netty.ChannelListener;
import com.comphenix.protocol.injector.netty.InjectionFactory;
import com.comphenix.protocol.injector.netty.Injector;
import com.comphenix.protocol.injector.server.TemporaryPlayerFactory;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.utility.MinecraftVersion;

import org.bukkit.plugin.Plugin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import net.andylizi.haproxydetector.HAProxyDetectorHandler;

class HAProxyInjectorFactory extends InjectionFactory {
    public HAProxyInjectorFactory(Plugin plugin) {
        super(plugin);
    }
    
    @Override
    public Injector fromChannel(Channel channel, ChannelListener listener, TemporaryPlayerFactory playerFactory) {
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get("haproxy-detector") == null) {
            Class<?> networkManagerClass = MinecraftReflection.getNetworkManagerClass();
            Object networkManager = null;
            for (Map.Entry<String, ChannelHandler> entry : pipeline) {
                if (networkManagerClass.isAssignableFrom(entry.getValue().getClass())) {
                    networkManager = entry.getValue();
                    break;
                }
            }

            if (networkManager == null) {
                throw new IllegalArgumentException("NetworkManager not found in channel pipeline " + pipeline.names());
            }

            // Copied from PL: For some reason it needs to be delayed on 1.12, but the delay breaks 1.11 and below
            if (MinecraftVersion.getCurrentVersion().getMinor() >= 12) {
                final Object tmp = networkManager;  // bypass 'final' check
                channel.eventLoop().submit(() -> inject(pipeline, tmp));
            } else {
                inject(pipeline, networkManager);
            }
        }

        return super.fromChannel(channel, listener, playerFactory);
    }

    private void inject(ChannelPipeline pipeline, Object networkManager) {
        synchronized (networkManager) {
            HAProxyDetectorHandler detectorHandler = new HAProxyDetectorHandler(BukkitMain.logger, new HAProxyMessageHandler(networkManager));
            try {
                pipeline.addAfter("timeout", "haproxy-detector", detectorHandler);
            } catch (NoSuchElementException e) {
                pipeline.addFirst("haproxy-detector", detectorHandler);
            }
        }
    }
}
