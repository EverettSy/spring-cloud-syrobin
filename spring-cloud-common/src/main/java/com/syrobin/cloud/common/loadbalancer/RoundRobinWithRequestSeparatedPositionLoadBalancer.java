package com.syrobin.cloud.common.loadbalancer;

import brave.Span;
import brave.Tracer;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.log4j.Log4j2;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author syrobin
 * @version v1.0
 * @description: 一定必须是实现ReactorServiceInstanceLoadBalancer
 * 而不是ReactorLoadBalancer<ServiceInstance>
 * 因为注册的时候是ReactorServiceInstanceLoadBalancer
 * @date 2022-05-27 17:22
 */
@Log4j2
public class RoundRobinWithRequestSeparatedPositionLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private final ServiceInstanceListSupplier serviceInstanceListSupplier;
    //每次请求算上重试不会超过1分钟
    //对于超过1分钟的，这种请求肯定比较重，不应该重试
    private final LoadingCache<Long, AtomicInteger> positionCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            //随机初始值，防止每次都是从第一个开始调用
            .build(k -> new AtomicInteger(ThreadLocalRandom.current().nextInt(0, 1000)));
    private final String serviceId;
    private final Tracer tracer;

    @VisibleForTesting
    public LoadingCache<Long, AtomicInteger> getPositionCache() {
        return positionCache;
    }

    public RoundRobinWithRequestSeparatedPositionLoadBalancer(ServiceInstanceListSupplier serviceInstanceListSupplier, String serviceId, Tracer tracer) {
        this.serviceInstanceListSupplier = serviceInstanceListSupplier;
        this.serviceId = serviceId;
        this.tracer = tracer;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        //在异步环境下，很可能这里的 span 不能和下面 Mono 流中的保持一致，这里需要保持一致，才能保证正确性
        Span span = tracer.currentSpan();
        return serviceInstanceListSupplier.get().next()
                .map(serviceInstances -> {
                    //保持 span 和调用 choose 的 span 一样
                    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
                        return getInstanceResponse(serviceInstances);
                    }
                });
    }

    private Response<ServiceInstance> getInstanceResponse(List<ServiceInstance> serviceInstances) {

        if (serviceInstances.isEmpty()) {
            log.warn("No servers available for service: " + this.serviceId);
            return new EmptyResponse();
        }
        return getInstanceResponseByRoundRobin(serviceInstances);
    }

    private Response<ServiceInstance> getInstanceResponseByRoundRobin(List<ServiceInstance> serviceInstances) {
        if (serviceInstances.isEmpty()) {
            log.warn("No servers available for service: " + this.serviceId);
            return new EmptyResponse();
        }
        //为了解决原始算法不同调用并发可能导致一个请求重试相同的实例
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            currentSpan = tracer.newTrace();
        }
        long l = currentSpan.context().traceId();
        AtomicInteger seed = positionCache.get(l);
        int s = seed.getAndIncrement();
        int pos = s % serviceInstances.size();
        log.info("position {}, seed: {}, instances count: {}", pos, s, serviceInstances.size());
        return new DefaultResponse(serviceInstances.stream()
                //实例返回列表顺序可能不同，为了保持一致，先排序再取
                .sorted(Comparator.comparing(ServiceInstance::getInstanceId))
                .collect(Collectors.toList()).get(pos));
    }

}
