package com.heartbridge.server.handler;

import com.heartbridge.utils.FileUtils;
import com.heartbridge.utils.Images;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.handler.codec.http.multipart.*;
import io.netty.util.CharsetUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static io.netty.handler.codec.http.HttpHeaders.Names.*;

public class HttpUploadServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final Logger logger = Logger.getLogger(HttpUploadServerHandler.class.getName());

    private HttpRequest request;

    //表单参数
    private Map<String,String[]> paramsMap = new HashMap<>();

    //文件参数
    private List<FileUpload> uploads = new ArrayList<>();

    private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE); // Disk if size exceed

    private HttpPostRequestDecoder decoder;

    //文件存储的基础路径
    private String baseDir = File.separator+"files"+File.separator;

    //基础路径的长度，不包含最后一个文件分隔符
    private int dirPrefixLength = baseDir.length() - 1;

    private long threshold = 1024 * 1024;//压缩阀值，1m

    //文件分隔符的正则表达式,用来统一返回数据为http url格式
    private String fileSeparatorRegex = File.separator.equals("\\") ? "\\\\" : "/";

    static {
        DiskFileUpload.deleteOnExitTemporaryFile = false;
        DiskFileUpload.baseDirectory = null;
        DiskAttribute.deleteOnExitTemporaryFile = false;
        DiskAttribute.baseDirectory = null;
    }

    public HttpUploadServerHandler(String baseDir, long threshold){
        if(baseDir != null && !"".equals(baseDir.trim())) {
            this.baseDir = baseDir.replaceAll("[/\\\\]+" , fileSeparatorRegex);
            if(!this.baseDir.endsWith(File.separator)){
                this.baseDir = this.baseDir + File.separator;
            }
            this.dirPrefixLength = this.baseDir.length() - 1;
        }
        if(threshold > 0){
            this.threshold = threshold;
        }
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        if (decoder != null) {
            decoder.cleanFiles();
        }
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = this.request = (HttpRequest) msg;
            //读取文件
            if(request.getMethod().equals(HttpMethod.GET)){
                FullHttpResponse response=  new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                logger.log(Level.INFO, "trying to get file at {0}", request.getUri());
                Long currentTime = System.currentTimeMillis();
                writeFileToResponse(response, new File(baseDir, request.getUri()));
                ChannelFuture future = ctx.channel().writeAndFlush(response);
                future.addListener(ChannelFutureListener.CLOSE);
                logger.log(Level.INFO, "success to get file at {0}, cost {1}", new String[]{request.getUri(), (System.currentTimeMillis()-currentTime)+"ms"});
                return;
            }

            if(!request.getMethod().equals(HttpMethod.POST)){
                unSupportMethod(ctx.channel());
                return;
            }

            /*文件保存请求*/

            //处理URL上的参数
            QueryStringDecoder decoderQuery = new QueryStringDecoder(request.getUri());
            Map<String, List<String>> uriAttributes = decoderQuery.parameters();
            for (Map.Entry<String, List<String>> attr: uriAttributes.entrySet()) {
                List<String> attrVal = attr.getValue();
                String[] arrayAttrVal = attr.getValue().toArray(new String[attrVal.size()]);
                paramsMap.put(attr.getKey(),arrayAttrVal);
            }

            try {
                decoder = new HttpPostRequestDecoder(factory, request);
            } catch (HttpPostRequestDecoder.ErrorDataDecoderException e1) {
                e1.printStackTrace();
                writeResponse(ctx.channel(),e1.getMessage());
                ctx.channel().close();
                return;
            } catch (HttpPostRequestDecoder.IncompatibleDataDecoderException e1) {
                writeResponse(ctx.channel(),e1.getMessage());
                return;
            }
        }

        //post请求下，才有对应的decoder
        if (decoder != null) {
            if (msg instanceof HttpContent) {
                // New chunk is received
                HttpContent chunk = (HttpContent) msg;
                try {
                    decoder.offer(chunk);
                } catch (HttpPostRequestDecoder.ErrorDataDecoderException e1) {
                    e1.printStackTrace();
                    writeResponse(ctx.channel(),e1.getMessage());
                    ctx.channel().close();
                    return;
                }

                handlePostMethod();

                if (chunk instanceof LastHttpContent) {
                    writeResponse(ctx.channel(), saveFiles());
                    reset();
                }
            }
        } else {
            writeResponse(ctx.channel(), "");
        }
    }

    private void reset() {
        request = null;
        decoder.destroy();
        decoder = null;
    }

    /**
     * 读取请求中的参数，并将数据写入到<code>uploads</code>或者<code>params</code>中
     */
    private void handlePostMethod(){
        while (decoder.hasNext()) {
            InterfaceHttpData data = decoder.next();
            if (data != null) {
                if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
                    Attribute attribute = (Attribute) data;
                    try {
                        String name = attribute.getName();
                        if(paramsMap.containsKey(name)){//如果已经存在该参数，则叠加到以前的参数值里
                            String[] oldValue = paramsMap.get(name);
                            String[] newValue = new String[oldValue.length+1];
                            System.arraycopy(oldValue,0,newValue,0,oldValue.length);
                            newValue[newValue.length-1] = attribute.getValue();
                            paramsMap.put(attribute.getName(),newValue);
                        }else{
                            paramsMap.put(attribute.getName(),new String[]{attribute.getValue()});
                        }

                    } catch (IOException e1) {
                        e1.printStackTrace();
                        return;
                    }finally {
                        data.release();
                    }
                } else {//处理文件
                    if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.FileUpload) {
                        FileUpload fileUpload = (FileUpload) data;
                        if(fileUpload.isCompleted()){
                            uploads.add(fileUpload);
                        }
                    }
                }
            }
        }
    }


    private void writeResponse(Channel channel , String responseText) {
        ByteBuf buf = copiedBuffer(responseText, CharsetUtil.UTF_8);

        boolean close = HttpHeaders.Values.CLOSE.equalsIgnoreCase(request.headers().get(CONNECTION))
                || request.getProtocolVersion().equals(HttpVersion.HTTP_1_0)
                && !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(request.headers().get(CONNECTION));

        // Build the response object.
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
        response.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");

        if (!close) {
            response.headers().set(CONTENT_LENGTH, buf.readableBytes());
        }

        addRequestCookiesToResponse(response);

        // Write the response.
        ChannelFuture future = channel.writeAndFlush(response);
        // Close the connection after the write operation is done if necessary.
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
    }

    /**
     * 不支持的方法
     * @param channel 通道
     */
    private void unSupportMethod(Channel channel){
        HttpResponse response = response(HttpResponseStatus.METHOD_NOT_ALLOWED);
        addRequestCookiesToResponse(response);
        ChannelFuture future = channel.writeAndFlush(response);
        future.addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * 根据响应代码构造响应
     * @param responseStatus 响应代码
     * @return 根据响应代码构造的响应
     */
    private FullHttpResponse response(HttpResponseStatus responseStatus){
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, responseStatus);
        response.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");
        return response;
    }

    /**
     * 将请求的cookies添加到响应中
     * @param response 响应
     */
    private void addRequestCookiesToResponse(HttpResponse response){
        Set<Cookie> cookies;
        String value = request.headers().get(COOKIE);
        if (value == null) {
            cookies = Collections.emptySet();
        } else {
            cookies = ServerCookieDecoder.LAX.decode(value);
        }
        if (!cookies.isEmpty()) {
            // Reset the cookies if necessary.
            for (Cookie cookie : cookies) {
                response.headers().add(SET_COOKIE, ServerCookieEncoder.LAX.encode(cookie));
            }
        }
    }

    /**
     * 保存请求的文件，返回保存结果
     * @return 保存的结果
     * <code>
     *     {
     *         success:[
     *           {
     *               originName:xx,//原始名字
     *               fileName:xx,//存储的名字
     *               filePath:xx//存储的相对路径
     *           }
     *         ],
     *         failure:[
     *          {
     *               originName:xx,//原始名字
     *               errorMsg:xx//错误信息描述
     *           }
     *         ]
     *     }
     * </code>
     */
    private String saveFiles(){
        StringJoiner successes = new StringJoiner(",","{","}");
        StringJoiner failures = new StringJoiner(",","{","}");
        int width = Integer.valueOf(paramsMap.getOrDefault("width",new String[]{"100"})[0]);
        int height = Integer.valueOf(paramsMap.getOrDefault("height", new String[]{"100"})[0]);
        boolean isAvatar = Boolean.valueOf(paramsMap.getOrDefault("isAvatar", new String[]{"false"})[0]);

        for(FileUpload fileUpload : uploads){
            try{
                File f = new File(FileUtils.getTimestampPath(baseDir),System.currentTimeMillis()+"."+FileUtils.getExtName(fileUpload.getFilename()));
                byte[] data = fileUpload.get();
                File out = FileUtils.getFileNotExists(f);
                FileUtils.createIfNotExists(out.getAbsolutePath(), false);

                String contentType = URLConnection.guessContentTypeFromName(fileUpload.getName());
                if(contentType != null && contentType.contains("images")) {//处理图片
                    File originImageFile = FileUtils.createIfNotExists(out.getParent() + File.separator + "origin_" + out.getName(), false);
                    FileUtils.save(data,originImageFile);//保存源文件或者原图,原图直接保存，避免imageIO读取后变红
                    logger.log(Level.INFO, "[origin]file saved at {0}",originImageFile.getAbsoluteFile());
                    BufferedImage originImage = ImageIO.read(new ByteArrayInputStream(data));
                    float quality;
                    if (data.length > threshold) {
                        quality = 0.8f;
                        originImage = Images.compressImage(originImage, quality);
                    }

                    if (isAvatar) {//如果是头像需要保存一个200*200的尺寸，并且将200*200设为默认
                        originImage = Images.toSquare(originImage);//正方形
                        BufferedImage image200 = Images.scaleImage(originImage, 200, 200, true, false);
                        ImageIO.write(image200, "jpg", out);
                        logger.log(Level.INFO, "[compressed default avatar]file saved at {0}", out.getAbsoluteFile());

                        BufferedImage image100 = Images.scaleImage(originImage, 100, 100, true, false);
                        File image100File = new File(out.getParentFile(), "100_" + out.getName());
                        ImageIO.write(image100, "jpg", image100File);
                        logger.log(Level.INFO, "[compressed avatar]file saved at {0}", image100File.getAbsoluteFile());

                    } else {//否则保存100*100的缩略图，并设为默认
                        BufferedImage imageScaled = Images.scaleImage(originImage, width, height, true, false);
                        ImageIO.write(imageScaled, "jpg", out);
                        logger.log(Level.INFO, "[compressed default]file saved at {0}", out.getAbsoluteFile());
                    }
                }else{//处理文件
                    fileUpload.renameTo(out);
                    logger.log(Level.INFO, "file saved at {0}", out.getAbsoluteFile());
                }

                successes.add("\"originName\" :\""+ fileUpload.getFilename()+
                        "\",\"fileName\":\""+out.getName()+
                        "\",\"filePath\":\""+ out.getAbsolutePath().substring(dirPrefixLength)+"\"");
            }catch (Exception e) {
                logger.log(Level.SEVERE, "exception {0} thrown when save file", e.getMessage());
                e.printStackTrace();
                failures.add("\"originName\" :\""+ fileUpload.getFilename()+
                        "\",\"errorMsg\":\""+e.getMessage()+"\"");
            }finally {
                fileUpload.release();
            }
        }
        return ("{success:["+successes.toString()+"],failure:["+failures.toString()+"]}").replaceAll(fileSeparatorRegex,"/");
    }

    /**
     * 将文件写到响应中
     * @param response 响应
     * @param file 文件对象
     */
    private void writeFileToResponse(FullHttpResponse response , File file){
        String contentType = URLConnection.guessContentTypeFromName(file.getName());
        if(contentType != null) {
            response.headers().add(CONTENT_TYPE, contentType);
            byte[] data = new byte[10240];
            int length;
            try (FileInputStream in = new FileInputStream(file)) {
                response.headers().add(CONTENT_LENGTH, in.available());
                while ((length = in.read(data)) != -1) {
                    response.content().writeBytes(data, 0, length);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
