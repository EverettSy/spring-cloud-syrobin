package com.syrobin.cloud.common.config;

import brave.Tracer;
import com.syrobin.cloud.common.loadbalancer.RoundRobinWithRequestSeparatedPositionLoadBalancer;
import com.syrobin.cloud.common.loadbalancer.SameZoneOnlyServiceInstanceListSupplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.loadbalancer.cache.LoadBalancerCacheManager;
import org.springframework.cloud.loadbalancer.config.LoadBalancerZoneConfig;
import org.springframework.cloud.loadbalancer.core.CachingServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.DiscoveryClientServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-05-27 15:33
 */
@Configuration(proxyBeanMethods = false)
public class DefaultLoadBalancerConfiguration {

    @Bean
    //有这个类代表有 spring-mvc 依赖
    //对于有 spring-mvc 依赖我们使用同步 Discovery 客户端
    @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
    public ServiceInstanceListSupplier serviceInstanceListSupplier(
            DiscoveryClient discoveryClient,
            Environment env,
            ConfigurableApplicationContext context,
            LoadBalancerZoneConfig zoneConfig
    ) {
        ObjectProvider<LoadBalancerCacheManager> cacheManagerProvider = context
                //获取缓存管理器（这里其实就是 Caffeine），不能直接注入，会有找不到 Bean 的问题，因为加载顺序不可控
                .getBeanProvider(LoadBalancerCacheManager.class);

        //这里的流程就是，首先通过 DiscoveryClientServiceInstanceListSupplier 使用 discoveryClient 获取对应微服务的实例列表
        //然后通过我们自定义的 SameZoneOnlyServiceInstanceListSupplier 进行筛选
        //最后通过 CachingServiceInstanceListSupplier 将结果缓存起来
        return
                //使用框架内置的 CachingServiceInstanceListSupplier 开启服务实例缓存，缓存需要在最外层，即缓存经过前面所有的 Supplier 筛选后的结果
                new CachingServiceInstanceListSupplier(
                        //使用我们自定义的 SameZoneOnlyServiceInstanceListSupplier，只能返回同一个 zone 的服务实例
                        new SameZoneOnlyServiceInstanceListSupplier(
                                //使用框架内置的 DiscoveryClientServiceInstanceListSupplier，通过 discoveryClient 的服务发现获取初始实例列表
                                new DiscoveryClientServiceInstanceListSupplier(discoveryClient, env),
                                zoneConfig),
                        cacheManagerProvider.getIfAvailable()
                );

    }

    @Bean
    //没有这个类代表没有 spring-mvc 依赖
    @ConditionalOnMissingClass("org.springframework.web.servlet.DispatcherServlet")
    //有这个类代表没有 spring-webflux 依赖
    @ConditionalOnClass(name = "org.springframework.web.reactive.DispatcherHandler")
    //对于只包含 spring-webflux 依赖我们使用异步 Discovery 客户端
    public ServiceInstanceListSupplier serviceInstanceListSupplierReactive(
            ReactiveDiscoveryClient reactiveDiscoveryClient,
            Environment env,
            ConfigurableApplicationContext context,
            LoadBalancerZoneConfig zoneConfig
    ) {

        ObjectProvider<LoadBalancerCacheManager> cacheManagerProvider = context
                //获取缓存管理器（这里其实就是 Caffeine），不能直接注入，会有找不到 Bean 的问题，因为加载顺序不可控
                .getBeanProvider(LoadBalancerCacheManager.class);
        //这里的流程就是，首先通过 DiscoveryClientServiceInstanceListSupplier 使用 discoveryClient 获取对应微服务的实例列表
        //然后通过我们自定义的 SameZoneOnlyServiceInstanceListSupplier 进行筛选
        //最后通过 CachingServiceInstanceListSupplier 将结果缓存起来
        return
                //使用框架内置的 CachingServiceInstanceListSupplier 开启服务实例缓存，缓存需要在最外层，即缓存经过前面所有的 Supplier 筛选后的结果
                new CachingServiceInstanceListSupplier(
                        new SameZoneOnlyServiceInstanceListSupplier(
                                //使用框架内置的 DiscoveryClientServiceInstanceListSupplier，通过 discoveryClient 的服务发现获取初始实例列表
                                new DiscoveryClientServiceInstanceListSupplier(reactiveDiscoveryClient, env),
                                zoneConfig),
                        cacheManagerProvider.getIfAvailable()
                );

    }

    @Bean
    public ReactorLoadBalancer<ServiceInstance> reactorServiceInstanceLoadBalancer(
            Environment environment,
            ServiceInstanceListSupplier serviceInstanceListSupplier,
            Tracer tracer
    ) {
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        return new RoundRobinWithRequestSeparatedPositionLoadBalancer(
                serviceInstanceListSupplier,
                name,
                tracer
        );
    }

}
