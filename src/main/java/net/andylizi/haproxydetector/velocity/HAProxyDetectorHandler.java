package net.andylizi.haproxydetector.velocity;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.ProtocolDetectionResult;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;

import java.util.List;

@ChannelHandler.Sharable
public class HAProxyDetectorHandler extends ByteToMessageDecoder {
    {
        setSingleDecode(true);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        ProtocolDetectionResult<HAProxyProtocolVersion> detectionResult = HAProxyMessageDecoder.detectProtocol(in);
        switch (detectionResult.state()) {
            case NEEDS_MORE_DATA:
                return;
            case INVALID:
                ctx.pipeline().remove(this);
                break;
            case DETECTED:
            default:
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
