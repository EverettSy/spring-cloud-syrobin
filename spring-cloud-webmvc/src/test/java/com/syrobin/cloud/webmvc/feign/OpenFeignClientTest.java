package com.syrobin.cloud.webmvc.feign;

import brave.Span;
import brave.Tracer;
import com.google.common.collect.Sets;
import com.syrobin.cloud.commons.loadbalancer.TracedCircuitBreakerRoundRobinLoadBalancer;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.ConfigurationNotFoundException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.netflix.eureka.loadbalancer.LoadBalancerEurekaAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.mockito.Mockito.when;


/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-07-13 22:22
 */
//SpringRunner也包含了MockitoJUnitRunner，所以 @Mock 等注解也生效了
@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        LoadBalancerEurekaAutoConfiguration.LOADBALANCER_ZONE + "=zone1",
        //验证 thread-pool-bulkhead 相关的配置
        "management.endpoints.web.exposure.include=*",
        "feign.client.config.default.connectTimeout=500",
        "feign.client.config.default.readTimeout=2000",
        "feign.client.config." + OpenFeignClientTest.CONTEXT_ID_2 + ".readTimeout=4000",
        "resilience4j.thread-pool-bulkhead.configs.default.coreThreadPoolSize=" + OpenFeignClientTest.DEFAULT_THREAD_POOL_SIZE,
        "resilience4j.thread-pool-bulkhead.configs.default.maxThreadPoolSize=" + OpenFeignClientTest.DEFAULT_THREAD_POOL_SIZE,
        "resilience4j.thread-pool-bulkhead.configs.default.queueCapacity=1" ,
        "resilience4j.thread-pool-bulkhead.configs." + OpenFeignClientTest.CONTEXT_ID_2 + ".coreThreadPoolSize=" + OpenFeignClientTest.TEST_SERVICE_2_THREAD_POOL_SIZE,
        "resilience4j.thread-pool-bulkhead.configs." + OpenFeignClientTest.CONTEXT_ID_2 + ".maxThreadPoolSize=" + OpenFeignClientTest.TEST_SERVICE_2_THREAD_POOL_SIZE,
        "resilience4j.thread-pool-bulkhead.configs." + OpenFeignClientTest.CONTEXT_ID_2 + ".queueCapacity=1",
        "resilience4j.circuitbreaker.configs.default.failureRateThreshold=" + OpenFeignClientTest.DEFAULT_FAILURE_RATE_THRESHOLD,
        "resilience4j.circuitbreaker.configs.default.slidingWindowType=TIME_BASED",
        "resilience4j.circuitbreaker.configs.default.slidingWindowSize=5",
        "resilience4j.circuitbreaker.configs.default.minimumNumberOfCalls=" + OpenFeignClientTest.DEFAULT_MINIMUM_NUMBER_OF_CALLS,
        "resilience4j.circuitbreaker.configs." + OpenFeignClientTest.CONTEXT_ID_2 + ".failureRateThreshold=" + OpenFeignClientTest.TEST_SERVICE_2_FAILURE_RATE_THRESHOLD,
        "resilience4j.circuitbreaker.configs." + OpenFeignClientTest.CONTEXT_ID_2 + ".minimumNumberOfCalls=" + OpenFeignClientTest.TEST_SERVICE_2_MINIMUM_NUMBER_OF_CALLS,
        "resilience4j.retry.configs.default.maxAttempts=" + OpenFeignClientTest.DEFAULT_RETRY,
        "resilience4j.retry.configs.default.waitDuration=500ms",
        "resilience4j.retry.configs.default.enableRandomizedWait=true",
        "resilience4j.retry.configs.default.randomizedWaitFactor=0.5",
        "resilience4j.retry.configs." + OpenFeignClientTest.CONTEXT_ID_2 + ".maxAttempts=" + OpenFeignClientTest.TEST_SERVICE_2_RETRY,
})
@Log4j2
public class OpenFeignClientTest {
    public static final String THREAD_ID_HEADER = "Threadid";
    public static final String TEST_SERVICE_1 = "testService1";
    public static final String CONTEXT_ID_1 = "testService1Client";
    public static final int DEFAULT_THREAD_POOL_SIZE = 10;
    public static final int DEFAULT_FAILURE_RATE_THRESHOLD = 50;
    public static final int DEFAULT_MINIMUM_NUMBER_OF_CALLS = 2;
    public static final int DEFAULT_RETRY = 3;
    public static final String TEST_SERVICE_2 = "testService2";
    public static final String CONTEXT_ID_2 = "testService2Client";
    public static final int TEST_SERVICE_2_THREAD_POOL_SIZE = 5;
    public static final int TEST_SERVICE_2_FAILURE_RATE_THRESHOLD = 30;
    public static final int TEST_SERVICE_2_MINIMUM_NUMBER_OF_CALLS = 10;
    public static final int TEST_SERVICE_2_RETRY = 2;
    public static final String TEST_SERVICE_3 = "testService3";
    public static final String CONTEXT_ID_3 = "testService3Client";

