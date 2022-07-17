package com.syrobin.cloud.webmvc.feign;

import feign.Client;
import feign.Request;
import feign.Response;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;

import java.io.IOException;

/**
 * @author syrobin
 * @version v1.0
 * @description: 由于初始化 FeignBlockingLoadBalancerClient 需要 LoadBalancerClient
 * 但是由于 Spring Cloud 2020 之后，Spring Cloud LoadBalancer BlockingClient 的加载，强制加入了顺序
 * @date 2022-07-12 17:32
 * @see org.springframework.cloud.loadbalancer.config.BlockingLoadBalancerClientAutoConfiguration
 * 这个自动配置加入了 @AutoConfigureAfter(LoadBalancerAutoConfiguration.class)
 * 导致我们在初始化的 FeignClient 的时候，无法拿到 BlockingClient
 * 所以，需要通过 ObjectProvider 封装 LoadBalancerClient，在真正调用 FeignClient 的时候通过 ObjectProvider 拿到 LoadBalancerClient 来创建 FeignBlockingLoadBalancerClient
 */
public class FeignBlockingLoadBalancerClientDelegate implements Client {

    private FeignBlockingLoadBalancerClient feignBlockingLoadBalancerClient;

    private final Client delegate;
    private final ObjectProvider<LoadBalancerClient> loadBalancerClientProvider;
    private final LoadBalancerProperties properties;
    private final LoadBalancerClientFactory loadBalancerClientFactory;

    public FeignBlockingLoadBalancerClientDelegate(
            Client delegate,
            ObjectProvider<LoadBalancerClient> loadBalancerClientProvider,
            LoadBalancerProperties properties,
            LoadBalancerClientFactory loadBalancerClientFactory) {
        this.delegate = delegate;
        this.loadBalancerClientProvider = loadBalancerClientProvider;
        this.properties = properties;
        this.loadBalancerClientFactory = loadBalancerClientFactory;
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        if (feignBlockingLoadBalancerClient == null) {
            synchronized (this) {
                if (feignBlockingLoadBalancerClient == null) {
                    feignBlockingLoadBalancerClient = new FeignBlockingLoadBalancerClient(
                            this.delegate,
                            this.loadBalancerClientProvider.getIfAvailable(),
                            this.properties,
                            this.loadBalancerClientFactory
                    );
                }
            }
        }
        return feignBlockingLoadBalancerClient.execute(request, options);

    }
}
