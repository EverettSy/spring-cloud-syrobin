package com.syrobin.cloud.webmvc.loadbalancer;

import brave.Span;
import brave.Tracer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.cloud.netflix.eureka.loadbalancer.LoadBalancerEurekaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.when;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-07-17 22:15
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {LoadBalancerEurekaAutoConfiguration.LOADBALANCER_ZONE + "=zone1"})
public class LoadBalancerTest {

    @EnableAutoConfiguration
    @Configuration
    public static class App {
        @Bean
        public DiscoveryClient myDiscoveryClient() {
            ServiceInstance zone1Instance1 = Mockito.spy(ServiceInstance.class);
            ServiceInstance zone1Instance2 = Mockito.spy(ServiceInstance.class);
            ServiceInstance zone2Instance3 = Mockito.spy(ServiceInstance.class);
            Map<String, String> zone1 = Map.ofEntries(
                    Map.entry("zone", "zone1")
            );
            Map<String, String> zone2 = Map.ofEntries(
                    Map.entry("zone", "zone2")
            );
            when(zone1Instance1.getMetadata()).thenReturn(zone1);
            when(zone1Instance1.getInstanceId()).thenReturn("instance1");
            when(zone1Instance2.getMetadata()).thenReturn(zone1);
            when(zone1Instance2.getInstanceId()).thenReturn("instance2");
            when(zone2Instance3.getMetadata()).thenReturn(zone2);
            when(zone2Instance3.getInstanceId()).thenReturn("instance3");
            DiscoveryClient spy = Mockito.spy(DiscoveryClient.class);
            when(spy.getInstances("testService"))
                    .thenReturn(List.of(zone1Instance1, zone1Instance2, zone2Instance3));
            return spy;
        }
    }

    @SpyBean
    private LoadBalancerClientFactory loadBalancerClientFactory;
    @SpyBean
    private Tracer tracer;

    /**
     * 只返回同一个 zone 下的实例
     */
    @Test
    public void testFilteredByZone() {
        ReactiveLoadBalancer<ServiceInstance> testService =
                loadBalancerClientFactory.getInstance("testService");
        for (int i = 0; i < 100; i++) {
            ServiceInstance server = Mono.from(testService.choose()).block().getServer();
            //必须处于和当前实例同一个zone下
            Assertions.assertEquals(server.getMetadata().get("zone"), "zone1");
        }
    }

    /**
     * 返回不同的实例
     */
    @Test
    public void testReturnNext() {
        ReactiveLoadBalancer<ServiceInstance> testService =
                loadBalancerClientFactory.getInstance("testService");
        Span span = tracer.nextSpan();
        for (int i = 0; i < 100; i++) {
            try (Tracer.SpanInScope cleared = tracer.withSpanInScope(span)) {
                ServiceInstance server1 = Mono.from(testService.choose()).block().getServer();
                ServiceInstance server2 = Mono.from(testService.choose()).block().getServer();
                //每次选择的是不同实例
                Assertions.assertNotEquals(server1.getInstanceId(), server2.getInstanceId());
            }
        }
    }

    /**
     * 跨线程，默认情况下是可能返回同一实例的，在我们的实现下，保持
     * span 则会返回下一个实例，这样保证多线程环境同一个 request 重试会返回下一实例
     *
     * @throws Exception
     */
    @Test
    public void testSameSpanReturnNext() throws Exception {
        Span span = tracer.nextSpan();
        for (int i = 0; i < 100; i++) {
            try (Tracer.SpanInScope cleared = tracer.withSpanInScope(span)) {
                ReactiveLoadBalancer<ServiceInstance> testService =
                        loadBalancerClientFactory.getInstance("testService");
                ServiceInstance server1 = Mono.from(testService.choose()).block().getServer();
                AtomicReference<ServiceInstance> server2 = new AtomicReference<>();
                Thread thread = new Thread(() -> {
                    try (Tracer.SpanInScope cleared2 = tracer.withSpanInScope(span)) {
                        server2.set(Mono.from(testService.choose()).block().getServer());
                    }
                });
                thread.start();
                thread.join();
                System.out.println(i);
                Assertions.assertNotEquals(server1.getInstanceId(), server2.get().getInstanceId());
            }
        }
    }
}
