package com.github.heartbridge.fs.utils;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author GavinCook
 * @since 1.0.0
 **/
public class ClassScanner {

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        getClasses("com.heartbridge").forEach(System.out::println);
    }

    /**
     * scan the classes which under package with the param ${packageName}
     * @param packageName the package name what need search
     * @return the class collection
     * @throws IOException
     */
    public static Set<Class> getClasses(String packageName) {
        packageName = packageName.replaceAll("\\.", "/");
        Set<Class> classes = new HashSet<>();
        try {
            Enumeration<URL> urls = ClassScanner.class.getClassLoader().getResources(packageName);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                String protocol = url.getProtocol();
                if ("jar".equals(protocol)) {
                    JarURLConnection jarURLConnection = (JarURLConnection) url.openConnection();
                    JarFile jarFile = jarURLConnection.getJarFile();
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (entry.isDirectory()) continue;
                        if (name.startsWith(packageName) && name.endsWith(".class")) {

                                classes.add(Class.forName(name.substring(0, name.length() - 6).replaceAll("/", ".")));

                        }
                    }
                } else if("file".equals(protocol)){
                    String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
                    findAndAddClassesInPackageByFile(packageName, filePath, classes);
                }
            }
        }catch (Exception|Error e){
            e.printStackTrace();
        }

        return classes;
    }

    /**
     * scan the classes under folder
     * @param packageName the package name what need search
     * @param filePath the folder path
     * @param classes the class collection
     * @return the class collection
     */
    private static Set<Class> findAndAddClassesInPackageByFile(String packageName, String filePath, Set<Class> classes){
        File file = new File(filePath);
        if (file.isDirectory()){
            for(File f :file.listFiles()){
                if(f.isDirectory()){
                    findAndAddClassesInPackageByFile(packageName+"/"+f.getName(), f.getAbsolutePath(), classes);
                }else if(f.getName().endsWith(".class")){
                    String className = (packageName + "/" + f.getName().substring(0, f.getName().length() - 6)).replaceAll("/", ".");
                    try {
                        classes.add(Class.forName(className));
                    }catch (ClassNotFoundException e){
                        e.printStackTrace();
                    }
                }
            }
        }
        return classes;
    }
}
