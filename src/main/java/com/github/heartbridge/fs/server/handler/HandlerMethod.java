package com.github.heartbridge.fs.server.handler;

import java.lang.reflect.Method;

/**
 * @author GavinCook
 * @since 1.0.0
 **/
public class HandlerMethod {

    private Object bean;

    private Method method;

    public HandlerMethod(Object bean, Method method) {
        this.bean = bean;
        this.method = method;
    }

    public Object getBean() {
        return bean;
    }

    public Method getMethod() {
        return method;
    }
}
