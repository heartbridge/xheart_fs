package com.heartbridge.server;

import com.heartbridge.server.handler.IPFilter;
import com.heartbridge.utils.FileUtils;
import com.heartbridge.server.handler.HttpUploadServerHandler;
import com.heartbridge.utils.IPTable;
import com.heartbridge.utils.KeyHolder;
import com.heartbridge.server.handler.ServerManagementHandler;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * 文件服务器
 * @author GavinCook
 * @since  1.0.0
 **/
public class FileServer implements Server{

    private int port = 8585;

    //文件存储的基础位置
    private String baseDir;

    //压缩阀值
    private long compressThreshold;

    //启动参数
    private String startParams ;

    //启动时间点
    private LocalDateTime startTime = LocalDateTime.now();

    private static Logger log = Logger.getLogger(FileServer.class.getName());

    private ChannelFuture channelFuture;

    private KeyHolder keyHolder = new KeyHolder();

    //ip table for filter ip
    private IPTable ipTable = new IPTable();

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
                        ch.pipeline().addLast(new IPFilter(FileServer.this.ipTable));
                        ch.pipeline().addLast(new HttpRequestDecoder());
                        ch.pipeline().addLast(new HttpResponseEncoder());
                        ch.pipeline().addLast(new ServerManagementHandler(FileServer.this, keyHolder));
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
        return this.startParams;
    }

    @Override
    public String getName() {
        return "File Server";
    }

    public int getPort() {
        return port;
    }

    public String getBaseDir() {
        return baseDir;
    }

    public long getCompressThreshold() {
        return compressThreshold;
    }

    @Override
    public LocalDateTime getStartTime() {
        return this.startTime;
    }


    public static void main(String[] args) throws InterruptedException {
        FileServer fileServer = new FileServer();
        StringBuilder startParam = new StringBuilder();
        String space = " ";
        Map<String,String> m = new HashMap<>();
        int length = args.length;
        if(length >= 2){
            if(length%2!=0){
                length--;//当传入的参数不是偶数个数，忽略最后一个
            }
            for(int i=0;i<length;){
                String key = args[i++];
                startParam.append(key).append(space);
                if(key.startsWith("--")){
                    String value = args[i++];
                    startParam.append(value).append(space);
                    m.put(key.substring(2).toLowerCase(), value);
                }else{
                    i++;
                    log.log(Level.WARNING,"the parameter {0} is invalid,will ignore.",key);
                }
            }
        }

        fileServer.startParams = startParam.toString();
        fileServer.port = Integer.valueOf(m.getOrDefault("port", "8585"));
        fileServer.baseDir = m.getOrDefault("basedir", "/files/");
        fileServer.compressThreshold = Long.valueOf(m.getOrDefault("threshold", "1048576"));//默认压缩阀值1m

        //handle the ip rule, see IPTable for detail
        String allowRegex = m.get("allow");
        String denyRegex = m.get("deny");
        if(allowRegex != null){
            fileServer.ipTable.allow(allowRegex);
        }

        if(denyRegex != null){
            fileServer.ipTable.deny(denyRegex);
        }

        fileServer.start();
    }
}
