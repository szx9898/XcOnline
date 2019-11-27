package com.xuecheng.test.fastdfs;

import com.github.tobato.fastdfs.domain.StorePath;
import com.github.tobato.fastdfs.service.FastFileStorageClient;
import org.csource.common.MyException;
import org.csource.fastdfs.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.*;

/**
 * @author Administrator
 * @version 1.0
 **/
@SpringBootTest
@RunWith(SpringRunner.class)
public class TestFastDFS {

    //上传文件
    @Test
    public void testUpload(){

        try {
            //加载fastdfs-client.properties配置文件
            ClientGlobal.initByProperties("config/fastdfs-client.properties");
            //定义TrackerClient，用于请求TrackerServer
            TrackerClient trackerClient = new TrackerClient();
            //连接tracker
            TrackerServer trackerServer = trackerClient.getConnection();
            //获取Stroage
            StorageServer storeStorage = trackerClient.getStoreStorage(trackerServer);
            //创建stroageClient
            StorageClient1 storageClient1 = new StorageClient1(trackerServer,storeStorage);
            //向stroage服务器上传文件
            //本地文件的路径
            String filePath = "D:/迅雷下载/abc.jpg";
            //上传成功后拿到文件Id
            String fileId = storageClient1.upload_file1(filePath, "jpg", null);
            System.out.println(fileId);
            //group1/M00/00/01/wKhlQVuhU3eADb4pAAAawU0ID2Q159.png

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    //下载文件
    @Test
    public void testDownload(){
        try {
            //加载fastdfs-client.properties配置文件
            ClientGlobal.initByProperties("config/fastdfs-client.properties");
            //定义TrackerClient，用于请求TrackerServer
            TrackerClient trackerClient = new TrackerClient();
            //连接tracker
            TrackerServer trackerServer = trackerClient.getConnection();
            //获取Stroage
            StorageServer storeStorage = trackerClient.getStoreStorage(trackerServer);
            //创建stroageClient
            StorageClient1 storageClient1 = new StorageClient1(trackerServer,storeStorage);
            //下载文件
            //文件id
            String fileId = "group1/M00/00/00/wKg4gl3UFCqASZvTAAnaD1A_hoI591.jpg";
            byte[] bytes = storageClient1.download_file1(fileId);
            //使用输出流保存文件
            FileOutputStream fileOutputStream = new FileOutputStream(new File("d:/logo.jpg"));
            fileOutputStream.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (MyException e) {
            e.printStackTrace();
        }
    }
    @Autowired
    private FastFileStorageClient storageClient;

    @Test
    public void testUpload1() throws FileNotFoundException {
        // 要上传的文件
        File file = new File("D:/迅雷下载/xxx.jpeg");
        // 上传并保存图片，参数：1-上传的文件流 2-文件的大小 3-文件的后缀 4-可以不管他
        StorePath storePath = this.storageClient.uploadFile(
                new FileInputStream(file), file.length(), "jpeg", null);
        // 带分组的路径
        System.out.println(storePath.getFullPath());
        // 不带分组的路径
        System.out.println(storePath.getPath());
    }



}