    @SpyBean
    private Tracer tracer;
    @SpyBean
    private TestService1Client testService1Client;
    @SpyBean
    private TestService2Client testService2Client;
    @SpyBean
    private LoadBalancerClientFactory loadBalancerClientFactory;
    @SpyBean
    private ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry;
    @SpyBean
    private CircuitBreakerRegistry circuitBreakerRegistry;
    @SpyBean
    private RetryRegistry retryRegistry;

    @SpringBootApplication
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Configuration
    public static class App {
        @Bean
        public ApacheHttpClientAop apacheHttpClientAop() {
            return new ApacheHttpClientAop();
        }

        @Bean
        public DiscoveryClient discoveryClient() {
            ServiceInstance service1Instance1 = Mockito.spy(ServiceInstance.class);
            ServiceInstance service2Instance2 = Mockito.spy(ServiceInstance.class);
            ServiceInstance service1Instance3 = Mockito.spy(ServiceInstance.class);
            ServiceInstance service1Instance4 = Mockito.spy(ServiceInstance.class);
            Map<String, String> zone1 = Map.ofEntries(
                    Map.entry("zone", "zone1")
            );
            when(service1Instance1.getMetadata()).thenReturn(zone1);
            when(service1Instance1.getInstanceId()).thenReturn("service1Instance1");
            when(service1Instance1.getHost()).thenReturn("httpbin.org");
            when(service1Instance1.getPort()).thenReturn(80);
            when(service2Instance2.getMetadata()).thenReturn(zone1);
            when(service2Instance2.getInstanceId()).thenReturn("service2Instance2");
            when(service2Instance2.getHost()).thenReturn("httpbin.org");
            when(service2Instance2.getPort()).thenReturn(80);
            when(service1Instance3.getMetadata()).thenReturn(zone1);
            when(service1Instance3.getInstanceId()).thenReturn("service1Instance3");
            //这其实就是 httpbin.org ，为了和第一个实例进行区分加上 www
            when(service1Instance3.getHost()).thenReturn("www.httpbin.org");
            when(service1Instance3.getPort()).thenReturn(80);
            when(service1Instance4.getMetadata()).thenReturn(zone1);
            when(service1Instance4.getInstanceId()).thenReturn("service1Instance4");
            when(service1Instance4.getHost()).thenReturn("www.httpbin.org");
            //这个port连不上，测试 IOException
            when(service1Instance4.getPort()).thenReturn(18080);
            DiscoveryClient spy = Mockito.spy(DiscoveryClient.class);
            Mockito.when(spy.getInstances(TEST_SERVICE_1))
                    .thenReturn(List.of(service1Instance1, service1Instance3));
            Mockito.when(spy.getInstances(TEST_SERVICE_2))
                    .thenReturn(List.of(service2Instance2));
            Mockito.when(spy.getInstances(TEST_SERVICE_3))
                    .thenReturn(List.of(service1Instance1, service1Instance4));
            return spy;
        }

        @Bean
        public TestService1ClientFallback testService1ClientFallback() {
            return new TestService1ClientFallback();
        }
    }

