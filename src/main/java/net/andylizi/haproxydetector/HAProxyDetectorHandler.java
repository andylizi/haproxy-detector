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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.ProtocolDetectionResult;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion;

public class HAProxyDetectorHandler extends ByteToMessageDecoder {
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
                    SocketAddress addr = ctx.channel().remoteAddress();
                    if (!ProxyWhitelist.check(addr)) {
                        try {
                            ProxyWhitelist.getWarningFor(addr).ifPresent(logger::info);
                        } finally {
                            ctx.close();
                        }
                        return;
                    }

                    ChannelPipeline pipeline = ctx.pipeline();
                    try {
                        pipeline.replace(this, "haproxy-decoder", new HAProxyMessageDecoder());
                    } catch (IllegalArgumentException ignored) {
                        pipeline.remove(this); // decoder already exists
                    }

                    if (haproxyHandler != null) {
                        try {
                            pipeline.addAfter("haproxy-decoder", "haproxy-handler", haproxyHandler);
                        } catch (IllegalArgumentException ignored) {
                        } catch (NoSuchElementException e) {  // Not sure why but...
                            if (pipeline.get("timeout") != null) {
                                pipeline.addAfter("timeout", "haproxy-decoder", new HAProxyMessageDecoder());
                                pipeline.addAfter("timeout", "haproxy-handler", haproxyHandler);
                            } else {
                                pipeline.addFirst("haproxy-handler", haproxyHandler);
                                pipeline.addFirst("haproxy-decoder", new HAProxyMessageDecoder());
                            }
                        }
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
