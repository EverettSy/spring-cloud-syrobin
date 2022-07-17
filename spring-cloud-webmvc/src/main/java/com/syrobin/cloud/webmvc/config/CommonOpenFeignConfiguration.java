package com.syrobin.cloud.webmvc.config;

import brave.Tracer;
import com.syrobin.cloud.webmvc.feign.ApacheHttpClient;
import com.syrobin.cloud.webmvc.feign.FeignBlockingLoadBalancerClientDelegate;
import com.syrobin.cloud.webmvc.feign.Resilience4jFeignClient;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-06-23 17:42
 */
@Configuration(proxyBeanMethods = false)
public class CommonOpenFeignConfiguration {

    @Bean
    public HttpClient getHttpClient() {
        //长链接保持5分钟
        PoolingHttpClientConnectionManager poolingConnectionManager = new PoolingHttpClientConnectionManager(5, TimeUnit.MINUTES);
        //最大连接数
        poolingConnectionManager.setMaxTotal(1000);
        //同路由的并发数
        poolingConnectionManager.setDefaultMaxPerRoute(1000);

        //默认的请求配置
        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        httpClientBuilder.setConnectionManager(poolingConnectionManager);
        //保持长连接配置，需要在头添加Keep-Alive
        httpClientBuilder.setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy());
        return httpClientBuilder.build();
    }

    @Bean
    public ApacheHttpClient apacheHttpClient(HttpClient httpClient) {
        return new ApacheHttpClient(httpClient);
    }

    /**
     *
     * @param apacheHttpClient
     * @param loadBalancerClientProvider 为何使用 ObjectProvider 请参考 FeignBlockingLoadBalancerClientDelegate 的注释
     * @param threadPoolBulkheadRegistry
     * @param circuitBreakerRegistry
     * @param tracer
     * @param properties
     * @param loadBalancerClientFactory
     * @return FeignBlockingLoadBalancerClientDelegate 为何使用这个不直接用 FeignBlockingLoadBalancerClient 请参考 FeignBlockingLoadBalancerClientDelegate 的注释
     */
    @Bean
    @Primary
    public FeignBlockingLoadBalancerClientDelegate feignBlockingLoadBalancerCircuitBreakableClient(
            ApacheHttpClient apacheHttpClient,
            ObjectProvider<LoadBalancerClient> loadBalancerClientProvider,
            ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry,
            Tracer tracer,
            LoadBalancerProperties properties,
            LoadBalancerClientFactory loadBalancerClientFactory ) {
        return new FeignBlockingLoadBalancerClientDelegate(
                new Resilience4jFeignClient(
                        apacheHttpClient,
                        threadPoolBulkheadRegistry,
                        circuitBreakerRegistry,
                        tracer
                ),
                loadBalancerClientProvider,
                properties,
                loadBalancerClientFactory
        );
    }
}
