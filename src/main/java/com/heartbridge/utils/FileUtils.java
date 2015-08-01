package com.heartbridge.utils;

import java.io.*;
import java.text.DecimalFormat;
import java.util.Calendar;

/**
 * file tools
 *
 * @author GavinCook
 * @since  1.0.0
 */
public class FileUtils {

    /**
     * read the data from stream and save into file
     * @param in the input stream
     * @param file dest file
     * @throws IOException
     */
    public static void save(InputStream in,File file) throws IOException{
        if(!file.exists()){
            file.createNewFile();
        }
        FileOutputStream out = new FileOutputStream(file);
        byte[] data = new byte[10240];
        int length = 0;
        while((length=in.read(data))!=-1){
            out.write(data,0,length);
        }
        out.flush();
        out.close();
        in.close();
    }

    /**
     * save the byte data into file
     * @param data file data in byte format
     * @param file dest file
     * @throws IOException
     */
    public static void save(byte[] data,File file) throws IOException{
        if(!file.exists()){
            file.createNewFile();
        }
        FileOutputStream out = new FileOutputStream(file);
        out.write(data);
        out.flush();
        out.close();
    }

    /**
     * get the extension name
     * @param file the file
     * @return the extension name
     */
    public static String getExtname(File file) {
        String name = file.getName();
        int position = name.lastIndexOf(".");
        if (position != -1) {
            return name.substring(position + 1);
        }
        return "";
    }

    /**
     * get the prefix name of file
     * @param file the file
     * @return prefix name
     */
    public static String getPrefixName(File file) {
        String name = file.getName();
        int position = name.lastIndexOf(".");
        if (position != -1) {
            return name.substring(0, position);
        }
        return "";
    }

    /**
     * get the file name that not duplicate with the exist files
     * @param file the file with origin file
     * @return the file name that not duplicate with the exist files
     */
    public static File getFileNotExists(File file) {
        String baseDir = file.getParent(),
                prefix = getPrefixName(file),
                extname = getExtname(file);
        int duplicatNum = 0;
        while (file.exists()) {
            duplicatNum++;
            file = new File(baseDir, prefix + "(" + duplicatNum + ")." + extname);
            if (duplicatNum == 50) {//loop 50 times at most, means there only can exist xxx(50).xx file at most
                prefix = prefix + System.currentTimeMillis();
                duplicatNum = 0;
            }
        }
        return file;
    }

    /**
     * create file, would create parent folder if not exist, someway it like "mkdir -p"
     * @param path the file path
     * @param dir is directory or file
     * @return created file
     * @throws IOException
     */
    public static File createIfNotExists(String path, boolean dir) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            if (dir) {
                file.mkdirs();
            } else {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
        }
        return file;
    }

    /**
     * get the timestamp file path under the base directory
     * like :
     * <code>
     * getTimestampPath("D:/upload")-->D:/upload/2015/02/10/12/25/30/
     * </code>
     *
     * @param baseDir the base directory
     * @return the timestamp file path
     */
    public static String getTimestampPath(String baseDir) {
        Calendar now = Calendar.getInstance();
        StringBuilder sb = new StringBuilder();
        String separator = File.separator;
        sb.append(baseDir)
                .append(now.get(Calendar.YEAR)).append(separator)
                .append(now.get(Calendar.MONTH) + 1).append(separator)
                .append(now.get(Calendar.DAY_OF_MONTH)).append(separator)
                .append(now.get(Calendar.HOUR_OF_DAY)).append(separator)
                .append(now.get(Calendar.MINUTE)).append(separator)
                .append(now.get(Calendar.SECOND)).append(separator);
        return sb.toString();
    }

    /**
     * get the readable file size
     * @param size the size in bytes
     * @return the readable file size
     */
    public static String getReadableFileSize(Long size) {
        if (size <= 0) return "0";
        final String[] units = new String[]{"B", "kB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    /**
     * get the extension name
     * @param name the file name
     * @return extension name
     */
    public static String getExtName(String name){
        int position = name.lastIndexOf(".");
        if(position != -1){
            return name.substring(position+1);
        }
        return "";
    }
}
