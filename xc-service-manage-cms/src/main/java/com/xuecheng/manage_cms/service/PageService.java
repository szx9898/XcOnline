package com.xuecheng.manage_cms.service;

import com.alibaba.fastjson.JSON;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.xuecheng.framework.domain.cms.CmsConfig;
import com.xuecheng.framework.domain.cms.CmsPage;
import com.xuecheng.framework.domain.cms.CmsSite;
import com.xuecheng.framework.domain.cms.CmsTemplate;
import com.xuecheng.framework.domain.cms.request.QueryPageRequest;
import com.xuecheng.framework.domain.cms.response.CmsCode;
import com.xuecheng.framework.domain.cms.response.CmsPageResult;
import com.xuecheng.framework.domain.cms.response.CmsPostPageResult;
import com.xuecheng.framework.exception.ExceptionCast;
import com.xuecheng.framework.model.response.CommonCode;
import com.xuecheng.framework.model.response.QueryResponseResult;
import com.xuecheng.framework.model.response.QueryResult;
import com.xuecheng.framework.model.response.ResponseResult;
import com.xuecheng.manage_cms.config.RabbitmqConfig;
import com.xuecheng.manage_cms.dao.CmsConfigRepository;
import com.xuecheng.manage_cms.dao.CmsPageRepository;
import com.xuecheng.manage_cms.dao.CmsSiteRepository;
import com.xuecheng.manage_cms.dao.CmsTemplateRepository;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Administrator
 * @version 1.0
 * @create 2018-09-12 18:32
 **/
@Service
public class PageService {
    @Autowired
    CmsSiteRepository cmsSiteRepository;
    @Autowired
    CmsPageRepository cmsPageRepository;
    @Autowired
    CmsConfigRepository cmsConfigRepository;
    @Autowired
    RestTemplate restTemplate;
    @Autowired
    CmsTemplateRepository cmsTemplateRepository;
    @Autowired
    GridFSBucket gridFSBucket;
    @Autowired
    GridFsTemplate gridFsTemplate;
    @Autowired
    RabbitTemplate rabbitTemplate;

    //页面发布
    public ResponseResult post(String pageId){
        //执行页面静态化
        String pageHtml = getPageHtml(pageId);
        //将页面静态化文件存入GridFs中
        CmsPage cmsPage = saveHtml(pageId, pageHtml);
        //向MQ发消息
        sendPostPage(pageId);
        return new ResponseResult(CommonCode.SUCCESS);
    }
    //向mq发送消息
    private void sendPostPage(String pageId){
        //先得到页面信息
        CmsPage cmsPage = findById(pageId);
        if (cmsPage==null){
            ExceptionCast.cast(CommonCode.INVALID_PARAM);
        }
        //创建消息对象
        Map<String,String> msg = new HashMap<>();
        msg.put("pageId",pageId);
        //转成json字符串
        String jsonString = JSON.toJSONString(msg);
        //发送mq
        //站点id
        String siteId = cmsPage.getSiteId();
        rabbitTemplate.convertAndSend(RabbitmqConfig.EX_ROUTING_CMS_POSTPAGE,siteId,jsonString);
    }
    //保存html到GridFs
    private CmsPage saveHtml(String pageId,String htmlContent){
        //先得到页面信息
        CmsPage cmsPage = findById(pageId);
        if (cmsPage==null){
            ExceptionCast.cast(CommonCode.INVALID_PARAM);
        }
        ObjectId objectId = null;
        try {
            //将html内容转成一个输入流
            InputStream inputStream = IOUtils.toInputStream(htmlContent, "utf-8");
            //将html文件保存到GridFS中
            objectId = gridFsTemplate.store(inputStream, cmsPage.getPageName());
        } catch (IOException e) {
            e.printStackTrace();
        }
        //将html文件id更新到cmsPage的htmlFielsId
        cmsPage.setHtmlFileId(objectId.toHexString());
        cmsPageRepository.save(cmsPage);
        return cmsPage;
    }