    @Aspect
    public static class ApacheHttpClientAop {
        //在最后一步 ApacheHttpClient 切面
        @Pointcut("execution(* com.syrobin.cloud.webmvc.feign.ApacheHttpClient.execute(..))")
        public void annotationPointcut() {
        }

        @Around("annotationPointcut()")
        public Object around(ProceedingJoinPoint pjp) throws Throwable {
            //设置 Header，不能通过 Feign 的 RequestInterceptor，因为我们要拿到最后调用 ApacheHttpClient 的线程上下文
            Request request = (Request) pjp.getArgs()[0];
            Field headers = ReflectionUtils.findField(Request.class, "headers");
            ReflectionUtils.makeAccessible(headers);
            Map<String, Collection<String>> map = (Map<String, Collection<String>>) ReflectionUtils.getField(headers, request);
            HashMap<String, Collection<String>> stringCollectionHashMap = new HashMap<>(map);
            stringCollectionHashMap.put(THREAD_ID_HEADER, List.of(String.valueOf(Thread.currentThread().getName())));
            ReflectionUtils.setField(headers, request, stringCollectionHashMap);
            return pjp.proceed();
        }
    }

    /**
     * 验证配置生效
     */
    @Test
    public void testRetry() throws InterruptedException {
        Span span = tracer.nextSpan();
        try (Tracer.SpanInScope cleared = tracer.withSpanInScope(span)) {
            //防止断路器影响
            circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
            long l = span.context().traceId();
            TracedCircuitBreakerRoundRobinLoadBalancer loadBalancerClientFactoryInstance
                    = (TracedCircuitBreakerRoundRobinLoadBalancer) loadBalancerClientFactory.getInstance(TEST_SERVICE_1);
            AtomicInteger atomicInteger = loadBalancerClientFactoryInstance.getPositionCache().get(l);
            int start = atomicInteger.get();
            try {
                //get 方法会重试
                testService1Client.testGetRetryStatus500();
            } catch (Exception e) {
            }
            Assertions.assertEquals(DEFAULT_RETRY, atomicInteger.get() - start);

            //防止断路器影响
            circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
            start = atomicInteger.get();
            try {
                //post 方法不会重试
                testService1Client.testPostRetryStatus500();
            } catch (Exception e) {
            }
            Assertions.assertEquals(atomicInteger.get() - start, 1);

            //防止断路器影响
            circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
            loadBalancerClientFactoryInstance
                    = (TracedCircuitBreakerRoundRobinLoadBalancer) loadBalancerClientFactory.getInstance(TEST_SERVICE_2);
            atomicInteger = loadBalancerClientFactoryInstance.getPositionCache().get(l);
            start = atomicInteger.get();
            try {
                //get 方法会重试，针对 testservice 2 我们配置了不同的重试次数
                testService2Client.testGetRetryStatus500();
            } catch (Exception e) {
            }
            Assertions.assertEquals(atomicInteger.get() - start, TEST_SERVICE_2_RETRY);

            //防止断路器影响
            circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
            start = atomicInteger.get();
            try {
                //默认 post 不会重试
                testService2Client.testPostRetryStatus500();
            } catch (Exception e) {
            }
            Assertions.assertEquals(atomicInteger.get() - start, 1);

            //防止断路器影响
            circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
            start = atomicInteger.get();
            try {
                //带了注解，会重试
                testService2Client.testPostWithAnnotationRetryStatus500();
            } catch (Exception e) {
            }
            Assertions.assertEquals(atomicInteger.get() - start, TEST_SERVICE_2_RETRY);
        }
    }

    /**
     * 测试 fallback 生效
     */
    @Test
    public void testFallback() {
        for (int i = 0; i < 10; i++) {
            String s = testService1Client.testGetRetryStatus500();
            Assertions.assertEquals(s, "fallback");
        }
        Assertions.assertThrows(RetryableException.class, () -> {
            testService2Client.testGetRetryStatus500();
        });
    }

    public static class TestService1ClientFallback implements TestService1Client {

        @Override
        public HttpBinAnythingResponse anything() {
            HttpBinAnythingResponse httpBinAnythingResponse = new HttpBinAnythingResponse();
            httpBinAnythingResponse.setData("fallback");
            return httpBinAnythingResponse;
        }

