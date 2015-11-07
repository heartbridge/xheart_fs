package com.github.heartbridge.fs.server.handler;

import com.github.heartbridge.fs.utils.IPTable;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ip filter, if the ip blocked, will response with code 403
 * @author GavinCook
 * @since 1.0.0
 **/
public class IPFilter extends SimpleChannelInboundHandler<ByteBuf> {

    private IPTable ipTable;

    private Logger logger = Logger.getLogger(IPFilter.class.getName());

    public IPFilter(IPTable ipTable){
        super(false);
        this.ipTable = ipTable;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        byte[] socketAddressBytes = socketAddress.getAddress().getAddress();
        //get the remote ip, sometimes it is negative, so need  and with 0XFF
        String ip = (socketAddressBytes[0]&0XFF)+"."+(socketAddressBytes[1]&0XFF)
                +"."+(socketAddressBytes[2]&0XFF)+"."+(socketAddressBytes[3]&0XFF);

        if(ipTable.isBlocked(ip)){
            logger.log(Level.INFO, "ip {0} is blocked by ip filter",ip);
            ByteBuf buf = ctx.alloc().buffer();
            buf.writeBytes("you are forbidden to access current server.".getBytes());
            HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN,buf);
            ChannelFuture future = ctx.channel().writeAndFlush(response);
            future.addListener(ChannelFutureListener.CLOSE);
        }else{
            ctx.fireChannelRead(msg);
        }
    }
}
