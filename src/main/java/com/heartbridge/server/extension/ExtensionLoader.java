package com.heartbridge.server.extension;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * the extension plugin loader, the file server allow custom handlers. Default, it will load the config from the file:
 * extension/plugin.conf, the extension folder is the same folder with the file server jar file. this path can be configured
 * with startup param <code>--plugin-conf</code>. In the plugin.conf can list the channel handler classes, one class one line.
 * <p>
 *     notice that: all the class config in the plugin.conf must be type of {@link ChannelHandler}
 * </p>
 * @author GavinCook
 * @since 1.0.0
 **/
public class ExtensionLoader {

    /**
     * the default config file path
     */
    private String confFilePath = "extension/plugin.conf";

    public  List<ChannelHandler> load(){
        List<ChannelHandler> handlers = new ArrayList<>();

        File confFile = new File(confFilePath);
        if(!confFile.exists()) return handlers;
        try(BufferedReader reader = new BufferedReader(new FileReader(confFile))) {
            String className;

            while ((className = reader.readLine()) != null) {
                Class<?> clazz = Class.forName(className);
                if (ChannelHandler.class.isAssignableFrom(clazz)) {
                    handlers.add((ChannelHandler) clazz.getConstructor().newInstance());
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return handlers;
    }

    public void setConfFilePath(String confFilePath) {
        this.confFilePath = confFilePath;
    }
}