        @Override
        public String testCircuitBreakerStatus500() {
            return "fallback";
        }

        @Override
        public String testGetRetryStatus500() {
            return "fallback";
        }

        @Override
        public String testPostRetryStatus500() {
            return "fallback";
        }

        @Override
        public String testGetDelayOneSecond() {
            return "fallback";
        }

        @Override
        public String testGetDelayThreeSeconds() {
            return "fallback";
        }

        @Override
        public String testPostDelayThreeSeconds() {
            return "fallback";
        }
    }

    @FeignClient(name = TEST_SERVICE_1, contextId = CONTEXT_ID_1, configuration = TestService1ClientConfiguration.class)
    public interface TestService1Client {
        @GetMapping("/anything")
        HttpBinAnythingResponse anything();

        @GetMapping("/status/500")
        String testCircuitBreakerStatus500();

        @GetMapping("/status/500")
        String testGetRetryStatus500();

        @PostMapping("/status/500")
        String testPostRetryStatus500();

        @GetMapping("/delay/1")
        String testGetDelayOneSecond();

        @GetMapping("/delay/3")
        String testGetDelayThreeSeconds();

        @PostMapping("/delay/3")
        String testPostDelayThreeSeconds();
    }

    /**
     * 验证不同服务处于不同线程
     *
     * @throws Exception
     */
    @Test
    public void testDifferentServiceWithDifferentThread() throws Exception {
        //防止断路器影响
        circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
        Thread[] threads = new Thread[100];
        AtomicBoolean passed = new AtomicBoolean(true);
        //循环100次
        for (int i = 0; i < 100; i++) {
            threads[i] = new Thread(() -> {
                Span span = tracer.nextSpan();
                try (Tracer.SpanInScope cleared = tracer.withSpanInScope(span)) {
                    HttpBinAnythingResponse response = testService1Client.anything();
                    String threadId1 = response.getHeaders().get(THREAD_ID_HEADER);
                    response = testService2Client.anything();
                    String threadId2 = response.getHeaders().get(THREAD_ID_HEADER);
                    //如果不同微服务的线程一样，则不通过
                    if (StringUtils.equalsIgnoreCase(threadId1, threadId2)) {
                        passed.set(false);
                    }
                }
            });
            threads[i].start();
        }
        for (int i = 0; i < 100; i++) {
            threads[i].join();
        }
        Assertions.assertTrue(passed.get());
    }

    @Test
    public void testDifferentThreadPoolForDifferentInstance() throws InterruptedException {
        //防止断路器影响
        circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
        Set<String> threadIds = Sets.newConcurrentHashSet();
        Thread[] threads = new Thread[100];
        //循环100次
        for (int i = 0; i < 100; i++) {
            threads[i] = new Thread(() -> {
                Span span = tracer.nextSpan();
                try (Tracer.SpanInScope cleared = tracer.withSpanInScope(span)) {
                    HttpBinAnythingResponse response = testService1Client.anything();
                    //因为 anything 会返回我们发送的请求实体的所有内容，所以我们能获取到请求的线程名称 header
                    String threadId = response.getHeaders().get(THREAD_ID_HEADER);
                    threadIds.add(threadId);
                }
            });
            threads[i].start();
        }
        for (int i = 0; i < 100; i++) {
            threads[i].join();
        }
        //确认实例 testService1Client:httpbin.org:80 线程池的线程存在
        Assertions.assertTrue(threadIds.stream().anyMatch(s -> s.contains("testService1Client:httpbin.org:80")));
        //确认实例 testService1Client:httpbin.org:80 线程池的线程存在
        Assertions.assertTrue(threadIds.stream().anyMatch(s -> s.contains("testService1Client:www.httpbin.org:80")));
    }

