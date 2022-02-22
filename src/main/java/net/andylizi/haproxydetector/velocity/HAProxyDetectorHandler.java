package net.andylizi.haproxydetector.velocity;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.ProtocolDetectionResult;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;
import net.andylizi.haproxydetector.ConnectionStats;
import net.andylizi.haproxydetector.ProxyWhitelist;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.net.SocketAddress;
import java.util.List;

public class HAProxyDetectorHandler extends ByteToMessageDecoder {
    {
        setSingleDecode(true);
    }

    private final Logger logger;

    public HAProxyDetectorHandler(@NotNull Logger logger) {
        this.logger = logger;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        SocketAddress addr = ctx.channel().remoteAddress();
        ProtocolDetectionResult<HAProxyProtocolVersion> detectionResult = HAProxyMessageDecoder.detectProtocol(in);
        switch (detectionResult.state()) {
            case NEEDS_MORE_DATA:
                return;
            case INVALID:
                ConnectionStats.trackConnection(addr, false);
                ctx.pipeline().remove(this);
                break;
            case DETECTED:
            default:
                if (!ProxyWhitelist.check(addr)) {
                    try {
                        ProxyWhitelist.getWarningFor(addr).ifPresent(logger::info);
                    } finally {
                        ctx.close();
                    }
                    return;
                }

                ConnectionStats.trackConnection(addr, true);
                ChannelPipeline pipeline = ctx.pipeline();
                try {
                    pipeline.replace(this, "haproxy-decoder", new HAProxyMessageDecoder());
                } catch (IllegalArgumentException ignored) {
                    pipeline.remove(this);
                }
                break;
        }
    }
}
