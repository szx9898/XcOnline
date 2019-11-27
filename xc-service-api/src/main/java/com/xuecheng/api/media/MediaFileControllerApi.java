package com.xuecheng.api.media;

import com.xuecheng.framework.domain.media.MediaFile;
import com.xuecheng.framework.domain.media.request.QueryMediaFileRequest;
import com.xuecheng.framework.model.response.QueryResponseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api(value="媒资文件管理",description = "媒资文件管理")
public interface MediaFileControllerApi {
    @ApiOperation("我的媒资文件查询列表")
    public QueryResponseResult<MediaFile> findList(int page, int size, QueryMediaFileRequest mediaFileRequest);
}
