package com.xuecheng.order.mq;

import com.xuecheng.framework.domain.task.XcTask;
import com.xuecheng.order.config.RabbitMQConfig;
import com.xuecheng.order.service.TaskService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

@Component
public class ChooseCourseTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChooseCourseTask.class);
    @Autowired
    TaskService taskService;

    @RabbitListener(queues = RabbitMQConfig.XC_LEARNING_FINISHADDCHOOSECOURSE)
    public void receiveChoosecourseTask(XcTask xcTask){
        if (xcTask!=null && StringUtils.isNotEmpty(xcTask.getId())){
            taskService.finishTask(xcTask.getId());
        }

    }
    //定时发送添加选课任务
    @Scheduled(cron = "0/3 * * * * *")
    public void sendChooseCourseTask(){
        //得到1分钟之前的时间
        Calendar calendar = new GregorianCalendar();
        calendar.setTime(new Date());
        calendar.set(GregorianCalendar.MINUTE,-1);
        Date time = calendar.getTime();
        List<XcTask> xcTaskList = taskService.findXcTaskList(time, 100);
        System.out.println(xcTaskList);
        //调用service发送消息，将添加选课的任务发送给mq
        for (XcTask xcTask : xcTaskList) {
            //取任务
            if (taskService.getTask(xcTask.getId(),xcTask.getVersion())>0){
                String mqExchange = xcTask.getMqExchange();//交换机
                String mqRoutingkey = xcTask.getMqRoutingkey();//routingkey
                taskService.publish(xcTask,mqExchange,mqRoutingkey);
            }
        }
    }
    //定义任务调试策略
    //@Scheduled(cron = "0/3 * * * * *")//每隔三秒去执行
    //@Scheduled(fixedRate = 3000)//在任务执行后三秒执行下一次调度
    //@Scheduled(fixedDelay = 3000)//在任务家属三秒后才开始执行
    public void task1(){
        LOGGER.info("========定时任务1开始=======");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOGGER.info("========定时任务1结束=======");
    }
    //@Scheduled(fixedRate = 3000)//在任务执行后三秒执行下一次调度
    //@Scheduled(fixedDelay = 3000)//在任务家属三秒后才开始执行
    public void task2(){
        LOGGER.info("========定时任务2开始=======");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOGGER.info("========定时任务2结束=======");
    }
}