    /**
     * 验证配置生效
     */
    @Test
    public void testConfigureThreadPool() {
        //防止断路器影响
        circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
        //调用下这两个 FeignClient 确保对应的 NamedContext 被初始化
        testService1Client.anything();
        testService2Client.anything();
        //验证线程隔离的实际配置，符合我们的填入的配置
        ThreadPoolBulkhead threadPoolBulkhead = threadPoolBulkheadRegistry.getAllBulkheads().asJava()
                .stream().filter(t -> t.getName().contains(CONTEXT_ID_1)).findFirst().get();
        Assertions.assertEquals(threadPoolBulkhead.getBulkheadConfig().getCoreThreadPoolSize(), DEFAULT_THREAD_POOL_SIZE);
        Assertions.assertEquals(threadPoolBulkhead.getBulkheadConfig().getMaxThreadPoolSize(), DEFAULT_THREAD_POOL_SIZE);
        threadPoolBulkhead = threadPoolBulkheadRegistry.getAllBulkheads().asJava()
                .stream().filter(t -> t.getName().contains(CONTEXT_ID_2)).findFirst().get();
        Assertions.assertEquals(threadPoolBulkhead.getBulkheadConfig().getCoreThreadPoolSize(), TEST_SERVICE_2_THREAD_POOL_SIZE);
        Assertions.assertEquals(threadPoolBulkhead.getBulkheadConfig().getMaxThreadPoolSize(), TEST_SERVICE_2_THREAD_POOL_SIZE);
    }

    /**
     * 验证配置生效
     */
    @Test
    public void testConfigureCircuitBreaker() {
        //防止断路器影响
        circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
        //调用下这两个 FeignClient 确保对应的 NamedContext 被初始化
        testService1Client.anything();
        testService2Client.anything();
        //验证断路器的实际配置，符合我们的填入的配置
        List<CircuitBreaker> circuitBreakers = circuitBreakerRegistry.getAllCircuitBreakers().asJava();
        Set<String> collect = circuitBreakers.stream().map(CircuitBreaker::getName)
                .filter(name -> {
                    try {
                        return name.contains(TestService1Client.class.getMethod("anything").toGenericString())
                                || name.contains(TestService2Client.class.getMethod("anything").toGenericString());
                    } catch (NoSuchMethodException e) {
                        return false;
                    }
                }).collect(Collectors.toSet());
        Assertions.assertEquals(collect.size(), 3);
        circuitBreakers.forEach(circuitBreaker -> {
            if (circuitBreaker.getName().contains(TestService1Client.class.getName())) {
                Assertions.assertEquals((int) circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold(), (int) DEFAULT_FAILURE_RATE_THRESHOLD);
                Assertions.assertEquals(circuitBreaker.getCircuitBreakerConfig().getMinimumNumberOfCalls(), DEFAULT_MINIMUM_NUMBER_OF_CALLS);
            } else if (circuitBreaker.getName().contains(TestService2Client.class.getName())) {
                Assertions.assertEquals((int) circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold(), (int) TEST_SERVICE_2_FAILURE_RATE_THRESHOLD);
                Assertions.assertEquals(circuitBreaker.getCircuitBreakerConfig().getMinimumNumberOfCalls(), TEST_SERVICE_2_MINIMUM_NUMBER_OF_CALLS);
            }
        });
    }

    /**
     * 验证断路器是基于服务和方法打开的，也就是某个微服务的某个方法断路器打开但是不会影响这个微服务的其他方法调用
     */
    @Test
    public void testCircuitBreakerOpenBasedOnServiceAndMethod() {
        //防止断路器影响
        circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
        AtomicBoolean passed = new AtomicBoolean(false);
        for (int i = 0; i < 10; i++) {
            //多次调用会导致断路器打开，但是有 fallback 所以不会抛异常，但是断路器还是打开的
            System.out.println(testService1Client.testCircuitBreakerStatus500());
            List<CircuitBreaker> circuitBreakers = circuitBreakerRegistry.getAllCircuitBreakers().asJava();
            circuitBreakers.stream().filter(circuitBreaker -> {
                return circuitBreaker.getName().contains("testCircuitBreakerStatus500")
                        && circuitBreaker.getName().contains("TestService1Client");
            }).findFirst().ifPresent(circuitBreaker -> {
                //验证对应微服务和方法的断路器被打开
                if (circuitBreaker.getState().equals(CircuitBreaker.State.OPEN)) {
                    passed.set(true);
                }
            });
            //验证 testCircuitBreakerStatus500 的断路器打开并不会影响 anything 的调用
            HttpBinAnythingResponse anything = testService1Client.anything();
            Assertions.assertNotEquals(anything.getData(), "fallback");
        }
        Assertions.assertTrue(passed.get());
    }

