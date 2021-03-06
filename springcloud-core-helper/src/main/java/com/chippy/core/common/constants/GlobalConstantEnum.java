package com.chippy.core.common.constants;

/**
 * 全局常量枚举
 *
 * @author chippy
 */
public enum GlobalConstantEnum {

    SPRING_SCHEDULED_ASSIGN_SERVER("spring.scheduled.assign-server", "SpringScheduled指定的执行服务"),
    ELASTIC_JOB_REGCENTER_SERVER_LIST("elastic-job.reg-center.server-list", "ZK服务链接地址IP"),
    ELASTIC_JOB_REGCENTER_NAMESPACE("elastic-job.reg-center.namespace", "ZK名命空间"),
    ELASTIC_JOB_REGCENTER_MAX_TETRIES("elastic-job.reg-center.max-retries", "ZK最大重试次数"),
    ELASTIC_JOB_REGCENTER_SESSION_TIMEOUT_MS("elastic-job.reg-center.session-timeout-ms", "ZK最大睡眠时间"),
    ELASTIC_JOB_REGCENTER_MAX_SLEEP_TIME_MS("elastic-job.reg-center.max-sleep-time-ms", "ZK操作超时时间"),
    ELASTIC_JOB_FAIL_RETRY_SERVER_IP("elastic-job.fail-retry-server-ip", "ElasticJob重启丢失任务重试服务IP地址"),
    ELASTIC_JOB_INFO_FILED_STATUS("status", "任务信息状态字段"),
    ELASTIC_JOB_INFO_FILED_ORIGINAL_NAME("originalJobName", "原任务名称字段"),
    ELASTIC_JOB_INFO_FILED_SERVER("server", "服务名称"),
    ;

    private final String constantValue;
    private final String constantDesc;

    private GlobalConstantEnum(String constantValue, String constantDesc) {
        this.constantValue = constantValue;
        this.constantDesc = constantDesc;
    }

    public String getConstantValue() {
        return constantValue;
    }

    public String getConstantDesc() {
        return constantDesc;
    }
}
