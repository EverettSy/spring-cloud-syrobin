package com.syrobin.cloud.api.gateway;

import com.syrobin.cloud.api.gateway.config.VersionLoadBalancerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;

/**
 * @author syrobin
 * @version v1.0
 * @description: API Gateway 启动类
 * @date 2022-08-05 16:32
 */
@SpringBootApplication
@EnableDiscoveryClient
//@LoadBalancerClients(defaultConfiguration = VersionLoadBalancerConfiguration.class)
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