    /**
     * 页面查询方法
     * @param page 页码，从1开始记数
     * @param size 每页记录数
     * @param queryPageRequest 查询条件
     * @return
     */
    public QueryResponseResult findList(int page, int size, QueryPageRequest queryPageRequest){
        if (queryPageRequest==null){
            queryPageRequest = new QueryPageRequest();
        }
        //自定义条件查询
        //定义条件匹配器
        ExampleMatcher exampleMatcher = ExampleMatcher
                //创建匹配器
                .matching()
                //添加匹配条件，这里是模糊查询
                .withMatcher("pageAliase", ExampleMatcher.GenericPropertyMatchers.contains());
        //条件值对象
        CmsPage cmsPage = new CmsPage();
        //设置条件值(站点id)
        if (StringUtils.isNoneEmpty(queryPageRequest.getSiteId())){
            cmsPage.setSiteId(queryPageRequest.getSiteId());
        }
        //模板id
        if (StringUtils.isNoneEmpty(queryPageRequest.getTemplateId())){
            cmsPage.setTemplateId(queryPageRequest.getTemplateId());
        }
        //页面别名
        if (StringUtils.isNoneEmpty(queryPageRequest.getPageAliase())){
            cmsPage.setPageAliase(queryPageRequest.getPageAliase());
        }
        //分页参数
        if(page <=0){
            page = 1;
        }
        //创建一个条件对象
        Example<CmsPage> example = Example.of(cmsPage, exampleMatcher);

        page = page -1;
        if(size<=0){
            size = 10;
        }
        Pageable pageable = PageRequest.of(page,size);
        Page<CmsPage> all = cmsPageRepository.findAll(example,pageable);//实现自定义条件查询，并且分页查询
        QueryResult queryResult = new QueryResult();
        queryResult.setList(all.getContent());//数据列表
        queryResult.setTotal(all.getTotalElements());//数据总记录数
        QueryResponseResult queryResponseResult = new QueryResponseResult(CommonCode.SUCCESS,queryResult);
        return queryResponseResult;
    }
    //新增页面
    /*public CmsPageResult add(CmsPage cmsPage){
        //校验页面名称丶站点id丶页面webpath的唯一性
        CmsPage cmsPage1 = cmsPageRepository.findByPageNameAndPageWebPathAndSiteId(cmsPage.getPageName(), cmsPage.getPageWebPath(), cmsPage.getSiteId());
        if (cmsPage1==null){
            //调用dao新增页面
            cmsPage.setPageId(null);
            cmsPageRepository.save(cmsPage);
            return new CmsPageResult(CommonCode.SUCCESS,cmsPage);
        }
        return new CmsPageResult(CommonCode.FAIL,null);
    }*/
    //新增页面
    public CmsPageResult add(CmsPage cmsPage){
        if (cmsPage==null){
            //抛出异常，非常参数异常..指定异常信息的内容
        }
        //校验页面名称丶站点id丶页面webpath的唯一性
        CmsPage cmsPage1 = cmsPageRepository.findByPageNameAndPageWebPathAndSiteId(cmsPage.getPageName(), cmsPage.getPageWebPath(), cmsPage.getSiteId());
        if (cmsPage1!=null) {
            //页面已经存在
            //抛出异常，异常内容就是页面已存在
            ExceptionCast.cast(CmsCode.CMS_ADDPAGE_EXISTSNAME);
        }
        //调用dao新增页面
        cmsPage.setPageId(null);
        cmsPageRepository.save(cmsPage);
        return new CmsPageResult(CommonCode.SUCCESS,cmsPage);
    }
    //根据页面id查询页面
    public CmsPage findById(String id){
        Optional<CmsPage> optional = cmsPageRepository.findById(id);
        if (optional.isPresent()){
            return optional.get();
        }
        return null;
    }
    //修改页面
    public CmsPageResult update(String id,CmsPage cmsPage){
        //查询
        CmsPage one = findById(id);
        if (one!=null){
            one.setPageAliase(cmsPage.getPageAliase());
            one.setTemplateId(cmsPage.getTemplateId());
            one.setSiteId(cmsPage.getSiteId());
            one.setDataUrl(cmsPage.getDataUrl());
            one.setPageWebPath(cmsPage.getPageWebPath());
            one.setPagePhysicalPath(cmsPage.getPagePhysicalPath());
            one.setPageName(cmsPage.getPageName());
            //提交修改
            cmsPageRepository.save(one);
            return new CmsPageResult(CommonCode.SUCCESS,one);
        }
        return new CmsPageResult(CommonCode.FAIL,null);
    }
    //根据id删除页面
    public ResponseResult delete(String id){
        //先查询一下
        CmsPage cms = findById(id);
        if (cms!=null){
            cmsPageRepository.deleteById(id);
            return new ResponseResult(CommonCode.SUCCESS);
        }
        return new ResponseResult(CommonCode.FAIL);
    }
    //根据id查询cmsConfig
    public CmsConfig getConfigById(String id){
        Optional<CmsConfig> optional = cmsConfigRepository.findById(id);
        if (optional.isPresent()){
            CmsConfig cmsConfig = optional.get();
            return cmsConfig;
        }
        return null;
    }
    //页面静态化方法

