package com.heartbridge.fs.server.extension;

import io.netty.channel.ChannelHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private Logger logger = Logger.getLogger(getClass().getName());
    /**
     * the default config file path
     */
    private String confFilePath = "../conf/plugin.conf";

    /**
     * server parameters
     */
    private Map<String,Object> serverParams;

    public  List<ChannelHandler> load(){
        List<ChannelHandler> handlers = new ArrayList<>();

        File confFile = new File(confFilePath);

        if(!confFile.exists()) return handlers;
        try(BufferedReader reader = new BufferedReader(new FileReader(confFile))) {
            String className;

            while ((className = reader.readLine()) != null) {
                className = className.trim();
                if(className.startsWith("#")) continue;//ignore the comment lines

                Class<?> clazz = Class.forName(className);
                if (ChannelHandler.class.isAssignableFrom(clazz)) {
                    ChannelHandler handler = (ChannelHandler) clazz.getConstructor().newInstance();
                    try {
                        Method method = clazz.getDeclaredMethod("setServerParams", Map.class);
                        System.out.println(method.toGenericString());
                        if (!method.isAccessible()) {
                            method.setAccessible(true);
                        }
                        method.invoke(handler, serverParams);
                    }catch (Exception e){
                        e.printStackTrace();
                        logger.log(Level.WARNING, "There no method named setServerParams(Map<String,Object>) in {0} defined, ignore set server parameters",className);
                    }
                    handlers.add(handler);
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

    public Map<String, Object> getServerParams() {
        return serverParams;
    }

    public void setServerParams(Map<String, Object> serverParams) {
        this.serverParams = serverParams;
    }
}
