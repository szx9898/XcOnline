package com.xuecheng.manage_course.controller;

import com.xuecheng.api.course.CourseControllerApi;
import com.xuecheng.framework.domain.course.*;
import com.xuecheng.framework.domain.course.ext.CourseInfo;
import com.xuecheng.framework.domain.course.ext.CourseView;
import com.xuecheng.framework.domain.course.ext.TeachplanNode;
import com.xuecheng.framework.domain.course.request.CourseListRequest;
import com.xuecheng.framework.domain.course.response.AddCourseResult;
import com.xuecheng.framework.domain.course.response.CoursePublishResult;
import com.xuecheng.framework.model.response.CommonCode;
import com.xuecheng.framework.model.response.QueryResponseResult;
import com.xuecheng.framework.model.response.ResponseResult;
import com.xuecheng.manage_course.service.CourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/course")
public class CourseController implements CourseControllerApi {
    @Autowired
    private CourseService courseService;
    @Override
    @GetMapping("/teachplan/list/{courseId}")
    public TeachplanNode findTeachplanList(@PathVariable("courseId")String courseId) {
        return courseService.findTeachplanList(courseId);
    }

    @Override
    @PostMapping("/teachplan/add")
    public ResponseResult addTeachplan(@RequestBody Teachplan teachplan) {
        courseService.addTeachplan(teachplan);
        return new ResponseResult(CommonCode.SUCCESS);
    }

    @Override
    @GetMapping("/coursebase/list/{page}/{size}")
    public QueryResponseResult<CourseInfo> findCourseList(
            @PathVariable("page")int page,
            @PathVariable("size")int size,
            CourseListRequest courseListRequest) {
        return courseService.findCourseList(page,size,courseListRequest) ;
    }

    @Override
    @PostMapping("/coursebase/add")
    public AddCourseResult addCourseBase(@RequestBody CourseBase courseBase) {
        return courseService.addCourseBase(courseBase);
    }

    @Override
    @GetMapping("/coursebase/get/{courseId}")
    public CourseBase getCourseBaseById(@PathVariable("courseId")String coureId) {
        return courseService.findById(coureId);
    }

    @Override
    @PutMapping("/coursebase/update/{id}")
    public ResponseResult updateCourseBase(@PathVariable("id")String id,@RequestBody CourseBase courseBase) {
        return courseService.updateCourseBase(id,courseBase);
    }

    @Override
    @GetMapping("/coursemarket/get/{courseid}")
    public CourseMarket getCourseMarketById(@PathVariable("courseid")String courseId) {
        return courseService.getCourseMarketById(courseId);
    }

    @Override
    @PutMapping("/coursemarket/update/{id}")
    public ResponseResult updateCourseMarket(@PathVariable("id")String id,@RequestBody CourseMarket courseMarket) {
        CourseMarket cm = courseService.updateCourseMarket(id,courseMarket);
        if (cm!=null){
            return new ResponseResult(CommonCode.SUCCESS);
        }else {
            return new ResponseResult(CommonCode.FAIL);
        }

    }

    @Override
    @PostMapping("/coursepic/add")
    public ResponseResult addCoursePic(@RequestParam("courseId") String courseId,@RequestParam("pic") String pic) {
        return courseService.addCoursePic(courseId,pic);
    }

    @Override
    @GetMapping("/coursepic/list/{courseId}")
    public CoursePic findCoursePic(@PathVariable("courseId") String courseId) {
        return courseService.findCoursePic(courseId);
    }

    @Override
    @DeleteMapping("/coursepic/delete")
    public ResponseResult deleteCoursePic(@RequestParam("courseId") String courseId) {
        return courseService.deleteCoursePic(courseId);
    }

    @Override
    @GetMapping("/courseview/{id}")
    public CourseView courseview(@PathVariable("id")String courseId) {
        return courseService.getCourseView(courseId);
    }

    @Override
    @PostMapping("/preview/{id}")
    public CoursePublishResult preview(@PathVariable("id") String id) {
        return courseService.preview(id);
    }

    @Override
    @PostMapping("/publish/{id}")
    public CoursePublishResult publish(@PathVariable("id")String courseId) {
        return courseService.publish(courseId);
    }

    @Override
    @PostMapping("/savemedia")
    public ResponseResult savemedia(@RequestBody TeachplanMedia teachplanMedia) {
        return courseService.savemedia(teachplanMedia);
    }
}
