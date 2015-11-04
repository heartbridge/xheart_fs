package com.heartbridge.fs.utils;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * @author GavinCook
 * @since 1.0.0
 **/
public class Parameters {

    /**
     * get the generic type for parameter
     * @param parameter the parameter
     * @return the generic type array
     */
    public static Type[] getActualGenericType(Parameter parameter){
        Type parameterType = parameter.getParameterizedType();
        if(parameterType instanceof ParameterizedType){
            return ((ParameterizedType) parameterType).getActualTypeArguments();
        }else{
            return new Type[0];
        }
    }

}
