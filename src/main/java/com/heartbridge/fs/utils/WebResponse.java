package com.heartbridge.fs.utils;


/**
 * the web response wrapper
 *
 * @author GavinCook
 * @since 1.0.0
 */
public class WebResponse {

    /**
     * 执行状态是否成功
     */
    private boolean success = true;

    /**
     * 返回的结果集
     */
    private Object result;


    public WebResponse() {
    }

    public static WebResponse build() {
        return new WebResponse();
    }

    public static WebResponse fail(Object result) {
        WebResponse webResponse = new WebResponse();
        webResponse.setSuccess(false);
        webResponse.setResult(result);
        return webResponse;
    }

    public static WebResponse success(Object result) {
        WebResponse webResponse = new WebResponse();
        webResponse.setSuccess(true);
        webResponse.setResult(result);
        return webResponse;
    }

    public static WebResponse success() {
        WebResponse webResponse = new WebResponse();
        webResponse.setSuccess(true);
        return webResponse;
    }

    public static WebResponse fail() {
        WebResponse webResponse = new WebResponse();
        webResponse.setSuccess(false);
        return webResponse;
    }

    public WebResponse setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public WebResponse setResult(Object result) {
        this.result = result;
        return this;
    }

    public boolean isSuccess() {
        return success;
    }

    public Object getResult() {
        return result;
    }

}
