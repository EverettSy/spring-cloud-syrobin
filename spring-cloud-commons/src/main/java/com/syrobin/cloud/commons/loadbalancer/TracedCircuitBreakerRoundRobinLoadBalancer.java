package com.syrobin.cloud.commons.loadbalancer;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import brave.Span;
import brave.Tracer;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.syrobin.cloud.commons.resilience4j.CircuitBreakerExtractor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Mono;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;

//一定必须是实现ReactorServiceInstanceLoadBalancer
//而不是ReactorLoadBalancer<ServiceInstance>
//因为注册的时候是ReactorServiceInstanceLoadBalancer
@Log4j2
@NoArgsConstructor//仅仅为了单元测试
public class TracedCircuitBreakerRoundRobinLoadBalancer implements ReactorServiceInstanceLoadBalancer {
    private ServiceInstanceListSupplier serviceInstanceListSupplier;
    //每次请求算上重试不会超过3分钟
    //对于超过3分钟的，这种请求肯定比较重，不应该重试
    private final LoadingCache<Long, AtomicInteger> positionCache = Caffeine.newBuilder()
            .expireAfterWrite(3, TimeUnit.MINUTES)
            //随机初始值，防止每次都是从第一个开始调用
            .build(k -> new AtomicInteger(ThreadLocalRandom.current().nextInt(0, 1000)));
    private final LoadingCache<Long, Set<String>> calledIpPrefixes = Caffeine.newBuilder()
            .expireAfterAccess(3, TimeUnit.MINUTES)
            .build(k -> Sets.newConcurrentHashSet());
    private final LoadingCache<Long, Set<String>> calledIps = Caffeine.newBuilder()
            .expireAfterAccess(3, TimeUnit.MINUTES)
            .build(k -> Sets.newConcurrentHashSet());
    private final LoadingCache<String, AtomicLong> numberOfReturnedByLoadBalancer = Caffeine.newBuilder()
            .expireAfterAccess(3, TimeUnit.MINUTES)
            .build(k -> new AtomicLong(0L));
    private String serviceId;
    private Tracer tracer;
    private CircuitBreakerExtractor circuitBreakerExtractor;
    private CircuitBreakerRegistry circuitBreakerRegistry;

    public void setServiceInstanceListSupplier(ServiceInstanceListSupplier serviceInstanceListSupplier) {
        this.serviceInstanceListSupplier = serviceInstanceListSupplier;
    }

    public void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    public void setCircuitBreakerExtractor(CircuitBreakerExtractor circuitBreakerExtractor) {
        this.circuitBreakerExtractor = circuitBreakerExtractor;
    }

    public void setCircuitBreakerRegistry(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @VisibleForTesting
    public LoadingCache<Long, AtomicInteger> getPositionCache() {
        return positionCache;
    }

	@VisibleForTesting
	LoadingCache<Long, Set<String>> getCalledIpPrefixes() {
		return calledIpPrefixes;
	}

	public TracedCircuitBreakerRoundRobinLoadBalancer(
            ServiceInstanceListSupplier serviceInstanceListSupplier, String serviceId, Tracer tracer,
            CircuitBreakerExtractor circuitBreakerExtractor,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.serviceInstanceListSupplier = serviceInstanceListSupplier;
        this.serviceId = serviceId;
        this.tracer = tracer;
        this.circuitBreakerExtractor = circuitBreakerExtractor;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        Span span = tracer.currentSpan();
        return serviceInstanceListSupplier.get().next()
                .map(serviceInstances -> {
                    //保持 span 和调用 choose 的 span 一样
                    try (Tracer.SpanInScope cleared = tracer.withSpanInScope(span)) {
                        return getInstanceResponse(serviceInstances, request);
                    }
                });
    }

    private Response<ServiceInstance> getInstanceResponse(List<ServiceInstance> serviceInstances, Request request) {
        if (serviceInstances.isEmpty()) {
            log.warn("No servers available for service: " + this.serviceId);
            return new EmptyResponse();
        }
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            currentSpan = tracer.newTrace();
        }
        long l = currentSpan.context().traceId();
        serviceInstances = serviceInstances.stream().distinct().collect(Collectors.toList());;
        Map<ServiceInstance, CircuitBreaker> serviceInstanceCircuitBreakerMap = serviceInstances.stream()
                .collect(Collectors.toMap(serviceInstance -> serviceInstance, v -> {
                    return circuitBreakerExtractor
                            .getCircuitBreaker(circuitBreakerRegistry, request, v.getHost(), v.getPort());
                }));
        return getInstanceResponseByRoundRobin(l, serviceInstances, serviceInstanceCircuitBreakerMap);
    }

