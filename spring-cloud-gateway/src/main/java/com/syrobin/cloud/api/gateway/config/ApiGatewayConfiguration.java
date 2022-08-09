package com.syrobin.cloud.api.gateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.event.EnableBodyCachingEvent;
import org.springframework.cloud.gateway.filter.AdaptCachedBodyGlobalFilter;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-08-05 16:35
 */
@Configuration(proxyBeanMethods = false)
public class ApiGatewayConfiguration {

    @Autowired
    private AdaptCachedBodyGlobalFilter adaptCachedBodyGlobalFilter;

    @Autowired
    private GatewayProperties gatewayProperties;

    @PostConstruct
    public void init(){
        gatewayProperties.getRoutes().forEach(routeDefinition -> {
            //对 spring cloud gateway 路由配置中的每个路由都启用 AdaptCachedBodyGlobalFilter
            EnableBodyCachingEvent enableBodyCachingEvent = new EnableBodyCachingEvent(new Object(), routeDefinition.getId());
            adaptCachedBodyGlobalFilter.onApplicationEvent(enableBodyCachingEvent);
        });
    }
}
