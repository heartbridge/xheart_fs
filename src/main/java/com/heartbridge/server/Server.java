package com.heartbridge.server;

/**
 * @author GavinCook
 * @date 2015/7/28 0028
 **/
public interface Server {
    void start();

    void stop();

    String getStartParams();

    String getName();
}
