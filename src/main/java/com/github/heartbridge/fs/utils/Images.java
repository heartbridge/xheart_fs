package com.github.heartbridge.fs.utils;


import com.github.heartbridge.fs.exception.ApplicationRunTimeException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;

/**
 * image tools
 * @author GavinCook
 * @since 1.0.0
 */
public class Images {
    /**
     * compress image into file
     * @param originImage the image file need to compress
     * @param quality compress quality
     * @param file file to store compress image
     * @throws IOException
     */
    public static void compressImageToFile(BufferedImage originImage,float quality,File file) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter imageWriter = writers.next();
        ImageWriteParam param = imageWriter.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        imageWriter.setOutput(ImageIO.createImageOutputStream(new FileInputStream(file)));
        imageWriter.write(null,new IIOImage(originImage,null,null),param);
    }

    /**
     * compress image, return the compressed image object
     * @param originImage the image file need to compress
     * @param quality compress quality
     * @return the compressed image object
     * @throws IOException
     */
    public static BufferedImage compressImage(BufferedImage originImage,float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter imageWriter = writers.next();
        ImageWriteParam param = imageWriter.getDefaultWriteParam();

        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageOutputStream imageOut = ImageIO.createImageOutputStream(out);
        imageWriter.setOutput(imageOut);
        imageWriter.write(null,new IIOImage(originImage,null,null),param);
        imageOut.flush();
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

        BufferedImage image = ImageIO.read(in);
        out.close();
        imageOut.close();
        in.close();

        return image;
    }


    /**
     * clip the image into square one
     * @param originImage the image need to clipped
     * @return squarized image
     */
    public static BufferedImage toSquare(BufferedImage originImage){
        int width = originImage.getWidth(),height = originImage.getHeight(),
                x = 0 , y = 0;
        if(width>height){//width is more than height, use height as side length
            x = (width-height)/2;
            width = height;
        }else{
            y = (height-width)/2;
            height = width;
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.getGraphics().drawImage(originImage, 0, 0, width, height, x, y, width, height, null);
        return image;
    }

    /**
     * clip image
     * @param originImage the image need to clipped
     * @param x start x-axis
     * @param y start y-axis
     * @param width the clip width
     * @param height the clip height
     * @return clipped images
     */
    public static BufferedImage clipImage(BufferedImage originImage ,int x, int y, int width , int height){
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.getGraphics().drawImage(originImage,0,0,width,height,x,y,width,height,null);
        return image;
    }

    /**
     * scale image, width and height can't both equal zero
     * @param originImage the image need to scaled
     * @param width scale width, ignore when zero,will use height to calculate
     * @param height scale height, ignore when zero,will use width to calculate
     * @param ratio if <code>true</code>, width and height parameter means: max-width and max-height
     * @param allowBigger if can allow scale out
     * @return the scaled image
     */
    public static BufferedImage scaleImage(BufferedImage originImage , int width , int height, boolean ratio, boolean allowBigger){
        if(ratio){
            int originWidth = originImage.getWidth(),
                    originHeight = originImage.getHeight();
            if(!allowBigger){//not allow scale out
                width = width > originWidth ? originWidth : width;
                height = height > originHeight ? originHeight : height;
            }

            if(width > 0 && height > 0 ) {//both width and height is valid, calculate the scale for height and width , then determine which one to based
                double widthScale = (double) originWidth / (double) width, heightScale = (double) originHeight / (double) height;
                if (widthScale > heightScale) {//width scaled more, use width
                    height = (int) (originHeight / widthScale);
                } else {
                    width = (int) (originWidth / heightScale);
                }
            }else if(width <= 0 && height <= 0){
                throw new ApplicationRunTimeException("The maximum-scaled width and maximum-scaled height for image must not both less than zero");
            }else if(width > 0){//height not valid,use width
                double widthScale = (double) originWidth / (double) width;
                height = (int) (originHeight / widthScale);
            }else if(height > 0){//width not valid,use height
                double heightScale = (double) originHeight / (double) height;
                width = (int) (originWidth / heightScale);
            }
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.createGraphics().drawImage(originImage.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);
        return image;
    }
}
