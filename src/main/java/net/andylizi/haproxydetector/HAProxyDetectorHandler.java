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
package net.andylizi.haproxydetector;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.ProtocolDetectionResult;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;

public class HAProxyDetectorHandler extends ByteToMessageDecoder {
    @SuppressWarnings("unchecked")
    public static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    private final Logger logger;
    private final ChannelHandler haproxyHandler;

    {
        setSingleDecode(true);
    }

    public HAProxyDetectorHandler(Logger logger, ChannelHandler haproxyHandler) {
        this.logger = logger;
        this.haproxyHandler = haproxyHandler;
    }

    @Override
    public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            ProtocolDetectionResult<HAProxyProtocolVersion> detectionResult = HAProxyMessageDecoder.detectProtocol(in);
            switch (detectionResult.state()) {
                case NEEDS_MORE_DATA:
                    return;
                case INVALID:
                    ctx.pipeline().remove(this);
                    break;
                case DETECTED:
                default:
                    try {
                        ctx.pipeline().replace(this, "haproxy-decoder", new HAProxyMessageDecoder(true));
                        if (logger != null) logger.log(Level.INFO, "Proxy handler activated from {0}", ctx.channel().remoteAddress());
                    } catch (IllegalArgumentException ignored) {
                        // decoder already exists
                    }

                    if (haproxyHandler != null) {
                        ctx.pipeline().addAfter("haproxy-decoder", "haproxy-handler", haproxyHandler);
                    }
                    break;
            }
        }  catch (Throwable t) {  // stop BC from eating my exceptions
            if (logger != null)
                logger.log(Level.WARNING, "Exception while detecting proxy", t);
            else 
                t.printStackTrace();
        }
    }
}
