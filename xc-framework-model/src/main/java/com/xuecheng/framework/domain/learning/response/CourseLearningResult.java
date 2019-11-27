package com.xuecheng.framework.domain.learning.response;

import com.xuecheng.framework.model.response.ResponseResult;
import com.xuecheng.framework.model.response.ResultCode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@NoArgsConstructor
public class CourseLearningResult extends ResponseResult {
    String fileUrl;//学习地址url
    public CourseLearningResult(ResultCode resultCode, String fileUrl){
        super(resultCode);
        this.fileUrl = fileUrl;
    }
}
