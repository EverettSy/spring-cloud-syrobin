package com.syrobin.cloud.api.gateway.route;

import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import reactor.core.publisher.Flux;

/**
 * @author syrobin
 * @version v1.0
 * @description: 从配置文件中加载路由信息
 * @date 2023-03-24 16:12
 */
public class PropertiesRouteDefinitionLocator implements RouteDefinitionLocator {

    private final GatewayProperties gatewayProperties;

    public PropertiesRouteDefinitionLocator(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        /*获取GatewayProperties中的routes属性*/
        return Flux.fromIterable(this.gatewayProperties.getRoutes());
    }
}
