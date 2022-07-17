package com.syrobin.cloud.common.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.jmx.RingBufferAdminMBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.export.ConditionalOnEnabledMetricsExport;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

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
    private volatile boolean isInitialized = false;


    //需要在 ApplicationContext 刷新之后进行注册
    //在加载 ApplicationContext 之前，日志配置就已经初始化好了
    //但是 prometheus 的相关 Bean 加载比较复杂，并且随着版本更迭改动比较多，所以就直接偷懒，在整个 ApplicationContext 刷新之后再注册
    // ApplicationContext 可能 refresh 多次，例如调用 /actuator/refresh，还有就是多 ApplicationContext 的场景
    // 这里为了简单，通过一个简单的 isInitialized 判断是否是第一次初始化，保证只初始化一次
    @EventListener(ContextRefreshedEvent.class)
    public synchronized void init() {
        if (!isInitialized) {
            //通过 LogManager 获取 LoggerContext，从而获取配置
            LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
            org.apache.logging.log4j.core.config.Configuration configuration = loggerContext.getConfiguration();
            //获取 LoggerContext 的名称，因为 Mbean 的名称包含这个
            String ctxName = loggerContext.getName();
            configuration.getLoggers().keySet().forEach(k -> {
                try {
                    //针对 RootLogger，它的 cfgName 是空字符串，为了显示好看，我们在 prometheus 中将它命名为 root
                    String cfgName = StringUtils.isBlank(k) ? "" : k;
                    String gaugeName = StringUtils.isBlank(k) ? "" : k;
                    Gauge.builder(gaugeName + "_logger_ring_buffer_remaining_capacity", () ->
                    {
                        try {
                            return (Number) ManagementFactory.getPlatformMBeanServer()
                                    .getAttribute(new ObjectName(
                                            //按照 Log4j2 源码中的命名方式组装名称
                                            String.format(RingBufferAdminMBean.PATTERN_ASYNC_LOGGER_CONFIG, cfgName, ctxName)
                                            //获取剩余大小，注意这个是严格区分大小写的
                                    ), "RemainingCapacity");
                        } catch (Exception e) {
                            log.error("get {} ring buffer remaining size error", k, e);
                        }
                        return -1;
                    }).register(meterRegistry.getIfAvailable());
                } catch (Exception e) {
                    log.error("Log4j2Configuration-init error: {}", e.getMessage(), e);
                }
            });
            isInitialized = true;
        }
    }
}
