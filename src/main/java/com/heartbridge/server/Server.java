package com.heartbridge.server;

import java.time.LocalDateTime;

/**
 * @author GavinCook
 * @since 1.0.0
 **/
public interface Server {

    /**
     * 启动
     */
    void start();

    /**
     * 停止
     */
    void stop();

    /**
     * 获取启动参数
     * @return 启动参数
     */
    String getStartParams();

    /**
     * 获取服务器名字
     * @return 服务器名字
     */
    String getName();

    /**
     * 获取服务器启动时间
     * @return 服务器启动时间
     */
    LocalDateTime getStartTime();
}
