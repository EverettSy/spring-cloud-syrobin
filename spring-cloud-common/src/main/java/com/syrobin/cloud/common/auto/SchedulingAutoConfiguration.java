package com.syrobin.cloud.common.auto;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-06-01 22:18
 */
@EnableScheduling
@Configuration(proxyBeanMethods = false)
public class SchedulingAutoConfiguration {
}