    @VisibleForTesting
    public Response<ServiceInstance> getInstanceResponseByRoundRobin(long traceId, List<ServiceInstance> serviceInstances, Map<ServiceInstance, CircuitBreaker> serviceInstanceCircuitBreakerMap) {
        Collections.shuffle(serviceInstances);
        //需要先将所有参数缓存起来，否则 comparator 会调用多次，并且可能在排序过程中参数发生改变
        Map<ServiceInstance, Integer> used = Maps.newHashMap();
        Map<ServiceInstance, Long> counts = Maps.newHashMap();
        serviceInstances = serviceInstances.stream().sorted(
                Comparator
                        //之前已经调用过的 ip，这里排后面
                        .<ServiceInstance>comparingInt(serviceInstance -> {
                            return used.computeIfAbsent(serviceInstance, k -> {
                                return calledIps.get(traceId).stream().anyMatch(prefix -> {
                                    return serviceInstance.getHost().equalsIgnoreCase(prefix);
                                }) ? 1 : 0;
                            });
                        })
                        //之前已经调用过的网段，这里排后面
                        .thenComparingInt(serviceInstance -> {
                            return used.computeIfAbsent(serviceInstance, k -> {
                                return calledIpPrefixes.get(traceId).stream().anyMatch(prefix -> {
                                    return serviceInstance.getHost().contains(prefix);
                                }) ? 1 : 0;
                            });
                        })
                        //当前断路器没有打开的优先
                        .thenComparingInt(serviceInstance -> {
                            return serviceInstanceCircuitBreakerMap.get(serviceInstance).getState().getOrder();
                        })
                        //当前错误率最少的
                        .thenComparingDouble(serviceInstance -> {
                            return serviceInstanceCircuitBreakerMap.get(serviceInstance).getMetrics().getFailureRate();
                        })
                        //由于断路器数据和负载均衡之间更新不在一起，有一定延迟，为了防止短时间内（几百毫秒内）收到太多请求，导致全都发到同一个实例上
                        //加上这个所有实例负载均衡器选择并返回的次数，选择返回最少的，但是要在断路器以及错误判断之后，我们还是期望发到错误最少的
                        .thenComparingLong(serviceInstance -> {
                            return counts.computeIfAbsent(serviceInstance, k -> {
                                return numberOfReturnedByLoadBalancer.get(k.toString()).get();
                            });
                        })
                        //当前负载请求最少的
                        .thenComparingLong(serviceInstance -> {
                            return serviceInstanceCircuitBreakerMap.get(serviceInstance).getMetrics().getNumberOfBufferedCalls();
                        })
        ).collect(Collectors.toList());
        if (serviceInstances.isEmpty()) {
            log.warn("No servers available for service: " + this.serviceId);
            return new EmptyResponse();
        }
        ServiceInstance serviceInstance = serviceInstances.get(0);
        //记录本次返回的网段
        calledIpPrefixes.get(traceId).add(serviceInstance.getHost().substring(0, serviceInstance.getHost().lastIndexOf(".")));
        calledIps.get(traceId).add(serviceInstance.getHost());
        //目前记录这个只为了兼容之前的单元测试（调用次数测试）
        positionCache.get(traceId).getAndIncrement();
        AtomicLong atomicLong = numberOfReturnedByLoadBalancer.get(serviceInstance.toString());
        long increment = atomicLong.getAndIncrement();
        if (increment < 0) {
            atomicLong.set(0L);
        }
        return new DefaultResponse(serviceInstance);
    }
}