    /**
     * 验证配置生效
     */
    @Test
    public void testConfigureRetry() {
        //读取所有的 Retry
        List<Retry> retries = retryRegistry.getAllRetries().asJava();
        //验证其中的配置是否符合我们填写的配置
        Map<String, Retry> retryMap = retries.stream().collect(Collectors.toMap(Retry::getName, v -> v));
        //我们初始化 Retry 的时候，使用 FeignClient 的 ContextId 作为了 Retry 的 Name
        Retry retry = retryMap.get(CONTEXT_ID_1);
        //验证 Retry 配置存在
        Assertions.assertNotNull(retry);
        //验证 Retry 配置符合我们的配置
        Assertions.assertEquals(retry.getRetryConfig().getMaxAttempts(), DEFAULT_RETRY);
        retry = retryMap.get(CONTEXT_ID_2);
        //验证 Retry 配置存在
        Assertions.assertNotNull(retry);
        //验证 Retry 配置符合我们的配置
        Assertions.assertEquals(retry.getRetryConfig().getMaxAttempts(), TEST_SERVICE_2_RETRY);
    }

    @FeignClient(name = TEST_SERVICE_2, contextId = CONTEXT_ID_2)
    public interface TestService2Client {
        @GetMapping("/anything")
        HttpBinAnythingResponse anything();

        @GetMapping("/status/500")
        String testGetRetryStatus500();

        @PostMapping("/status/500")
        String testPostRetryStatus500();

        @RetryableMethod
        @PostMapping("/status/500")
        String testPostWithAnnotationRetryStatus500();

        @GetMapping("/delay/1")
        String testGetDelayOneSecond();

        @GetMapping("/delay/3")
        String testGetDelayThreeSeconds();
    }

    public static class TestService1ClientConfiguration {
        @Bean
        public FeignDecoratorBuilderInterceptor feignDecoratorBuilderInterceptor(
                TestService1ClientFallback testService1ClientFallback
        ) {
            return builder -> {
                builder.withFallback(testService1ClientFallback);
            };
        }

        @Bean
        public TestService1ClientFallback testService1ClientFallback() {
            return new TestService1ClientFallback();
        }
    }

    @FeignClient(name = TEST_SERVICE_3, contextId = CONTEXT_ID_3)
    public interface TestService3Client {
        @PostMapping("/anything")
        HttpBinAnythingResponse anything();
    }

    @SpyBean
    private TestService3Client testService3Client;

    /**
     * 验证对于有不正常实例（正在关闭的实例，会 connect timeout）请求是否正常重试
     */
    @Test
    public void testIOExceptionRetry() {
        //防止断路器影响
        circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
        for (int i = 0; i < 10; i++) {
            Span span = tracer.nextSpan();
            try (Tracer.SpanInScope cleared = tracer.withSpanInScope(span)) {
                //不抛出异常，则正常重试了
                testService3Client.anything();
                testService3Client.anything();
            }
        }
    }

    @Test
    public void testRetryOnCircuitBreakerException() {
        //防止断路器影响
        circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
        CircuitBreaker testService1ClientInstance1Anything;
        try {
            testService1ClientInstance1Anything = circuitBreakerRegistry
                    .circuitBreaker("testService1Client:httpbin.org:80:public abstract com.github.jojotech.spring.cloud.webmvc.test.feign.HttpBinAnythingResponse com.github.jojotech.spring.cloud.webmvc.test.feign.OpenFeignClientTest$TestService1Client.anything()", "testService1Client");
        } catch (ConfigurationNotFoundException e) {
            //找不到就用默认配置
            testService1ClientInstance1Anything = circuitBreakerRegistry
                    .circuitBreaker("testService1Client:httpbin.org:80:public abstract com.github.jojotech.spring.cloud.webmvc.test.feign.HttpBinAnythingResponse com.github.jojotech.spring.cloud.webmvc.test.feign.OpenFeignClientTest$TestService1Client.anything()");
        }
        //将断路器打开
        testService1ClientInstance1Anything.transitionToOpenState();
        //调用多次，调用成功即对断路器异常重试了
        for (int i = 0; i < 10; i++) {
            this.testService1Client.anything();
        }
    }

