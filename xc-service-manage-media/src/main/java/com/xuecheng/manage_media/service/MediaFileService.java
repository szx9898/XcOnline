package com.xuecheng.manage_media.service;

import com.xuecheng.framework.domain.media.MediaFile;
import com.xuecheng.framework.domain.media.request.QueryMediaFileRequest;
import com.xuecheng.framework.model.response.CommonCode;
import com.xuecheng.framework.model.response.QueryResponseResult;
import com.xuecheng.framework.model.response.QueryResult;
import com.xuecheng.manage_media.dao.MediaFileRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class MediaFileService {
    @Autowired
    MediaFileRepository mediaFileRepository;
    //分页查询所有
    public QueryResponseResult<MediaFile> findList(int page, int size, QueryMediaFileRequest mediaFileRequest) {
        MediaFile mediaFile = new MediaFile();
        if (mediaFileRequest==null){
            mediaFileRequest = new QueryMediaFileRequest();
        }
        //查询条件匹配器
        ExampleMatcher exampleMatcher = ExampleMatcher.matching()
                //tag字段模糊匹配
                .withMatcher("tag",ExampleMatcher.GenericPropertyMatchers.contains())
                //文件原始名称模糊匹配
                .withMatcher("fileOriginalName",ExampleMatcher.GenericPropertyMatchers.contains())
                //处理状态精确匹配
                .withMatcher("processStatus",ExampleMatcher.GenericPropertyMatchers.exact());
        if (StringUtils.isNotEmpty(mediaFileRequest.getFileOriginalName())){
            mediaFile.setFileOriginalName(mediaFileRequest.getFileOriginalName());
        }
        if (StringUtils.isNotEmpty(mediaFileRequest.getProcessStatus())){
            mediaFile.setProcessStatus(mediaFileRequest.getProcessStatus());
        }
        if (StringUtils.isNotEmpty(mediaFileRequest.getTag())){
            mediaFile.setTag(mediaFileRequest.getTag());
        }
        Example<MediaFile> example = Example.of(mediaFile, exampleMatcher);
        if (page<=0){
            page = 1;
        }
        if (size<=0){
            size = 10;
        }
        page = page -1;
        //分页参数
        PageRequest pageRequest = new PageRequest(page, size);
        //分页查询
        Page<MediaFile> all = mediaFileRepository.findAll(example, pageRequest);
        QueryResult<MediaFile> mediaFileQueryResult = new QueryResult<MediaFile>();
        mediaFileQueryResult.setList(all.getContent());
        mediaFileQueryResult.setTotal(all.getTotalElements());
        QueryResponseResult<MediaFile> mediaFileQueryResponseResult = new QueryResponseResult<>(CommonCode.SUCCESS, mediaFileQueryResult);
        return mediaFileQueryResponseResult;
    }
}
