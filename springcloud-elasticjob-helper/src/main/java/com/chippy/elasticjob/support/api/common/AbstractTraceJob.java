package com.chippy.elasticjob.support.api.common;

import cn.hutool.json.JSONUtil;
import com.chippy.elasticjob.support.api.db.IJobInfoService;
import com.chippy.elasticjob.support.api.db.redis.JobInfo;
import com.chippy.elasticjob.support.domain.enums.JobStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.elasticjob.simple.job.SimpleJob;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;

/**
 * 抽象实现状态跟踪记录类型任务
 * 实现该类一定要确保任务是实现{@link AbstractTraceJobProcessor}，以保证任务状态跟踪记录
 * 一定要确保实现{@link ElasticJobBusinessProcessor}，以保证任务进行相关业务操作
 *
 * @author: chippy
 * @datetime 2020-11-16 15:00
 */
@Slf4j
public abstract class AbstractTraceJob<T> implements SimpleJob {

    private static final String LOG_TEMPLATE = "通用定时任务功能实现-%s";

    @Resource
    private IJobInfoService jobInfoService;

    @Autowired
    private ElasticJobBusinessProcessor<T> elasticJobBusinessProcessor;

    protected abstract Class<T> getGenericClass();

    protected void doExecute(String jobName, String jobParameter) {
        try {
            final JobInfo jobInfo = jobInfoService.byJobName(jobName, JobStatusEnum.ING);
            if (null == jobInfo) {
                if (log.isErrorEnabled()) {
                    log.error("任务[" + jobName + "]状态已被修改，本次任务不做任何处理");
                }
                // TODO 记录一下ERROR MSG
                return;
            }
            T data = JSONUtil.toBean(jobParameter, this.getGenericClass());
            elasticJobBusinessProcessor.processCronJob(data);
        } catch (Exception e) {
            String exceptionMessage = "异常信息-ex:[" + e.getMessage() + "]";
            log.error(String.format(LOG_TEMPLATE, exceptionMessage));
            throw e;
        }
    }

}
