package com.heartbridge.server;

import com.heartbridge.utils.FileUtils;
import com.heartbridge.server.handler.HttpUploadServerHandler;
import com.heartbridge.utils.KeyHolder;
import com.heartbridge.server.handler.ServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * 文件服务器
 * @author GavinCook
 * @date 2015/7/25 0025
 **/
public class FileServer implements Server{

    private int port = 8585;

    //文件存储的基础位置
    private String baseDir;

    //压缩阀值
    private long compressThreshold;

    private static Logger log = Logger.getLogger(FileServer.class.getName());

    private ChannelFuture channelFuture;

    private KeyHolder keyHolder = new KeyHolder();

    @Override
    public void start(){
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workGroup = new NioEventLoopGroup();
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup, workGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new HttpRequestDecoder());
                        ch.pipeline().addLast(new HttpResponseEncoder());
                        ch.pipeline().addLast(new ServerHandler(FileServer.this, keyHolder));
                        ch.pipeline().addLast(new HttpUploadServerHandler(baseDir, compressThreshold));
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        try {
            channelFuture = serverBootstrap.bind(port).sync();
            log.log(Level.INFO, "file server started success");
            log.log(Level.INFO, "file server listen on {0}, and use \"{1}\" as base directory, compress threshold is {2}",
                    new String[]{port + "", baseDir, FileUtils.getReadableFileSize(compressThreshold)});
            channelFuture.channel().closeFuture().sync();
        }catch (InterruptedException e){
            e.printStackTrace();
            System.exit(-1);
        }finally {
            workGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    @Override
    public void stop() {
        channelFuture.channel().close();
    }

    @Override
    public String getStartParams() {
        return null;
    }

    @Override
    public String getName() {
        return "File Server";
    }


    public static void main(String[] args) throws InterruptedException {
        FileServer fileServer = new FileServer();
        Map<String,String> m = new HashMap<>();
        int length = args.length;
        if(length >= 2){
            if(length%2!=0){
                length--;//当传入的参数不是偶数个数，忽略最后一个
            }
            for(int i=0;i<length;){
                String key = args[i++];
                if(key.startsWith("--")){
                    m.put(key.substring(2).toLowerCase(), args[i++]);
                }else{
                    i++;
                    log.log(Level.WARNING,"the parameter {} is invalid,will ignore.",key);
                }

            }
        }

        fileServer.port = Integer.valueOf( m.getOrDefault("port","8585") );
        fileServer.baseDir = m.getOrDefault("basedir","/files/");
        fileServer.compressThreshold = Long.valueOf(m.getOrDefault("threshold","1048576"));//默认压缩阀值1m
        fileServer.start();
    }
}
