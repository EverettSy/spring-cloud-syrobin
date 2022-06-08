package com.syrobin.cloud.common.auto;

import com.syrobin.cloud.common.config.Log4j2Configuration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-06-01 22:20
 */
@Configuration(proxyBeanMethods = false)
@Import({Log4j2Configuration.class})
@AutoConfigureAfter(PrometheusMetricsExportAutoConfiguration.class)
public class Log4j2AutoConfiguration {
}
