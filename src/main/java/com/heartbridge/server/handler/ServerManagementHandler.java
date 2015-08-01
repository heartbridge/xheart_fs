package com.heartbridge.server.handler;

import com.heartbridge.server.FileServer;
import com.heartbridge.utils.FileUtils;
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
 * @since 1.0.0
 **/
public class ServerManagementHandler extends SimpleChannelInboundHandler<HttpObject> {

    private Logger logger =  Logger.getLogger(ServerManagementHandler.class.getName());

    private Server server;

    private KeyHolder keyHolder;

    public ServerManagementHandler(Server server, KeyHolder keyHolder){
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
                    String token;
                    try {
                        token = new String(RSA.decryptByPrivateKey(RSA.decryptBASE64(encryptedToken), keyHolder.getPrivateKey()));
                    }catch (Exception e){
                        authorizeFailed(ctx);
                        return;
                    }
                    String signal = uriAttributes.getOrDefault("signal", new ArrayList<>()).get(0);
                    LocalDateTime localDateTime = LocalDateTime.now();
                    if(!token.equals(localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH")))){//授权检查
                        authorizeFailed(ctx);
                        return;
                    }

                    if("shutdown".equals(signal)){//关闭服务器
                        logger.log(Level.INFO, "preparing to stop the server : {0}", server.getName());
                        server.stop();
                        ByteBuf buf = ctx.alloc().buffer();
                        buf.writeBytes("shutdown successfully".getBytes());
                        HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK,buf);
                        ChannelFuture future = ctx.channel().writeAndFlush(response);
                        future.addListener(ChannelFutureListener.CLOSE);
                        logger.log(Level.INFO, "stop the server [{0}] successfully", server.getName());
                    }else if("status".equals(signal)){//查看服务器窗台
                        ByteBuf buf = ctx.alloc().buffer();
                        StringBuilder responseText = new StringBuilder();
                        responseText.append("启动参数：").append(server.getStartParams()).append("<br/>");
                        if(server instanceof FileServer) {
                            FileServer fileServer = (FileServer) server;
                            responseText.append("启动时间：").append(server.getStartTime()).append("<br/>");
                            responseText.append("监听端口：").append(fileServer.getPort()).append("<br/>");
                            responseText.append("文件基础路径：").append(fileServer.getBaseDir()).append("<br/>");
                            responseText.append("图片压缩阀值：").append(FileUtils.getReadableFileSize(fileServer.getCompressThreshold())).append("<br/>");
                        }
                        buf.writeBytes(responseText.toString().getBytes());
                        HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK,buf);
                        response.headers().add(HttpHeaders.Names.CONTENT_TYPE,"text/html");
                        ChannelFuture future = ctx.channel().writeAndFlush(response);
                        future.addListener(ChannelFutureListener.CLOSE);
                    }
                }else{
                    authorizeFailed(ctx);
                }
            }else{
                ctx.fireChannelRead(msg);
            }
        }else{
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * 授权失败
     * @param ctx 通道处理器上下文
     */
    private void authorizeFailed(ChannelHandlerContext ctx){
        ByteBuf buf = ctx.alloc().buffer();
        buf.writeBytes("Authorize failed".getBytes());
        HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.UNAUTHORIZED,buf);
        ChannelFuture future = ctx.channel().writeAndFlush(response);
        future.addListener(ChannelFutureListener.CLOSE);
    }
}
