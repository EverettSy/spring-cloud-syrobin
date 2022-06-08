package com.syrobin.cloud.common.config;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.export.ConditionalOnEnabledMetricsExport;
import org.springframework.context.annotation.Configuration;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-06-01 22:22
 */
@Log4j2
@Configuration(proxyBeanMethods = false)
//需要在引入了 prometheus 并且 actuator 暴露了 prometheus 端口的情况下才加载
@ConditionalOnEnabledMetricsExport("prometheus")
public class Log4j2Configuration {

    @Autowired
    private ObjectProvider<PrometheusMeterRegistry> meterRegistry;

    //只初始化一次
    private volatile boolean initialized = false;
}
