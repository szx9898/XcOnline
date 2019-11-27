package com.xuecheng.manage_media.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class DirDemo {
    private static int num = 0;
    public static void main(String[] args) throws Exception{
        int n = listDirDemo(new File("E:\\java0512 - 0806 起点"));
        System.out.println(n);
    }
    public static int read(File file)throws Exception{
        BufferedReader br = new BufferedReader(new FileReader(file));
        int lineNum = 0;
        String line = null;
        while ((line=br.readLine())!=null){
            lineNum ++;
        }
        return lineNum;
    }
    public  static int listDirDemo(File f) throws Exception{

        File[] files = f.listFiles((pathname) ->pathname.isDirectory()||pathname.getName().toLowerCase().endsWith(".java"));
        for (File file : files) {
            if(file.isDirectory()){
                listDirDemo(file);
            } else{
                int lineNum = read(file);
                num += lineNum;
            }
        }
        return num;
    }
}