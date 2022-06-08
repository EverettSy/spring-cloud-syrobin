package com.syrobin.cloud.common.auto;

import com.syrobin.cloud.common.config.DefaultLoadBalancerConfiguration;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Configuration;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-05-27 15:31
 */
@Configuration(proxyBeanMethods = false)
@LoadBalancerClients(defaultConfiguration = DefaultLoadBalancerConfiguration.class)
public class LoadBalancerAutoConfiguration {
}
