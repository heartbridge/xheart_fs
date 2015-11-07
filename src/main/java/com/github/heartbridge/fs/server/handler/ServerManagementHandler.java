package com.github.heartbridge.fs.server.handler;

import com.github.heartbridge.fs.annotation.RequestMapping;
import com.github.heartbridge.fs.annotation.RequestParam;
import com.github.heartbridge.fs.server.FileServer;
import com.github.heartbridge.fs.server.Server;
import com.github.heartbridge.fs.server.ServerAware;
import com.github.heartbridge.fs.utils.FileUtils;
import com.github.heartbridge.fs.utils.KeyHolder;
import com.github.heartbridge.fs.utils.RSA;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author GavinCook
 * @since 1.0.0
 **/
public class ServerManagementHandler implements ServerAware {

    private Logger logger =  Logger.getLogger(ServerManagementHandler.class.getName());

    private Server server;

    private KeyHolder keyHolder = new KeyHolder();

    @RequestMapping(value = "/management")
    public void manageServer(ChannelHandlerContext ctx, @RequestParam("token") String encryptedToken,
                               @RequestParam("signal")String signal) {

        if (encryptedToken != null && encryptedToken.length() >= 128) {
            //信号解密
            String token = "";
            try {
                token = new String(RSA.decryptByPrivateKey(RSA.decryptBASE64(encryptedToken), keyHolder.getPrivateKey()));
            } catch (Exception e) {
                authorizeFailed(ctx);
            }
            LocalDateTime localDateTime = LocalDateTime.now();
            if (!token.equals(localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH")))) {//check the authorization
                authorizeFailed(ctx);
            }

            if ("shutdown".equals(signal)) {//shutdown the server
                logger.log(Level.INFO, "preparing to stop the server : {0}", server.getName());
                server.stop();
                ByteBuf buf = ctx.alloc().buffer();
                buf.writeBytes("shutdown successfully".getBytes());
                HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
                ChannelFuture future = ctx.channel().writeAndFlush(response);
                future.addListener(ChannelFutureListener.CLOSE);
                logger.log(Level.INFO, "stop the server [{0}] successfully", server.getName());
            } else if ("status".equals(signal)) {//get the server status
                ByteBuf buf = ctx.alloc().buffer();
                StringBuilder responseText = new StringBuilder();
                responseText.append("启动参数：").append(server.getStartParams()).append("<br/>");
                if (server instanceof FileServer) {
                    FileServer fileServer = (FileServer) server;
                    responseText.append("启动时间：").append(server.getStartTime()).append("<br/>");
                    responseText.append("监听端口：").append(fileServer.getPort()).append("<br/>");
                    responseText.append("文件基础路径：").append(fileServer.getBaseDir()).append("<br/>");
                    responseText.append("图片压缩阀值：").append(FileUtils.getReadableFileSize(fileServer.getCompressThreshold())).append("<br/>");
                }
                buf.writeBytes(responseText.toString().getBytes());
                HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
                response.headers().add(HttpHeaders.Names.CONTENT_TYPE, "text/html");
                ChannelFuture future = ctx.channel().writeAndFlush(response);
                future.addListener(ChannelFutureListener.CLOSE);
            }
        } else {
            authorizeFailed(ctx);
        }
    }


    /**
     * handle authorized failed, response with status:401
     * @param ctx the channel handler context
     */
    private void authorizeFailed(ChannelHandlerContext ctx){
        ByteBuf buf = ctx.alloc().buffer();
        buf.writeBytes("Authorize failed".getBytes());
        HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.UNAUTHORIZED,buf);
        ChannelFuture future = ctx.channel().writeAndFlush(response);
        future.addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void setServer(Server server) {
        this.server = server;
    }
}
