package com.heartbridge.utils;

import com.heartbridge.exception.ApplicationRunTimeException;

import java.io.InputStream;

/**
 * keystore manager
 * @author GavinCook
 * @since 1.0.0
 **/
public class KeyHolder {

    private String privateKeyLocation = "/private.keystore";

    private String privateKey;
    /**
     * get the private key
     * @return the private key
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
