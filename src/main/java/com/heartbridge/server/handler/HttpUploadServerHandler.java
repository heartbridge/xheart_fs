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

/**
 * the file upload handler for http request
 * @author GavinCook
 * @since 1.0.0
 */
public class HttpUploadServerHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final Logger logger = Logger.getLogger(HttpUploadServerHandler.class.getName());

    private HttpRequest request;

    //the form parameter
    private Map<String,String[]> paramsMap = new HashMap<>();

    //the file parameter
    private List<FileUpload> uploads = new ArrayList<>();

    private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE); // Disk if size exceed

    private HttpPostRequestDecoder decoder;

    //the base directory for storing files
    private String baseDir = File.separator+"files"+File.separator;

    //the base directory path length, used to substring the full path to an relative path
    private int dirPrefixLength = baseDir.length() - 1;

    //image compressed threshold
    private long threshold = 1024 * 1024;

    //file separator regex, used to format the file path into http url pattern
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

            //get file from server
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

            /*handle the file store request*/

            //handle the url parameter, store them into the <code>paramsMap</code>
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

        //only Post request will own the decoder
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
     * read the Post request, analyze the form parameter and the files parameter
     */
    private void handlePostMethod(){
        while (decoder.hasNext()) {
            InterfaceHttpData data = decoder.next();
            if (data != null) {
                if (data.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {//handle the form parameter
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
                } else {//handle file
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
     * not support the http request
     * @param channel request channel
     */
    private void unSupportMethod(Channel channel){
        HttpResponse response = response(HttpResponseStatus.METHOD_NOT_ALLOWED);
        addRequestCookiesToResponse(response);
        ChannelFuture future = channel.writeAndFlush(response);
        future.addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * construct response with the HttpResponseStatus, and Content-Type in "application/json"
     * @param responseStatus response status,like 200
     * @return the response for the <code><responseStatus/code>
     */
    private FullHttpResponse response(HttpResponseStatus responseStatus){
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, responseStatus);
        response.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");
        return response;
    }

    /**
     * add request cookies to response
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
     * store the files which kept in the <code>uploads</code>
     * @return
     * <code>
     *     {
     *         success:[
     *           {
     *               originName:xx,//origin file name
     *               fileName:xx,// name for stored file
     *               filePath:xx//the file path which not contains the base directory
     *           }
     *         ],
     *         failure:[
     *          {
     *               originName:xx,//orgin file name
     *               errorMsg:xx//the error message description
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
                if(contentType != null && contentType.contains("images")) {//handle image
                    File originImageFile = FileUtils.createIfNotExists(out.getParent() + File.separator + "origin_" + out.getName(), false);
                    FileUtils.save(data,originImageFile);//direct save the origin image, avoid red mask when use ImageIO
                    logger.log(Level.INFO, "[origin]file saved at {0}",originImageFile.getAbsoluteFile());
                    BufferedImage originImage = ImageIO.read(new ByteArrayInputStream(data));
                    float quality;
                    if (data.length > threshold) {
                        quality = 0.8f;
                        originImage = Images.compressImage(originImage, quality);
                    }

                    if (isAvatar) {//compress a 200*200 thumbnail, and set it as default if current image is avatar
                        originImage = Images.toSquare(originImage);
                        BufferedImage image200 = Images.scaleImage(originImage, 200, 200, true, false);
                        ImageIO.write(image200, "jpg", out);
                        logger.log(Level.INFO, "[compressed default avatar]file saved at {0}", out.getAbsoluteFile());

                        BufferedImage image100 = Images.scaleImage(originImage, 100, 100, true, false);
                        File image100File = new File(out.getParentFile(), "100_" + out.getName());
                        ImageIO.write(image100, "jpg", image100File);
                        logger.log(Level.INFO, "[compressed avatar]file saved at {0}", image100File.getAbsoluteFile());

                    } else {//compress 100*100 thumbnail, set the thumbnail as default
                        BufferedImage imageScaled = Images.scaleImage(originImage, width, height, true, false);
                        ImageIO.write(imageScaled, "jpg", out);
                        logger.log(Level.INFO, "[compressed default]file saved at {0}", out.getAbsoluteFile());
                    }
                }else{//handle other files
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
     * write file in response, return back to client
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
