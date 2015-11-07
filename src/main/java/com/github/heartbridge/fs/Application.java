package com.github.heartbridge.fs;

import com.github.heartbridge.fs.server.FileServer;
import com.github.heartbridge.fs.server.Server;
import com.github.heartbridge.fs.server.ServerStartParamsAware;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author GavinCook
 * @since 1.0.0
 **/
public class Application {

    private static Logger log = Logger.getLogger(FileServer.class.getName());

    public static void main(String[] args) throws InterruptedException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        Map<String,String> m = new HashMap<>();
        int length = args.length;
        if(length >= 2){
            if(length%2!=0){
                length--;//ignore the last one, when parameters length is odd
            }
            for(int i=0;i<length;){
                String key = args[i++];
                if(key.startsWith("--")){
                    String value = args[i++];
                    m.put(key.substring(2).toLowerCase(), value);
                }else{
                    i++;
                    log.log(Level.WARNING,"the parameter {0} is invalid,will ignore.",key);
                }
            }
        }

        Class<Server> serverClass = (Class<Server>) Class.forName("com.github.heartbridge.fs.server.FileServer");

        Server server = serverClass.newInstance();

        if(server instanceof ServerStartParamsAware){
            ((ServerStartParamsAware) server).setStartParams(m);
        }

        server.start();
    }
}
