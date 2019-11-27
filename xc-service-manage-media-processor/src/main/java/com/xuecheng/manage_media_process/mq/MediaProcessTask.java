package com.xuecheng.manage_media_process.mq;

import com.alibaba.fastjson.JSON;
import com.xuecheng.framework.domain.media.MediaFile;
import com.xuecheng.framework.domain.media.MediaFileProcess_m3u8;
import com.xuecheng.framework.utils.HlsVideoUtil;
import com.xuecheng.framework.utils.Mp4VideoUtil;
import com.xuecheng.manage_media_process.dao.MediaFileRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MediaProcessTask {
    @Value("${xc-service-manage-media.ffmpeg-path}")
    String ffmpeg_path;
    @Value("${xc-service-manage-media.video-location}")
    String video_location;
    @Autowired
    private MediaFileRepository mediaFileRepository;
    //接收视频处理消息并进行视频处理
    @RabbitListener(queues = "${xc-service-manage-media.mq.queue-media-video-processor}",containerFactory = "customContainerFactory")
    public void receiveMediaPorcessTask(String msg){
        //1.解析消息内容，得到mediaId
        Map map = JSON.parseObject(msg, Map.class);
        String mediaId = (String) map.get("mediaId");
        //2.拿mediaId从数据库查询文件信息
        Optional<MediaFile> optional = mediaFileRepository.findById(mediaId);
        if (!optional.isPresent()){
            return;
        }
        MediaFile mediaFile = optional.get();
        //3.使用工具类将avi文件转成mp4
        if (!StringUtils.equals(mediaFile.getFileType(),"avi")){
            mediaFile.setProcessStatus("303004");//无需处理
            mediaFileRepository.save(mediaFile);
            return;
        }else {
            //需要处理
            mediaFile.setProcessStatus("303001");//无需处理
            mediaFileRepository.save(mediaFile);
        }
        //String ffmpeg_path, String video_path, String mp4_name, String mp4folder_path
        //要处理的视频的位置
        String video_path = video_location + mediaFile.getFilePath()+mediaFile.getFileName();
        //mp4的名称
        String mp4_name = mediaFile.getFileId()+".mp4";
        //mp4存放路径
        String mp4folder_path = video_location + mediaFile.getFilePath();
        //avi转mp4工具类
        Mp4VideoUtil mp4VideoUtil = new Mp4VideoUtil(ffmpeg_path, video_path, mp4_name, mp4folder_path);
        //进行处理
        String result = mp4VideoUtil.generateMp4();
        if (result==null || !"success".equals(result)){
            mediaFile.setProcessStatus("303003");
            //记录失败原因
            MediaFileProcess_m3u8 mediaFileProcess_m3u8 = new MediaFileProcess_m3u8();
            mediaFileProcess_m3u8.setErrormsg(result);
            mediaFile.setMediaFileProcess_m3u8(mediaFileProcess_m3u8);
            mediaFileRepository.save(mediaFile);
            return;
        }
        //4.将map4生成m3u8和ts文件
        //mp4视频文件路径
        String mp4_video_path = video_location + mediaFile.getFilePath() + mp4_name;
        //m3u8文件名称
        String m3u8_name = mediaFile.getFileId()+".m3u8";
        //m3u8文件路径
        String m3u8folder_path = video_location + mediaFile.getFilePath() + "hls/";
        //转m3u8工具类
        HlsVideoUtil hlsVideoUtil = new HlsVideoUtil(ffmpeg_path, mp4_video_path, m3u8_name, m3u8folder_path);
        String m3u8 = hlsVideoUtil.generateM3u8();
        if (m3u8==null || !"success".equals(m3u8)){
            mediaFile.setProcessStatus("303003");
            //记录失败原因
            MediaFileProcess_m3u8 mediaFileProcess_m3u8 = new MediaFileProcess_m3u8();
            mediaFileProcess_m3u8.setErrormsg(result);
            mediaFile.setMediaFileProcess_m3u8(mediaFileProcess_m3u8);
            mediaFileRepository.save(mediaFile);
            return;
        }
        //处理成功
        mediaFile.setProcessStatus("303002");
        //保存ts文件列表
        List<String> ts_list = hlsVideoUtil.get_ts_list();
        MediaFileProcess_m3u8 mediaFileProcess_m3u8 = new MediaFileProcess_m3u8();
        mediaFileProcess_m3u8.setTslist(ts_list);
        mediaFile.setMediaFileProcess_m3u8(mediaFileProcess_m3u8);
        //保存fileUrl(此url就是视频播放的相对路径)
        String fileUrl = mediaFile.getFilePath()+"hls/"+m3u8_name;
        mediaFile.setFileUrl(fileUrl);
        mediaFileRepository.save(mediaFile);


    }
}