    @Test
    public void testRetryOnBulkheadException() throws InterruptedException {
        //防止断路器影响
        circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
        this.testService1Client.anything();
        ThreadPoolBulkhead threadPoolBulkhead;
        try {
            threadPoolBulkhead = threadPoolBulkheadRegistry
                    .bulkhead("testService1Client:httpbin.org:80", "testService1Client");
        } catch (ConfigurationNotFoundException e) {
            //找不到就用默认配置
            threadPoolBulkhead = threadPoolBulkheadRegistry
                    .bulkhead("testService1Client:httpbin.org:80");
        }
        //线程队列我们配置的是 1，线程池大小是 10，这样会将线程池填充满
        for (int i = 0; i < DEFAULT_THREAD_POOL_SIZE * 2; i++) {
            try {
                threadPoolBulkhead.submit(() -> {
                    try {
                        //10s
                        TimeUnit.SECONDS.sleep(10);
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
            }
        }
        //调用多次，调用成功即对断路器异常重试了
        for (int i = 0; i < 10; i++) {
            this.testService1Client.anything();
        }
        //wait for all thread completion
        TimeUnit.SECONDS.sleep(10);
    }

    /**
     * 验证 responseTimeout 的重试特性
     * @throws InterruptedException
     */
    @Test
    public void testTimeOutAndRetry() throws InterruptedException {
        Span span = tracer.nextSpan();
        try (Tracer.SpanInScope cleared = tracer.withSpanInScope(span)) {
            //防止断路器影响
            circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
            long l = span.context().traceId();
            TracedCircuitBreakerRoundRobinLoadBalancer loadBalancerClientFactoryInstance
                    = (TracedCircuitBreakerRoundRobinLoadBalancer) loadBalancerClientFactory.getInstance(TEST_SERVICE_1);
            AtomicInteger atomicInteger = loadBalancerClientFactoryInstance.getPositionCache().get(l);
            int start = atomicInteger.get();
            //不超时，则不会有重试，也不会有异常导致 fallback
            String s = testService1Client.testGetDelayOneSecond();
            Assertions.assertNotEquals(s, "fallback");
            //没有重试，只会请求一次
            Assertions.assertEquals(1, atomicInteger.get() - start);

            //防止断路器影响
            circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
            start = atomicInteger.get();
            //超时，并且方法可以重试，所以会请求 3 次
            s = testService1Client.testGetDelayThreeSeconds();
            Assertions.assertEquals(s, "fallback");
            Assertions.assertEquals(DEFAULT_RETRY, atomicInteger.get() - start);

            //防止断路器影响
            circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
            start = atomicInteger.get();
            //超时
            s = testService1Client.testPostDelayThreeSeconds();
            Assertions.assertEquals(s, "fallback");
            //因为 post 方法默认不重试，所以只有一次
            Assertions.assertEquals(1, atomicInteger.get() - start);

            //防止断路器影响
            circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
            loadBalancerClientFactoryInstance
                    = (TracedCircuitBreakerRoundRobinLoadBalancer) loadBalancerClientFactory.getInstance(TEST_SERVICE_2);
            atomicInteger = loadBalancerClientFactoryInstance.getPositionCache().get(l);
            start = atomicInteger.get();
            //不超时
            s = testService2Client.testGetDelayOneSecond();
            Assertions.assertEquals(1, atomicInteger.get() - start);

            //防止断路器影响
            circuitBreakerRegistry.getAllCircuitBreakers().asJava().forEach(CircuitBreaker::reset);
            start = atomicInteger.get();
            //验证不同微服务配置是否生效，对于 testService2Client 不超时
            s = testService2Client.testGetDelayThreeSeconds();
            Assertions.assertEquals(1, atomicInteger.get() - start);
        }
    }
}
