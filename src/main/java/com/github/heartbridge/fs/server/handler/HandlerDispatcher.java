package com.github.heartbridge.fs.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.heartbridge.fs.annotation.Handler;
import com.github.heartbridge.fs.annotation.RequestMapping;
import com.github.heartbridge.fs.annotation.RequestParam;
import com.github.heartbridge.fs.exception.ApplicationRunTimeException;
import com.github.heartbridge.fs.exception.NoMatchedMethodFoundException;
import com.github.heartbridge.fs.server.Server;
import com.github.heartbridge.fs.server.ServerAware;
import com.github.heartbridge.fs.server.ServerStartParamsAware;
import com.github.heartbridge.fs.utils.ClassScanner;
import com.github.heartbridge.fs.utils.Parameters;
import com.github.heartbridge.fs.utils.TypeConvertor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.multipart.*;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;

/**
 * @author GavinCook
 * @since 1.0.0
 **/
@ChannelHandler.Sharable
public class HandlerDispatcher extends SimpleChannelInboundHandler<HttpObject> {

    private ObjectMapper mapper = new ObjectMapper();

    private Logger logger = Logger.getLogger(getClass().getName());

    private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE); // Disk if size exceed

    private Map<String,List<RequestMapping>> urlMap = new HashMap<>();

    // mapping for request mapping and the mapped method
    private Map<RequestMapping,HandlerMethod> handlerMethods = new HashMap<>();

    private Map<Class,Object> singletonContainer = new HashMap<>();

    private Map<String,String> serverStartParams = new HashMap<>();

    private ThreadLocal<Map<String,String[]>> paramsHolder = new ThreadLocal<>();

    private ThreadLocal<Map<String,List<FileUpload>>> files = new ThreadLocal<>();

    private ThreadLocal<HttpRequest> requestHolder = new ThreadLocal<>();

    private ThreadLocal<HttpPostRequestDecoder> decoderHolder = new ThreadLocal<>();

    private Server server;

    public HandlerDispatcher(){}

    static {
        DiskFileUpload.deleteOnExitTemporaryFile = false;
        DiskFileUpload.baseDirectory = null;
        DiskAttribute.deleteOnExitTemporaryFile = false;
        DiskAttribute.baseDirectory = null;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        super.channelRegistered(ctx);
        paramsHolder.set(new HashMap<>());
        files.set(new HashMap<>());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        cause.printStackTrace();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        Channel channel = ctx.channel();
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            requestHolder.set(request);
            Map<String,String[]> params = this.paramsHolder.get();

            //handle the url parameter, store them into the <code>paramsMap</code>
            QueryStringDecoder decoderQuery = new QueryStringDecoder(request.getUri());
            Map<String, List<String>> uriAttributes = decoderQuery.parameters();
            for (Map.Entry<String, List<String>> attr: uriAttributes.entrySet()) {
                List<String> attrVal = attr.getValue();
                String[] arrayAttrVal = attr.getValue().toArray(new String[attrVal.size()]);
                params.put(attr.getKey(),arrayAttrVal);
            }

            decoderHolder.set(new HttpPostRequestDecoder(factory, request));
        }

        HttpPostRequestDecoder decoder = decoderHolder.get();
        if(decoder != null) {
            try {
                if (msg instanceof HttpContent) {
                    // New chunk is received
                    HttpContent chunk = (HttpContent) msg;
                    decoder.offer(chunk);
                    handlePostMethod(decoder);
                    if (chunk instanceof LastHttpContent) {
                        invokeAndResponse(ctx);
                        requestHolder.set(null);
                        decoder.destroy();
                    }
                }

            } catch (HttpPostRequestDecoder.ErrorDataDecoderException e1) {
                e1.printStackTrace();
                json(channel, requestHolder.get(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
            } catch (HttpPostRequestDecoder.IncompatibleDataDecoderException e1) {
                json(channel, requestHolder.get(), HttpResponseStatus.INTERNAL_SERVER_ERROR, e1.getMessage());
            }
        }
    }

    protected void invokeAndResponse(ChannelHandlerContext ctx) throws InvocationTargetException, IllegalAccessException, JsonProcessingException {
        HttpRequest request = requestHolder.get();
        try {

            HandlerMethod handlerMethod = lookupHandlerMethod(request);
            Object result = invokeHandlerMethod(ctx, handlerMethod);
            if(result instanceof String){
                json(ctx.channel(), request, HttpResponseStatus.OK, (String) result);
            }else if(result instanceof Void || result == null){
                json(ctx.channel(), request, HttpResponseStatus.OK);
            }else{
                json(ctx.channel(), request, HttpResponseStatus.OK, mapper.writeValueAsString(result));
            }
        }catch (NoMatchedMethodFoundException e){
            logger.log(Level.WARNING, e.getMessage());
            json(ctx.channel(), request, HttpResponseStatus.NOT_FOUND);
        }finally {
            ctx.channel().close();
        }
    }

    public void setServerStartParams(Map<String,String> serverStartParams){
        this.serverStartParams = serverStartParams;

        //register system handler
        Arrays.asList(FileUploadHandler.class.getMethods()).stream().filter(m -> m.isAnnotationPresent(RequestMapping.class)).forEach(this::registerHandlerMethod);
        Arrays.asList(ServerManagementHandler.class.getMethods()).stream().filter(m -> m.isAnnotationPresent(RequestMapping.class)).forEach(this::registerHandlerMethod);
    }

    public void setServer(Server server){
        this.server = server;
    }

    /**
     * discover the handler methods and register them
     * @param packageName multiple package names supported, use comma split
     */
    public void scanAndRegisterHandlerMethods(String packageName){
        if(packageName == null) return;

        String[] packages = packageName.split(",");
        Set<String> packagesSet = new HashSet<>();
        Collections.addAll(packagesSet, packages);

        packagesSet.forEach(pack -> {
            logger.log(Level.INFO, "Scanning");
            Set<Class> classes = ClassScanner.getClasses(packageName);
            classes.stream().filter(c -> c.isAnnotationPresent(Handler.class)).forEach(c -> {
                Arrays.asList(c.getMethods()).stream().filter(m -> m.isAnnotationPresent(RequestMapping.class)).forEach(this::registerHandlerMethod);
            });
        });
    }

    /**
     * lookup the best matched method for http request
     * @param request
     * @return
     */
    public HandlerMethod lookupHandlerMethod(HttpRequest request){
        String lookupPath = request.getUri();
        int index = lookupPath.indexOf("?");
        if(index > 0){
            lookupPath = lookupPath.substring(0,index);
        }

        HashSet<HandlerMethod> matchMethods = new HashSet<>();
        urlMap.keySet().stream().filter(lookupPath::equals).forEach(url -> {
                    urlMap.get(url).stream().filter(mapping -> matchRequestMethod(request, mapping))
                            .forEach(mapping -> matchMethods.add(handlerMethods.get(mapping)));
                }
        );

        //if there no url matched the url directly, use the url as lookPath prefix
        if(matchMethods.isEmpty()) {
            urlMap.keySet().stream().filter(lookupPath::matches).forEach(url ->
                            urlMap.get(url).stream().filter(mapping -> matchRequestMethod(request, mapping))
                                    .forEach(mapping -> matchMethods.add(handlerMethods.get(mapping)))
            );
        }

        if(matchMethods.isEmpty()){
            throw new NoMatchedMethodFoundException("No handler method matched the url : ["+request.getMethod()+"]"+lookupPath);
        }

        HandlerMethod method = matchMethods.iterator().next();
        System.out.println("Found "+method.getMethod().getName()+" for :"+lookupPath);
        return method;
    }

    /**
     * create handlerMethod for method
     * @param method
     * @return
     */
    private HandlerMethod createHandlerMethod(Method method){
        Class clazz = method.getDeclaringClass();
        Object bean = singletonContainer.get(clazz);
        if(bean == null){
            try {
                bean = clazz.newInstance();
                if(bean instanceof ServerStartParamsAware){
                    ((ServerStartParamsAware)bean).setStartParams(serverStartParams);
                }
                if(bean instanceof ServerAware){
                    ((ServerAware)bean).setServer(server);
                }
                singletonContainer.put(clazz, bean);
            } catch (Exception e) {
                throw new ApplicationRunTimeException(e);
            }
        }
        return new HandlerMethod(bean,method);
    }
    /**
     * check the the mapping is match the request method
     * @param request http request
     * @param mapping the request mapping
     * @return true if matched
     */
    private boolean matchRequestMethod(HttpRequest request, RequestMapping mapping) {
        return mapping.method().length == 0 ||
                Arrays.asList(mapping.method()).stream().filter(m -> request.getMethod().name().equals(m.name())).count() > 0;
    }

    /**
     * register the handler method
     * @param method the handler method
     */
    private void registerHandlerMethod(Method method){
        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
        handlerMethods.put(requestMapping, createHandlerMethod(method));

        String[] mappingUrls = requestMapping.value();
        for (String mappingUrl : mappingUrls) {
            addUrlMapping(mappingUrl, requestMapping);
            logger.log(Level.INFO, "Mapping "+method.getDeclaringClass().getName()+"."+method.getName()+" to url :"+mappingUrl);
        }
    }
    /**
     * add url mapping
     * @param url request mapping url
     * @param mapping the request mapping
     */
    private void addUrlMapping(String url, RequestMapping mapping){
        List<RequestMapping> mappingList = urlMap.get(url);
        if(mappingList != null){
            mappingList.add(mapping);
        }else{
            mappingList = new ArrayList<>();
            mappingList.add(mapping);
            urlMap.put(url, mappingList);
        }
    }

    /**
     * invoke the handler method
     * @param handlerMethod
     * @return
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    private Object invokeHandlerMethod(ChannelHandlerContext ctx, HandlerMethod handlerMethod) throws InvocationTargetException, IllegalAccessException {
        Method method = handlerMethod.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] values = new Object[parameters.length];

        for(int p = 0, l = parameters.length; p < l; p++){
            values[p] = resolveParameterValue(ctx, parameters[p]);
        }

        return method.invoke(handlerMethod.getBean(), values);
    }

    /**
     * resolve the parameter value
     * @param parameter
     * @return
     */
    private Object resolveParameterValue(ChannelHandlerContext ctx, Parameter parameter){
        Class parameterType = parameter.getType();
        if(ChannelHandlerContext.class == parameterType){
            return ctx;
        }
        if(HttpRequest.class == parameterType){
            return requestHolder.get();
        }
        RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
        if(requestParam != null){
            String[] defaultValue = new String[]{RequestParam.DEFAULT_NONE.equals(requestParam.defaultValue())? null : requestParam.defaultValue()};
            String paramName = requestParam.value();
            String value = paramsHolder.get().getOrDefault(paramName, defaultValue)[0];

            if(String[].class == parameterType){
                return paramsHolder.get().getOrDefault(paramName, defaultValue);
            }else if(int[].class == parameterType || Integer[].class == parameterType){
                return TypeConvertor.toIntegerArray(paramsHolder.get().getOrDefault(paramName, defaultValue));
            }else if(long[].class == parameterType || Long[].class == parameterType){
                return TypeConvertor.toLongArray(paramsHolder.get().getOrDefault(paramName, defaultValue));
            }else if(double[].class ==  parameterType || Double[].class == parameterType){
                return TypeConvertor.toDoubleArray(paramsHolder.get().getOrDefault(paramName, defaultValue));
            }else if(float[].class == parameterType || Float[].class == parameterType){
                return TypeConvertor.toFloatArray(paramsHolder.get().getOrDefault(paramName, defaultValue));
            }else if(boolean[].class == parameterType || Boolean[].class == parameterType){
                return TypeConvertor.toBooleanArray(paramsHolder.get().getOrDefault(paramName, defaultValue));
            }else if(FileUpload[].class == parameterType){
                List<FileUpload> fileUploads = files.get().get(paramName);
                if(fileUploads != null && !fileUploads.isEmpty()) {
                    return (FileUpload[])(fileUploads.toArray(new MixedFileUpload[fileUploads.size()]));
                }else {
                    return null;
                }
            }else if(File[].class == parameterType){
                List<FileUpload> fileUploads = files.get().get(paramName);
                if(fileUploads != null && fileUploads.isEmpty()) {
                    List<File> files = fileUploads.stream().map(f -> {
                        try {
                            return f.getFile();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }).collect(Collectors.toList());
                    return files.toArray(new File[files.size()]);
                }else {
                    return null;
                }
            }else if(Collection.class.isAssignableFrom(parameterType)){// collection
                Object[] arrayParams = paramsHolder.get().getOrDefault(paramName, defaultValue);
                List<FileUpload> fileParams = files.get().get(paramName);
                Type[] genericTypes = Parameters.getActualGenericType(parameter);
                if(genericTypes.length > 0){
                    Class clazz = null;
                    try {
                        clazz = Class.forName(genericTypes[0].getTypeName());
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                    if(String.class == clazz){
                        return Arrays.asList(arrayParams);
                    } else if (int.class == clazz || Integer.class == clazz) {
                        return Arrays.asList(TypeConvertor.toIntegerArray(arrayParams));
                    }else if (long.class == clazz || Long.class == clazz) {
                        return  Arrays.asList(TypeConvertor.toLongArray(arrayParams));
                    }else if (double.class ==  clazz || Double.class == clazz) {
                        return  Arrays.asList(TypeConvertor.toDoubleArray(arrayParams));
                    }else if (float.class == clazz || Float.class == clazz) {
                        return  Arrays.asList(TypeConvertor.toFloatArray(arrayParams));
                    }else if (boolean.class == clazz || Boolean.class == clazz) {
                        return  Arrays.asList(TypeConvertor.toBooleanArray(arrayParams));
                    }else if(FileUpload.class == clazz){
                        return files.get().get(paramName);
                    }else if(File.class == clazz){
                        return files.get().get(paramName).stream().map(f -> {
                            try {
                                return f.getFile();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            return null;
                        }).collect(Collectors.toList());
                    }
                }


                if(arrayParams != null){
                    return Arrays.asList(arrayParams);
                }else if(fileParams != null){
                    return fileParams;
                }
            }else if(String.class == parameterType){
                return value;
            } else if (int.class == parameterType || Integer.class == parameterType) {
                return TypeConvertor.toInteger(value);
            }else if (long.class == parameterType || Long.class == parameterType) {
                return TypeConvertor.toLong(value);
            }else if (double.class == parameterType || Double.class == parameterType) {
                return TypeConvertor.toDouble(value);
            }else if (float.class == parameterType || Float.class == parameterType) {
                return TypeConvertor.toFloat(value);
            }else if (boolean.class == parameterType || Boolean.class == parameterType) {
                return TypeConvertor.toBoolean(value);
            }else if(FileUpload.class == parameterType){
                List<FileUpload> fileUploads = files.get().get(paramName);
                if(fileUploads == null || fileUploads.size() == 0){
                    return null;
                }else{
                    return fileUploads.get(0);
                }
            }else if(File.class == parameterType){
                List<FileUpload> fileUploads = files.get().get(paramName);
                if(fileUploads == null || fileUploads.size() == 0){
                    return null;
                }else{
                    try {
                        return fileUploads.get(0).getFile();
                    } catch (IOException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }
    /**
     * read the Post request, analyze the form parameter and the files parameter
     */
    private void handlePostMethod(HttpPostRequestDecoder decoder){
        Map<String,String[]> params = this.paramsHolder.get();
        Map<String,List<FileUpload>> files = this.files.get();
        try {
            while (decoder.hasNext()) {
                InterfaceHttpData data = decoder.next();
                if (data != null) {
                    if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {//handle the form parameter
                        Attribute attribute = (Attribute) data;
                        try {
                            String name = attribute.getName();
                            if (params.containsKey(name)) {//如果已经存在该参数，则叠加到以前的参数值里
                                String[] oldValue = params.get(name);
                                String[] newValue = new String[oldValue.length + 1];
                                System.arraycopy(oldValue, 0, newValue, 0, oldValue.length);
                                newValue[newValue.length - 1] = attribute.getValue();
                                params.put(attribute.getName(), newValue);
                            } else {
                                params.put(attribute.getName(), new String[]{attribute.getValue()});
                            }

                        } catch (IOException e1) {
                            e1.printStackTrace();
                            return;
                        } finally {
                            data.release();
                        }
                    } else {//handle file
                        if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
                            FileUpload fileUpload = (FileUpload) data;
                            if (fileUpload.isCompleted()) {
                                List<FileUpload> fileList = files.get(fileUpload.getName());
                                if (fileList == null) {
                                    fileList = new ArrayList<>();
                                    files.put(fileUpload.getName(), fileList);
                                }
                                fileList.add(fileUpload);
                            }
                        }
                    }
                }
            }
        }catch (HttpPostRequestDecoder.EndOfDataDecoderException e){
            logger.log(Level.INFO, "END OF CONTENT CHUNK BY CHUNK");
        }
    }

    /**
     * not support the http request
     * @param channel request channel
     */
    private void json(Channel channel, HttpRequest request, HttpResponseStatus status, String responseText){
        ByteBuf buf = responseText == null?Unpooled.buffer(0) :copiedBuffer(responseText, CharsetUtil.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,buf);
        response.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().add("Access-Control-Allow-Origin", "*");
        addRequestCookiesToResponse(request, response);

        ChannelFuture future = channel.writeAndFlush(response);
        future.addListener(ChannelFutureListener.CLOSE);
        channel.close();
    }

    /**
     * not support the http request
     * @param channel request channel
     */
    private void json(Channel channel, HttpRequest request, HttpResponseStatus status){
        json(channel, request, status, null);
    }

    /**
     * add request cookies to response
     */
    private void addRequestCookiesToResponse(HttpRequest request, HttpResponse response){
        Set<io.netty.handler.codec.http.cookie.Cookie> cookies;
        String value = request.headers().get(COOKIE);
        if (value == null) {
            cookies = Collections.emptySet();
        } else {
            cookies = ServerCookieDecoder.LAX.decode(value);
        }
        if (!cookies.isEmpty()) {
            // Reset the cookies if necessary.
            for (io.netty.handler.codec.http.cookie.Cookie cookie : cookies) {
                response.headers().add(SET_COOKIE, io.netty.handler.codec.http.cookie.ServerCookieEncoder.LAX.encode(cookie));
            }
        }
    }
}
