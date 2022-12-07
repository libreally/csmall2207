package cn.tedu.mall.seckill.timer.config;

import cn.tedu.mall.seckill.timer.job.SeckillInitialJob;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {
    @Bean
    public JobDetail initJobDetail(){
        return JobBuilder.newJob(SeckillInitialJob.class)
                .withIdentity("initJobDetail")
                .storeDurably()
                .build();
    }
    @Bean
    public Trigger initTrigger(){
        // 声明Cron表达式,定义触发时间
        CronScheduleBuilder cron=
                CronScheduleBuilder.cronSchedule("0 0/1 * * * ?");
        return TriggerBuilder.newTrigger()
                // 绑定要运行的JobDetail对象
                .forJob(initJobDetail())
                // 当前触发器也要起名字,名字也不要重复
                .withIdentity("initTrigger")
                // 绑定cron表达式
                .withSchedule(cron)
                .build();
    }
}
