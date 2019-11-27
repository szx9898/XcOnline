package com.xuecheng.manage_course.service;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.xuecheng.framework.domain.cms.CmsPage;
import com.xuecheng.framework.domain.cms.response.CmsPageResult;
import com.xuecheng.framework.domain.cms.response.CmsPostPageResult;
import com.xuecheng.framework.domain.course.*;
import com.xuecheng.framework.domain.course.ext.CourseInfo;
import com.xuecheng.framework.domain.course.ext.CourseView;
import com.xuecheng.framework.domain.course.ext.TeachplanNode;
import com.xuecheng.framework.domain.course.request.CourseListRequest;
import com.xuecheng.framework.domain.course.response.AddCourseResult;
import com.xuecheng.framework.domain.course.response.CourseCode;
import com.xuecheng.framework.domain.course.response.CoursePublishResult;
import com.xuecheng.framework.exception.ExceptionCast;
import com.xuecheng.framework.model.response.CommonCode;
import com.xuecheng.framework.model.response.QueryResponseResult;
import com.xuecheng.framework.model.response.QueryResult;
import com.xuecheng.framework.model.response.ResponseResult;
import com.xuecheng.manage_course.client.CmsPageClient;
import com.xuecheng.manage_course.dao.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CourseService {
    @Autowired
    private TeachplanMediaRepository teachplanMediaRepository;
    @Autowired
    private TeachplanMediaPubRepository teachplanMediaPubRepository;
    @Autowired
    private CoursePubRepository coursePubRepository;
    @Autowired
    private CoursePicRepository coursePicRepository;
    @Autowired
    private CourseMarketRepository courseMarketRepository;
    @Autowired
    private CourseMapper courseMapper;
    @Autowired
    private TeachplanMapper teachplanMapper;
    @Autowired
    private CourseBaseRepository courseBaseRepository;
    @Autowired
    private TeachplanRepository teachplanRepository;
    @Autowired
    private CmsPageClient cmsPageClient;

    @Value("${course-publish.dataUrlPre}")
    private String publish_dataUrlPre;

    @Value("${course-publish.pagePhysicalPath}")
    private String publish_pagePhysicalPath;

    @Value("${course-publish.pageWebPath}")
    private String publish_pageWebPath;

    @Value("${course-publish.previewUrl}")
    private String publish_previewUrl;

    @Value("${course-publish.templateId}")
    private String publish_templateId;

    @Value("${course-publish.siteId}")
    private String publish_siteId;
    //课程计划查询
    public TeachplanNode findTeachplanList(String courseId){
        TeachplanNode teachplanNode = teachplanMapper.selectList(courseId);
        return teachplanNode;
    }
    //添加课程
    @Transactional
    public void addTeachplan(Teachplan teachplan) {
        if (teachplan==null ||
                StringUtils.isEmpty(teachplan.getCourseid()) ||
                StringUtils.isEmpty(teachplan.getPname())){
            ExceptionCast.cast(CommonCode.INVALID_PARAM);
        }

        //课程计划
        String courseid = teachplan.getCourseid();
        //页面传入的parentId
        String parentid = teachplan.getParentid();
        if (StringUtils.isEmpty(parentid)){
            //取出该课程的根节点
            parentid = getTeachplanRoot(courseid);
        }
        Optional<Teachplan> optional = teachplanRepository.findById(parentid);
        Teachplan parentNode = optional.get();
        //父节点的级别
        String parentGrade = parentNode.getGrade();
        //新节点
        Teachplan teachplanNew = new Teachplan();
        //将页面提交的teachplan信息拷贝到teachplanNew对象中
        BeanUtils.copyProperties(teachplan,teachplanNew);
        teachplanNew.setParentid(parentid);
        teachplanNew.setCourseid(courseid);
        if (StringUtils.equals(parentGrade,"1")){
            teachplanNew.setGrade("2");
        }else {
            teachplanNew.setGrade("3");
        }
        teachplanRepository.save(teachplanNew);
    }
    //查询课程的根节点，查询不到要自动添加根节点
    private String getTeachplanRoot(String courseId){
        Optional<CourseBase> option = courseBaseRepository.findById(courseId);
        if (!option.isPresent()){
            return null;
        }
        //课程信息
        CourseBase courseBase = option.get();
        //查询课程的根节点
        List<Teachplan> teachplanList = teachplanRepository.findByCourseidAndParentid(courseId, "0");
        if (CollectionUtils.isEmpty(teachplanList)){
            //查询不到，要自动添加根节点
            Teachplan teachplan = new Teachplan();
            teachplan.setParentid("0");
            teachplan.setGrade("1");
            teachplan.setCourseid(courseId);
            teachplan.setPname(courseBase.getName());
            teachplan.setCourseid(courseId);
            teachplan.setStatus("0");
            teachplanRepository.save(teachplan);
            return teachplan.getId();
        }
        //返回根节点id
        return teachplanList.get(0).getId();
    }

    public QueryResponseResult findCourseList(int page, int size, CourseListRequest courseListRequest) {
        if (courseListRequest == null){
            courseListRequest = new CourseListRequest();
        }
        if (page<=0){
            page = 0;
        }
        if (size<=0){
            size = 20;
        }
        PageHelper.startPage(page,size);
        Page<CourseInfo> courseList = courseMapper.findCourseList(courseListRequest);
        List<CourseInfo> result = courseList.getResult();
        long total = courseList.getTotal();
        QueryResult<CourseInfo> queryResult = new QueryResult<>();
        queryResult.setList(result);
        queryResult.setTotal(total);
        return new QueryResponseResult<CourseInfo>(CommonCode.SUCCESS,queryResult);
    }
    @Transactional
    public AddCourseResult addCourseBase(CourseBase courseBase) {
        //客户才能默认状态为未发布
        courseBase.setStatus("202001");
        courseBaseRepository.save(courseBase);
        return new AddCourseResult(CommonCode.SUCCESS,courseBase.getId());
    }

    public CourseBase findById(String courseId) {
        Optional<CourseBase> optional = courseBaseRepository.findById(courseId);
        if (optional.isPresent()){
            return optional.get();
        }
        ExceptionCast.cast(CourseCode.COURSE_NOTFOUNDCOURSE);
        return null;
    }
    @Transactional
    public ResponseResult updateCourseBase(String id, CourseBase courseBase) {
        CourseBase one = findById(id);
        if (one==null){
            ExceptionCast.cast(CommonCode.INVALID_PARAM);
        }
        BeanUtils.copyProperties(courseBase,one);
        CourseBase save = courseBaseRepository.save(one);
        return new ResponseResult(CommonCode.SUCCESS);
    }

    public CourseMarket getCourseMarketById(String courseId) {
        Optional<CourseMarket> optional = courseMarketRepository.findById(courseId);
        if (optional.isPresent()){
            return optional.get();
        }
        return null;
    }

    public CourseMarket updateCourseMarket(String id, CourseMarket courseMarket) {
        CourseMarket one = getCourseMarketById(id);
        if (one==null){
            one = new CourseMarket();
            BeanUtils.copyProperties(courseMarket,one);
            one.setId(id);
            courseMarketRepository.save(one);
        }else {
            BeanUtils.copyProperties(courseMarket,one);
            courseMarketRepository.save(one);
        }
        return one;
    }
    //向课程管理数据添加课程与图片的关联信息
    @Transactional
    public ResponseResult addCoursePic(String courseId, String pic) {
        CoursePic coursePic = null;
        Optional<CoursePic> optional = coursePicRepository.findById(courseId);
        if (optional.isPresent()){
            coursePic = optional.get();
        }
        if (coursePic == null){
            coursePic = new CoursePic();
        }
        coursePic.setCourseid(courseId);
        coursePic.setPic(pic);
        coursePicRepository.save(coursePic);
        return new ResponseResult(CommonCode.SUCCESS);
    }
    //查询课程图片
    public CoursePic findCoursePic(String courseId) {
        Optional<CoursePic> optional = coursePicRepository.findById(courseId);
        if (optional.isPresent()){
            CoursePic coursePic = optional.get();
            return coursePic;
        }
        return null;
    }
    //删除课程图片
    @Transactional
    public ResponseResult deleteCoursePic(String courseId) {
        //删除成功记录数
        long count = coursePicRepository.deleteByCourseid(courseId);
        if (count>0){
            return new ResponseResult(CommonCode.SUCCESS);
        }
        return new ResponseResult(CommonCode.FAIL);
    }
    //课程视图查询
    public CourseView getCourseView(String courseId) {
        CourseView courseView = new CourseView();
        //获取课程基础信息
        Optional<CourseBase> baseOptional = courseBaseRepository.findById(courseId);
        if (baseOptional.isPresent()){
            CourseBase courseBase = baseOptional.get();
            courseView.setCourseBase(courseBase);
        }
        //获取课程营销信息
        Optional<CourseMarket> courseMarketOptional = courseMarketRepository.findById(courseId);
        if (courseMarketOptional.isPresent()){
            CourseMarket courseMarket = courseMarketOptional.get();
            courseView.setCourseMarket(courseMarket);
        }
        //获取课程图片信息
        Optional<CoursePic> coursePicOptional = coursePicRepository.findById(courseId);
        if (coursePicOptional.isPresent()){
            CoursePic coursePic = coursePicOptional.get();
            courseView.setCoursePic(coursePic);
        }
        //获取课程计划信息
        TeachplanNode teachplanNode = teachplanMapper.selectList(courseId);
        courseView.setTeachplanNode(teachplanNode);
        return courseView;
    }

    //课程页面预览
    public CoursePublishResult preview(String id) {
        //获取cms_page的信息
        CmsPage cmsPage = getCmsPage(id);
        //远程调用cms,保存页面信息
        CmsPageResult cmsPageResult = cmsPageClient.saveCmsPage(cmsPage);
        if (!cmsPageResult.isSuccess()){
            //抛出异常
            return new CoursePublishResult(CommonCode.FAIL,null);
        }
        CmsPage cmsPage1 = cmsPageResult.getCmsPage();
        String pageId = cmsPage1.getPageId();
        //拼装页面预览的url
        String url = publish_previewUrl+pageId;
        //返回coursePublishResult对象(当中包含了页面预览的url)
        return new CoursePublishResult(CommonCode.SUCCESS,url);
    }
    //根据课程id获取页面信息
    private CmsPage getCmsPage(String courseId) {
        //查询课程
        CourseBase courseBase = this.findById(courseId);
        //请求cms添加页面
        //准备cmsPage的信息
        CmsPage cmsPage = new CmsPage();
        cmsPage.setSiteId(publish_siteId);//站点
        cmsPage.setTemplateId(publish_templateId);//模板
        cmsPage.setPageName(courseId +".html");//页面名称
        cmsPage.setPageAliase(courseBase.getName());//页面别名
        cmsPage.setPageWebPath(publish_pageWebPath);//页面访问路径
        cmsPage.setPagePhysicalPath(publish_pagePhysicalPath);//页面存储路径
        cmsPage.setDataUrl(publish_dataUrlPre+ courseId);//数据url
        return cmsPage;
    }

    //课程发布
    @Transactional
    public CoursePublishResult publish(String courseId) {
        //获取页面信息
        CmsPage cmsPage = this.getCmsPage(courseId);
        //远程调用cms课程一键课程发布功能，保存到服务器
        CmsPostPageResult cmsPostPageResult = cmsPageClient.postPageQuick(cmsPage);
        if (!cmsPostPageResult.isSuccess()){
            ExceptionCast.cast(CommonCode.FAIL);
        }
        //保存课程发布状态为已发布
        CourseBase courseBase = updateCourseBaseStatus(courseId);
        if (courseBase == null){
            ExceptionCast.cast(CommonCode.FAIL);
        }

        //1.保存课程索引信息
        //1.1.创建coursePub对象
        CoursePub coursePub = createCoursePub(courseId);
        //1.2.将coursePub保存到数据库
        this.saveCoursePub(courseId, coursePub);
        //缓存课程的信息
        //...
        //得到页面的url
        String pageUrl = cmsPostPageResult.getPageUrl();
        //向teachplanMediaPub中保存课程的媒资信息
        this.saveTeachPlanMediaPub(courseId);
        return new CoursePublishResult(CommonCode.SUCCESS,pageUrl);
    }
    //向teachplanMediaPub中保存课程的媒资信息
    private void  saveTeachPlanMediaPub(String courseId){
        //先删除teachplanMediaPub
        teachplanMediaPubRepository.deleteByCourseId(courseId);
        //从teachplanMedia中查询
        List<TeachplanMedia> teachplanMediaList = teachplanMediaRepository.findByCourseId(courseId);
        //将teachplanMediaList插入到teachplanMediaPub中
        List<TeachplanMediaPub> teachplanMediaPubList = teachplanMediaList.stream().map(teachplanMedia -> {
            TeachplanMediaPub teachplanMediaPub = new TeachplanMediaPub();
            BeanUtils.copyProperties(teachplanMedia, teachplanMediaPub);
            teachplanMediaPub.setTimestamp(new Date());
            return teachplanMediaPub;
        }).collect(Collectors.toList());
        teachplanMediaPubRepository.saveAll(teachplanMediaPubList);
    }

    //将coursePub保存到数据库
    private CoursePub saveCoursePub(String courseId,CoursePub coursePub){
        if (StringUtils.isEmpty(courseId)){
            ExceptionCast.cast(CourseCode.COURSE_PUBLISH_COURSEIDISNULL);
        }
        CoursePub coursePubNew = null;
        //根据课程id查询coursePub
        Optional<CoursePub> coursePubOptional = coursePubRepository.findById(courseId);
        if(coursePubOptional.isPresent()){
            coursePubNew = coursePubOptional.get();
        }else{
            coursePubNew = new CoursePub();
        }
        //将coursePub对象中的信息保存到coursePubNew中
        BeanUtils.copyProperties(coursePub,coursePubNew);
        coursePubNew.setId(courseId);
        //时间戳,给logstach使用
        coursePubNew.setTimestamp(new Date());
        //发布时间
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss");
        String date = simpleDateFormat.format(new Date());
        coursePubNew.setPubTime(date);
        coursePubRepository.save(coursePubNew);
        return coursePubNew;
    }
    //创建coursePub对象
    private CoursePub createCoursePub(String courseId){
        CoursePub coursePub = new CoursePub();
        //1.存入基础课程信息
        Optional<CourseBase> courseBaseOptional = courseBaseRepository.findById(courseId);
        if (courseBaseOptional.isPresent()){
            CourseBase courseBase = courseBaseOptional.get();
            BeanUtils.copyProperties(courseBase,coursePub);
        }
        //2.存入课程营销信息
        Optional<CourseMarket> courseMarketOptional = courseMarketRepository.findById(courseId);
        if (courseMarketOptional.isPresent()){
            CourseMarket courseMarket = courseMarketOptional.get();
            BeanUtils.copyProperties(courseMarket,coursePub);
        }
        //3.存入课程图片信息
        Optional<CoursePic> coursePicOptional = coursePicRepository.findById(courseId);
        if (coursePicOptional.isPresent()){
            CoursePic coursePic = coursePicOptional.get();
            BeanUtils.copyProperties(coursePic,coursePub);
        }
        //4.存入课程计划信息
        TeachplanNode teachplanNode = teachplanMapper.selectList(courseId);
        String jsonString = JSON.toJSONString(teachplanNode);
        //将课程计划信息json串保存到course_pub中
        coursePub.setTeachplan(jsonString);
        return coursePub;
    }
    //更改课程状态为已发布 状态码：202002
    private CourseBase updateCourseBaseStatus(String courseId){
        CourseBase one = findById(courseId);
        one.setStatus("202002");
        courseBaseRepository.save(one);
        return one;
    }
    //保存视频与课程的关联信息
    @Transactional
    public ResponseResult savemedia(TeachplanMedia teachplanMedia) {
        if (teachplanMedia==null || StringUtils.isEmpty(teachplanMedia.getTeachplanId())){
            ExceptionCast.cast(CommonCode.INVALID_PARAM);
        }
        //检验课程计划是否为三级
        String teachplanId = teachplanMedia.getTeachplanId();
        Optional<Teachplan> optional = teachplanRepository.findById(teachplanId);
        if (!optional.isPresent()){
            ExceptionCast.cast(CommonCode.INVALID_PARAM);
        }
        Teachplan teachplan = optional.get();
        String grade = teachplan.getGrade();
        if (!StringUtils.equals(grade,"3")){
            ExceptionCast.cast(CourseCode.COURSE_MEDIA_TEACHPLAN_GRADEERROR);
        }
        Optional<TeachplanMedia> teachplanMediaOptional = teachplanMediaRepository.findById(teachplanId);
        if (teachplanMediaOptional.isPresent()){
            TeachplanMedia teachplanMedia1 = teachplanMediaOptional.get();
            BeanUtils.copyProperties(teachplanMedia,teachplanMedia1);
            teachplanMediaRepository.save(teachplanMedia1);
        }else {
            //向表中插入数据
            teachplanMediaRepository.save(teachplanMedia);
        }
        return new ResponseResult(CommonCode.SUCCESS);
    }
}
