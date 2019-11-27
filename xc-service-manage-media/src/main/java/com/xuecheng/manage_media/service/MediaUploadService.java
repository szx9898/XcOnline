package com.xuecheng.manage_media.service;

import com.alibaba.fastjson.JSON;
import com.xuecheng.framework.domain.media.MediaFile;
import com.xuecheng.framework.domain.media.response.CheckChunkResult;
import com.xuecheng.framework.domain.media.response.MediaCode;
import com.xuecheng.framework.exception.ExceptionCast;
import com.xuecheng.framework.model.response.CommonCode;
import com.xuecheng.framework.model.response.ResponseResult;
import com.xuecheng.manage_media.dao.MediaFileRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

@Service
public class MediaUploadService {
    @Autowired
    private AmqpTemplate amqpTemplate;
    @Autowired
    MediaFileRepository mediaFileRepository;

    @Value("${xc-service-manage-media.upload-location}")
    private String upload_location;

    //得到文件所属目录路径
    private String getFileFolderPath(String fileMd5){
        return upload_location+fileMd5.substring(0,1)+"/"+fileMd5.substring(1,2)+"/"+fileMd5+"/";
    }
    //得到文件的路径
    private String getFilePath(String fileMd5,String fileExt){
        return upload_location+fileMd5.substring(0,1)+"/"+fileMd5.substring(1,2)+"/"+fileMd5+"/"+fileMd5+"."+fileExt;
    }
    //得到块文件所属目录路径
    private String getChunkFileFolderPath(String fileMd5){
        return upload_location+fileMd5.substring(0,1)+"/"+fileMd5.substring(1,2)+"/"+fileMd5+"/chunk/";
    }
    /**
     * 根据文件Md5得到文件路径
     * 规则：
     *      一级目录：md5的第一个字符
     *      二级目录：md5的第二个字符
     *      三级目录：md5
     *      文件名：md5加扩展名
     * @param fileMd5
     * @param fileName
     * @param fileSize
     * @param mimetype
     * @param fileExt
     * @return
     */
    public ResponseResult register(String fileMd5, String fileName, Long fileSize, String mimetype, String fileExt) {
        //1.检查文件在磁盘上是否存在
        //1.1文件所属目录的路径
        String fileFolderPath = this.getFileFolderPath(fileMd5);
        //1.2文件的路径
        String filePath = this.getFilePath(fileMd5, fileExt);
        File file = new File(filePath);
        //1.3是否存在
        boolean exists = file.exists();
        //2.检查文件信息在mongoDB是否存在
        Optional<MediaFile> optional = mediaFileRepository.findById(fileMd5);
        if (exists && optional.isPresent()){
            //文件存在
            ExceptionCast.cast(MediaCode.UPLOAD_FILE_REGISTER_EXIST);
        }
        //文件不存在时做一些准备工作，检查文件所在目录是否存在，如果不存在则创建
        File fileFolder = new File(fileFolderPath);
        if (!fileFolder.exists()){
            fileFolder.mkdirs();
        }
        return new ResponseResult(CommonCode.SUCCESS);
    }

