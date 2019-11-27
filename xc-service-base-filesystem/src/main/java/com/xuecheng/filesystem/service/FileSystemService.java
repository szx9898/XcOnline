package com.xuecheng.filesystem.service;

import com.alibaba.fastjson.JSON;
import com.github.tobato.fastdfs.domain.StorePath;
import com.github.tobato.fastdfs.service.FastFileStorageClient;
import com.xuecheng.filesystem.dao.FileSystemRepository;
import com.xuecheng.framework.domain.filesystem.FileSystem;
import com.xuecheng.framework.domain.filesystem.response.FileSystemCode;
import com.xuecheng.framework.domain.filesystem.response.UploadFileResult;
import com.xuecheng.framework.exception.ExceptionCast;
import com.xuecheng.framework.model.response.CommonCode;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class FileSystemService {
    private static final List<String> CONTENT_TYPES = Arrays.asList("image/gif", "image/jpeg","application/x-jpg","application/x-png");
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemService.class);
    @Autowired
    private FastFileStorageClient fileStorageClient;
    @Autowired
    FileSystemRepository fileSystemRepository;
    public UploadFileResult upload(MultipartFile multipartFile,
                                   String filetag,
                                   String businesskey,
                                   String metadata){
        //1.文件上传至fastDFS中，返回文件id(也就是路径)
        if (multipartFile==null){
            ExceptionCast.cast(FileSystemCode.FS_UPLOADFILE_FILEISNULL);
        }
        String fileId = fdfs_upload(multipartFile);
        if (StringUtils.isEmpty(fileId)){
            ExceptionCast.cast(FileSystemCode.FS_UPLOADFILE_SERVERFAIL);
        }
        //2.将文件信息存储到mongoDB中
        FileSystem fileSystem = new FileSystem();
        fileSystem.setFileId(fileId);
        fileSystem.setFilePath(fileId);
        fileSystem.setBusinesskey(businesskey);
        fileSystem.setFiletag(filetag);
        fileSystem.setFileName(multipartFile.getOriginalFilename());
        fileSystem.setFileType(multipartFile.getContentType());
        if (StringUtils.isNoneEmpty()){
            try {
                Map map = JSON.parseObject(metadata, Map.class);
                fileSystem.setMetadata(map);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        fileSystemRepository.save(fileSystem);
        return new UploadFileResult(CommonCode.SUCCESS,fileSystem);
    }
    //上传文件到fastDFS中

    /**
     *
     * @param file 文件本身
     * @return
     */
    public String fdfs_upload(MultipartFile file){
        //得到文件原始名称
        String originalFilename = file.getOriginalFilename();
        //检验文件类型
        String contentType = file.getContentType();
        if (!CONTENT_TYPES.contains(contentType)) {
            LOGGER.info("文件类型不合法:{}", originalFilename);
            return null;
        }
        try {
            //检验文件内容
            BufferedImage bufferedImage = ImageIO.read(file.getInputStream());
            if (bufferedImage == null) {
                LOGGER.info("文件内容不合法:{}", originalFilename);
                return null;
            }
            //获取文件扩展名
            String ext = StringUtils.substringAfterLast(originalFilename, ".");
            //保存到服务器
            StorePath storePath = fileStorageClient.uploadFile(file.getInputStream(), file.getSize(), ext, null);
            //获取文件id
            String fileId = storePath.getFullPath();
            return fileId;
        } catch (IOException e) {
            LOGGER.info("服务器内部错误:"+originalFilename);
            e.printStackTrace();
        }
        return null;
    }
    //文件信息存储到mongoDB中
}
