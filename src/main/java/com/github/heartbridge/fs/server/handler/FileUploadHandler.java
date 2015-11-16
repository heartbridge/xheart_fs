package com.github.heartbridge.fs.server.handler;

import com.github.heartbridge.fs.annotation.RequestMapping;
import com.github.heartbridge.fs.annotation.RequestMethod;
import com.github.heartbridge.fs.annotation.RequestParam;
import com.github.heartbridge.fs.exception.ApplicationRunTimeException;
import com.github.heartbridge.fs.server.ServerStartParamsAware;
import com.github.heartbridge.fs.utils.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.FileUpload;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;

/**
 * @author GavinCook
 * @since 1.0.0
 **/
public class FileUploadHandler implements ServerStartParamsAware{

    private String baseDir;

    private int threshold;

    private Logger logger = Logger.getLogger(getClass().getName());

    //file separator regex, used to format the file path into http url pattern
    private String fileSeparatorRegex = File.separator.equals("\\") ? "\\\\" : "/";

    @RequestMapping(value = "/.*", method = RequestMethod.GET)
    public void getFile(HttpRequest request, ChannelHandlerContext ctx, FullHttpResponse response){
        String fileName = request.getUri();
        System.out.println(fileName);
        String contentType = URLConnection.guessContentTypeFromName(fileName);
        if(contentType != null) {
            response.headers().add(CONTENT_TYPE, contentType);
            byte[] data = new byte[10240];
            int length;
            try (FileInputStream in = new FileInputStream(new File(baseDir,fileName))) {
                response.headers().add(CONTENT_LENGTH, in.available());
                while ((length = in.read(data)) != -1) {
                    response.content().writeBytes(data, 0, length);
                }
            } catch (IOException e) {
                throw new ApplicationRunTimeException(e);
            }
        }else{
            throw new ApplicationRunTimeException("contentType is valid");
        }
        ctx.channel().write(response);
    }

    @RequestMapping(value = "/", method = RequestMethod.POST)
    public Map<String,Object> upload(@RequestParam("files")FileUpload[] uploads,
                       @RequestParam(value = "width", defaultValue = "100")int width,
                       @RequestParam(value = "height", defaultValue = "100")int height,
                       @RequestParam(value = "isAvatar", defaultValue = "false")boolean isAvatar){

        List<Map<String,Object>> successes = new ArrayList<>();
        List<Map<String,Object>> failures = new ArrayList<>();

        for(FileUpload fileUpload : uploads){
            try{
                File f = new File(FileUtils.getTimestampPath(baseDir),System.currentTimeMillis()+"."+FileUtils.getExtName(fileUpload.getFilename()));
                byte[] data = fileUpload.get();
                File out = FileUtils.getFileNotExists(f);
                FileUtils.createIfNotExists(out.getAbsolutePath(), false);

                String contentType = fileUpload.getContentType();
                if(contentType != null && contentType.contains("image")) {//handle image
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
                successes.add(Maps.mapItSO("originName", fileUpload.getFilename(),
                        "fileName", out.getName(),
                        "filePath", out.getAbsolutePath().substring(baseDir.length() - 1).replaceAll(fileSeparatorRegex,"/")));
            }catch (Exception e) {
                logger.log(Level.SEVERE, "exception {0} thrown when save file", e.getMessage());
                e.printStackTrace();
                failures.add(Maps.mapItSO("originName", fileUpload.getFilename(), "errorMsg", e.getMessage()));

            }finally {
                fileUpload.release();
            }
        }
        return Maps.mapItSO("success", successes, "failure", failures);
    }

    @Override
    public void setStartParams(Map<String, String> startParams) {
        this.threshold = TypeConverter.toInteger(startParams.get(ServerStartParamsAware.THRESHOLD));
        this.baseDir = TypeConverter.toString(startParams.get(ServerStartParamsAware.BASEDIR));
    }
}
