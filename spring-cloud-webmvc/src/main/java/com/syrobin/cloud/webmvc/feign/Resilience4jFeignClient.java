package com.syrobin.cloud.webmvc.feign;

import brave.Span;
import brave.Tracer;
import com.alibaba.fastjson.JSON;
import com.syrobin.cloud.commons.resilience4j.Resilience4jUtil;
import com.syrobin.cloud.webmvc.misc.SpecialHttpStatus;
import feign.Client;
import feign.Request;
import feign.Response;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.openfeign.FeignClient;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-07-13 15:23
 */
@Slf4j
public class Resilience4jFeignClient implements Client {

    private final ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Tracer tracer;
    private ApacheHttpClient apacheHttpClient;

    public Resilience4jFeignClient(ApacheHttpClient apacheHttpClient,
                                   ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry,
                                   CircuitBreakerRegistry circuitBreakerRegistry,
                                   Tracer tracer

    ) {
        this.apacheHttpClient = apacheHttpClient;
        this.threadPoolBulkheadRegistry = threadPoolBulkheadRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.tracer = tracer;
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        FeignClient annotation = request.requestTemplate().methodMetadata().method().getDeclaringClass().getAnnotation(FeignClient.class);
        //和Retry 保持一致，使用contextId,而不是微服务名称
        String contextId = annotation.contextId();
        //获取实例唯一id
        String serviceInstanceId = getServiceInstanceId( request);
        //获取实例+方法唯一id
        String serviceInstanceMethodId = getServiceInstanceMethodId(request);

        ThreadPoolBulkhead threadPoolBulkhead;
        CircuitBreaker circuitBreaker;
        try {
            //每个实例一个线程池
            threadPoolBulkhead = threadPoolBulkheadRegistry.bulkhead(contextId + ":" + serviceInstanceId, contextId);
        } catch (Exception e) {
            threadPoolBulkhead = threadPoolBulkheadRegistry.bulkhead( contextId + ":" + serviceInstanceId);
        }
        try {
            ///每个服务实例具体方法一个resilience4j熔断记录器，在服务实例具体方法维度做熔断，所有这个服务的实例具体方法共享这个服务的resilience4j熔断配置
            circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceInstanceMethodId, contextId);
        } catch (Exception e) {
            circuitBreaker = circuitBreakerRegistry.circuitBreaker(serviceInstanceMethodId);

        }

        //保持traceId
        Span span = tracer.currentSpan();
        ThreadPoolBulkhead finalThreadPoolBulkhead = threadPoolBulkhead;
        CircuitBreaker finalCircuitBreaker = circuitBreaker;
        Supplier<CompletionStage<Response>> completionStageSupplier = ThreadPoolBulkhead.decorateSupplier(threadPoolBulkhead,
                OpenfeignUtil.decorateSupplier(circuitBreaker, () -> {
                    try (Tracer.SpanInScope cleared = tracer.withSpanInScope(span)) {
                        log.info("call url: {} -> {}, ThreadPoolStats({}): {}, CircuitBreakStats({}): {}",
                                request.httpMethod(),
                                request.url(),
                                serviceInstanceId,
                                JSON.toJSONString(finalThreadPoolBulkhead.getMetrics()),
                                serviceInstanceMethodId,
                                JSON.toJSONString(finalCircuitBreaker.getMetrics())
                        );
                        Response execute = apacheHttpClient.execute(request, options);
                        log.info("response: {} - {}", execute.status(), execute.reason());
                        return execute;
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                })
        );

        ServiceInstance serviceInstance = getServiceInstance(request);
        try {
            Response response = Try.ofSupplier(completionStageSupplier).get().toCompletableFuture().join();
            return response;
        } catch (BulkheadFullException e) {
            //线程池限流异常
            return Response.builder()
                    .request(request)
                    .status(SpecialHttpStatus.BULKHEAD_FULL.getValue())
                    .reason(e.getLocalizedMessage())
                    .requestTemplate(request.requestTemplate()).build();
        } catch (CompletionException e) {
            //内部抛出的所有异常都被封装了一层 CompletionException，所以这里需要取出里面的 Exception
            Throwable cause = e.getCause();
            //对于断路器打开，返回对应特殊的错误码
            if (cause instanceof CallNotPermittedException) {
                return Response.builder()
                        .request(request)
                        .status(SpecialHttpStatus.CIRCUIT_BREAKER_ON.getValue())
                        .reason(cause.getLocalizedMessage())
                        .requestTemplate(request.requestTemplate()).build();
            }
            //对于 IOException，需要判断是否请求已经发送出去了
            //对于 connect time out 的异常，则可以重试，因为请求没发出去，但是例如 read time out 则不行，因为请求已经发出去了
            if (cause instanceof IOException) {
                String message = cause.getMessage().toLowerCase();
                boolean containsRead = message.contains("read") || message.contains("respon");
                if (containsRead) {
                    log.info("{}-{} exception contains read, which indicates the request has been sent", e.getMessage(), cause.getMessage());
                    //如果是 read 异常，则代表请求已经发了出去，则不能重试（除非是 GET 请求或者有 RetryableMethod 注解，这个在 DefaultErrorDecoder 判断）
                    return Response.builder()
                            .request(request)
                            .status(SpecialHttpStatus.NOT_RETRYABLE_IO_EXCEPTION.getValue())
                            .reason(cause.getLocalizedMessage())
                            .requestTemplate(request.requestTemplate()).build();
                } else {
                    return Response.builder()
                            .request(request)
                            .status(SpecialHttpStatus.RETRYABLE_IO_EXCEPTION.getValue())
                            .reason(cause.getLocalizedMessage())
                            .requestTemplate(request.requestTemplate()).build();
                }
            }
            throw e;
        }
    }

    private ServiceInstance getServiceInstance(Request request) throws MalformedURLException {
        URL url = new URL(request.url());
        DefaultServiceInstance defaultServiceInstance = new DefaultServiceInstance();
        defaultServiceInstance.setHost(url.getHost());
        defaultServiceInstance.setPort(url.getPort());
        return defaultServiceInstance;
    }

    private String getServiceInstanceId(Request request) throws MalformedURLException {
        URL url = new URL(request.url());
        return Resilience4jUtil.getServiceInstance(url);
    }

    private String getServiceInstanceMethodId(Request request) throws MalformedURLException {
        URL url = new URL(request.url());
        //通过微服务名称 + 实例 + 方法的方式，获取唯一id
        return Resilience4jUtil.getServiceInstanceMethodId(url, request.requestTemplate().methodMetadata().method());
    }
}
