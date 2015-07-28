package com.heartbridge.utils;

import com.heartbridge.exception.ApplicationRunTimeException;

import java.io.InputStream;

/**
 * 密钥管理器
 * @author GavinCook
 * @date 2015/7/28 0028
 **/
public class KeyHolder {

    private String privateKeyLocation = "/private.keystore";

    private String privateKey;
    /**
     * 获取私钥
     * @return
     */
    public String getPrivateKey(){
        if(this.privateKey != null){
            return this.privateKey;
        }
        try(InputStream stream = KeyHolder.class.getResourceAsStream(privateKeyLocation)) {
            byte[] privateKey = new byte[stream.available()];
            stream.read(privateKey);
            this.privateKey = new String(privateKey);
            return this.privateKey;
        } catch (Exception e) {
            e.printStackTrace();
            throw new ApplicationRunTimeException(e);
        }
    }

}