    //分块检查
    /**
     *
     * @param fileMd5 文件md5
     * @param chunk 块的下标
     * @param chunkSize 块的大小
     * @return
     */
    public CheckChunkResult checkChunk(String fileMd5, Integer chunk, Integer chunkSize) {
        //检查块文件所属目录是否存在
        //得到块文件所属目录
        String chunkFileFolderPath = this.getChunkFileFolderPath(fileMd5);
        //块文件
        File file = new File(chunkFileFolderPath+chunk);
        if (file.exists()){
            //存在
            return new CheckChunkResult(CommonCode.SUCCESS,true);
        }else {
            //不存在
            return new CheckChunkResult(CommonCode.FAIL,false);
        }
    }
    //上传分块
    public ResponseResult uploadChunk(MultipartFile file, String fileMd5, Integer chunk) {
        //检查分块目录，如果不存在则要自动创建
        //得到分块目录
        String chunkFileFolderPath = this.getChunkFileFolderPath(fileMd5);
        File file1 = new File(chunkFileFolderPath);
        if (!file1.exists()){
            file1.mkdirs();
        }
        //得到上传文件的输入流
        InputStream inputStream =null;
        FileOutputStream outputStream =null;
        try {
            inputStream = file.getInputStream();
            outputStream = new FileOutputStream(new File(chunkFileFolderPath+chunk));
            IOUtils.copy(inputStream,outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new ResponseResult(CommonCode.SUCCESS);
    }
    //合并文件
    public ResponseResult mergeChunks(String fileMd5, String fileName, Long fileSize, String mimetype, String fileExt) {
        //1.合并所有分块
        //得到块文件所属目录
        String chunkFileFolderPath = this.getChunkFileFolderPath(fileMd5);
        File chunkFileFolder = new File(chunkFileFolderPath);
        //得到块集合
        File[] chunkFiles = chunkFileFolder.listFiles();
        List<File> chunkFileList = Arrays.asList(chunkFiles);
        //创建一个合并文件
        String mergeFilePath = this.getFilePath(fileMd5, fileExt);
        File mergeFile = new File(mergeFilePath);
        //合并文件
        mergeFile = this.mergeFile(chunkFileList, mergeFile);
        if (mergeFile==null){
            ExceptionCast.cast(MediaCode.MERGE_FILE_FAIL);
        }
        //2.校验文件的md5值是否和前端传入的md5一致
        boolean checkMergeFile = checkFile(mergeFile, fileMd5);
        if (!checkMergeFile){
            //校验失败
            ExceptionCast.cast(MediaCode.MERGE_FILE_CHECKFAIL);
        }
        //3.将文件的信息写入 mongodb
        MediaFile mediaFile = new MediaFile();
        mediaFile.setFileId(fileMd5);
        mediaFile.setFileOriginalName(fileName);
        mediaFile.setFileName(fileMd5+"."+fileExt);
        //文件路径保存相对路径
        String fileRelativelyPath = fileMd5.substring(0,1)+"/"+fileMd5.substring(1,2)+"/"+fileMd5+"/";
        mediaFile.setFilePath(fileRelativelyPath);
        mediaFile.setFileSize(fileSize);
        mediaFile.setUploadTime(new Date());
        mediaFile.setMimeType(mimetype);
        mediaFile.setFileType(fileExt);
        //状态为上传成功
        mediaFile.setFileStatus("301002");
        mediaFileRepository.save(mediaFile);
        //发送视频处理消息
        sendProcessVideoMsg(mediaFile.getFileId());
        return new ResponseResult(CommonCode.SUCCESS);
    }
    //发送视频处理消息
    public ResponseResult sendProcessVideoMsg(String mediaId){
        //查询数据路此id存不存在
        Optional<MediaFile> optional = mediaFileRepository.findById(mediaId);
        if (!optional.isPresent()){
            ExceptionCast.cast(CommonCode.INVALID_PARAM);
        }
        Map<String,String> map = new HashMap<>();
        map.put("mediaId",mediaId);
        String jsonString = JSON.toJSONString(map);
        //向MQ发送视频处理消息
        try {
            amqpTemplate.convertAndSend(jsonString);
        } catch (AmqpException e) {
            e.printStackTrace();
            return new ResponseResult(CommonCode.FAIL);
        }
        return new ResponseResult(CommonCode.SUCCESS);
    }
    //校验文件
    private boolean checkFile(File mergeFile,String Md5){
        try {
            InputStream inputStream = new FileInputStream(mergeFile);
            //得到文件的Md5
            String md5Hex = DigestUtils.md5Hex(inputStream);
            //与传入的md5相比较
            if (StringUtils.endsWithIgnoreCase(Md5,md5Hex)){
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }
    //合并文件
    private File mergeFile(List<File> chunkFiles,File mergeFile){
        try {
            if (mergeFile.exists()){
                //如果合并的文件已存在，则删除
                mergeFile.delete();
            }
            //不存在，创建一个新的文件
            mergeFile.createNewFile();
            //对块文件进行排序
            Collections.sort(chunkFiles, new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    if (Integer.parseInt(o1.getName())>Integer.parseInt(o2.getName())){
                        return 1;
                    }
                    return -1;
                }
            });
            //创建一个写对象
            RandomAccessFile raf_write=new RandomAccessFile(mergeFile,"rw");
            byte [] buf = new byte[1024];
            //合并块文件到一个新的文件
            for (File chunkFile : chunkFiles) {
                RandomAccessFile raf_read=new RandomAccessFile(chunkFile,"r");
                int len = 0;
                while ((len=raf_read.read(buf))!=-1){
                    raf_write.write(buf,0,len);
                }
                raf_read.close();
            }
            raf_write.close();
            return mergeFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
