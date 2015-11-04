package com.heartbridge.fs.server;

import java.util.Map;

/**
 * @author GavinCook
 * @since 1.0.0
 **/
public interface ServerStartParamsAware {

    String BASEDIR = "basedir", PORT = "port", THRESHOLD = "threshold";

    void setStartParams(Map<String,String> startParams);

}
