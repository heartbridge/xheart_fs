package com.heartbridge.server.handler;

import com.heartbridge.utils.KeyHolder;
import com.heartbridge.utils.RSA;
import com.heartbridge.server.Server;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 服务器管理
 * @author GavinCook
 * @date 2015/7/28 0028
 **/
public class ServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    private Logger logger =  Logger.getLogger(ServerHandler.class.getName());

    private Server server;

    private KeyHolder keyHolder;

    public ServerHandler(Server server, KeyHolder keyHolder){
        super(false);
        this.server = server;
        this.keyHolder = keyHolder;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            if(request.getUri().startsWith("/management")){//服务器关闭
                QueryStringDecoder decoderQuery = new QueryStringDecoder(request.getUri());
                Map<String, List<String>> uriAttributes = decoderQuery.parameters();
                List<String> tokens = uriAttributes.getOrDefault("token", new ArrayList<>());
                String encryptedToken = tokens.get(0);
                if(encryptedToken != null && encryptedToken.length() >= 128){
                    //信号解密
                    String token = new String(RSA.decryptByPrivateKey(RSA.decryptBASE64(encryptedToken), keyHolder.getPrivateKey()));
                    String signal = uriAttributes.getOrDefault("signal", new ArrayList<>()).get(0);
                    LocalDateTime localDateTime = LocalDateTime.now();
                    if(!token.equals(localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH")))){//授权检查
                        ByteBuf buf = ctx.alloc().buffer();
                        buf.writeBytes("Authorize failed".getBytes());
                        HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.UNAUTHORIZED,buf);
                        ChannelFuture future = ctx.channel().writeAndFlush(response);
                        future.addListener(ChannelFutureListener.CLOSE);
                        return;
                    }
                    //关闭服务器
                    if("shutdown".equals(signal)){
                        logger.log(Level.INFO, "preparing to stop the server : {0}", server.getName());
                        server.stop();
                        ByteBuf buf = ctx.alloc().buffer();
                        buf.writeBytes("shutdown successfully".getBytes());
                        HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK,buf);
                        ChannelFuture future = ctx.channel().writeAndFlush(response);
                        future.addListener(ChannelFutureListener.CLOSE);
                        logger.log(Level.INFO, "stop the server [{0}] successfully", server.getName());
                    }
                }
            }else{
                ctx.fireChannelRead(msg);
            }
        }else{
            ctx.fireChannelRead(msg);
        }
    }

}