    /**
     * 静态化程序获取页面的 DataURL
     * 静态化程序远程请求 DataUrl获取数据模型。
     * 静态化程序获取页面的模板信息
     * 执行页面静态化
     * @param pageId
     * @return
     */
    public String getPageHtml(String pageId){
        //获取数据模型
        Map model = getModelByPageId(pageId);
        if (model==null){
            //数据模型获取不到
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_DATAISNULL);
        }
        //获取页面的模板信息
        String template = getTemplateByPageId(pageId);
        if (StringUtils.isEmpty(template)){
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_TEMPLATEISNULL);
        }
        //执行页面静态化
        String html = generateHtml(template, model);
        return html;

    }
    //执行静态化
    private String generateHtml(String templateContent,Map model){
        //创建配置对象
        Configuration configuration = new Configuration(Configuration.getVersion());
        //创建模板加载器
        StringTemplateLoader stringTemplateLoader = new StringTemplateLoader();
        //加载模板到模板加载器
        stringTemplateLoader.putTemplate("template",templateContent);
        //向configuration中配置模板加载器
        configuration.setTemplateLoader(stringTemplateLoader);
        //获取模板内容
        try {
            Template template = configuration.getTemplate("template");
            //调用api进行静态化
            String content = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
            return content;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TemplateException e) {
            e.printStackTrace();
        }
        return null;
    }
    //获取页面模板
    private String getTemplateByPageId(String pageId){
        //取出页面的信息
        CmsPage cmsPage = this.findById(pageId);
        if (cmsPage == null){
            //页面不存在
            ExceptionCast.cast(CmsCode.CMS_PAGE_NOTEXISTS);
        }
        //获取页面的模板id
        String templateId = cmsPage.getTemplateId();
        if (StringUtils.isEmpty(templateId)){
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_TEMPLATEISNULL);
        }
        //查询模板信息
        Optional<CmsTemplate> optional = cmsTemplateRepository.findById(templateId);
        if (optional.isPresent()){
            CmsTemplate cmsTemplate = optional.get();
            //获取模板文件id
            String templateFileId = cmsTemplate.getTemplateFileId();
            //从GridFS中取模板文件中的内容
            //根据文件id查询文件
            GridFSFile gridFSFile = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(templateFileId)));

            //打开一个下载流对象
            GridFSDownloadStream gridFSDownloadStream = gridFSBucket.openDownloadStream(gridFSFile.getObjectId());
            //创建GridFsResource对象，获取流
            GridFsResource gridFsResource = new GridFsResource(gridFSFile,gridFSDownloadStream);
            //从流中取数据
            String content = null;
            try {
                content = IOUtils.toString(gridFsResource.getInputStream(), "utf-8");
                return content;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
    //获取数据模型
    private Map getModelByPageId(String pageId){
        //取出页面的信息
        CmsPage cmsPage = this.findById(pageId);
        if (cmsPage == null){
            //页面不存在
            ExceptionCast.cast(CmsCode.CMS_PAGE_NOTEXISTS);
        }
        //取出页面的dataUrl
        String dataUrl = cmsPage.getDataUrl();
        if (StringUtils.isEmpty(dataUrl)){
            //页面的dataUrl为空
            ExceptionCast.cast(CmsCode.CMS_GENERATEHTML_DATAURLISNULL);
        }
        //通过restTemplate请求dataUrl获取数据
        ResponseEntity<Map> forEntity = restTemplate.getForEntity(dataUrl, Map.class);
        Map body = forEntity.getBody();
        return body;
    }
    //保存页面，有则更新，没有则添加
    public CmsPageResult save(CmsPage cmsPage) {
        CmsPage one = cmsPageRepository.findByPageNameAndPageWebPathAndSiteId(cmsPage.getPageName(), cmsPage.getPageWebPath(), cmsPage.getSiteId());
        if (one!=null){
            //页面更新
            return this.update(one.getPageId(),cmsPage);
        }
        return this.add(cmsPage);
    }
    //一键发布
    public CmsPostPageResult postPageQuick(CmsPage cmsPage) {
        //将页面信息存储到cms_page中
        CmsPageResult cmsPageResult = this.save(cmsPage);
        if (!cmsPageResult.isSuccess()){
            ExceptionCast.cast(CommonCode.FAIL);
        }
        //得到页面id
        CmsPage cmsPageSave = cmsPageResult.getCmsPage();
        String pageId = cmsPageSave.getPageId();
        //执行页面发布(先静态化、保存GridFS，向MQ发送消息下载到服务器)
        ResponseResult responseResult = this.post(pageId);
        if (!responseResult.isSuccess()){
            ExceptionCast.cast(CommonCode.FAIL);
        }
        //拼接url
        String siteId = cmsPageSave.getSiteId();
        CmsSite cmsSite = this.findCmsSiteById(siteId);
        //页面url
        String pageUrl = cmsSite.getSiteDomain()+cmsSite.getSiteWebPath()+cmsPage.getPageWebPath()+cmsPage.getPageName();
        return new CmsPostPageResult(CommonCode.SUCCESS,pageUrl);
    }
    //根据站点id查询站点信息
    public CmsSite findCmsSiteById(String siteId){
        Optional<CmsSite> optional = cmsSiteRepository.findById(siteId);
        if (optional.isPresent()){
            CmsSite cmsSite = optional.get();
            return cmsSite;
        }
        return null;
    }
}
