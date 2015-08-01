package com.heartbridge.server;

import java.time.LocalDateTime;

/**
 * @author GavinCook
 * @since 1.0.0
 **/
public interface Server {

    /**
     * start server
     */
    void start();

    /**
     * stop server
     */
    void stop();

    /**
     * get the server's start parameters
     * @return server's start parameters
     */
    String getStartParams();

    /**
     * get the server's name
     * @return server's name
     */
    String getName();

    /**
     * get the server's start time
     * @return server's start time
     */
    LocalDateTime getStartTime();
}
