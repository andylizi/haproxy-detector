package net.andylizi.haproxydetector.bukkit;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.logging.Level;

import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.utility.MinecraftReflection;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import static net.andylizi.haproxydetector.ReflectionUtil.sneakyThrow;

@Sharable
class HAProxyMessageHandler extends SimpleChannelInboundHandler<HAProxyMessage> {
    private static volatile MethodHandle freeAddressSetter;
    private final MethodHandle addressSetter;

    public HAProxyMessageHandler(Object networkManager) {
        if (freeAddressSetter == null) {
            synchronized (HAProxyMessageHandler.class) {
                if (freeAddressSetter == null) {
                    Field f = FuzzyReflection.fromClass(MinecraftReflection.getNetworkManagerClass(), true)
                            .getFieldByType("socketAddress", SocketAddress.class);
                    try {
                        f.setAccessible(true);
                    } catch (Throwable ignored) {
                    }

                    try {
                        freeAddressSetter = MethodHandles.lookup().unreflectSetter(f);
                    } catch (IllegalAccessException e) {
                        sneakyThrow(e);
                        throw new AssertionError("dead code");
                    }
                }
            }
        }

        this.addressSetter = freeAddressSetter.bindTo(networkManager);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HAProxyMessage msg) throws Exception {
        SocketAddress realAddress = new InetSocketAddress(msg.sourceAddress(), msg.sourcePort());
        BukkitMain.logger.log(Level.INFO, "Set remote address via proxy {0} -> {1}", 
                new Object[] { ctx.channel().remoteAddress(), realAddress });
        try {
            addressSetter.invokeExact(realAddress);
        } catch (Throwable e) {
            sneakyThrow(e);
        }
    }
}
