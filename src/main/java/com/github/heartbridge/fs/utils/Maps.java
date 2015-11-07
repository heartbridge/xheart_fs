package com.github.heartbridge.fs.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Map tools
 * @author GavinCook
 * @since 1.0.0
 */
public class Maps {

    /**
     * construct Map based on the arguments, the even for key, odd for value. An empty Map will return if arguments is null
     */
    public static Map<String,Object> mapItSO(Object... params){
        Map<String,Object> m = new HashMap<>();
        int length = params == null ? 0:params.length;
        if(length<2){
            return m;
        }else{
            if(length%2!=0){
                length--;//ignore the last one when pass odd parameters
            }
            for(int i=0;i<length;){
                m.put(TypeConvertor.toString(params[i++]), params[i++]);
            }
        }
        return m;
    }
    
}
