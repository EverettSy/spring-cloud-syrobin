package webclient

import brave.Span
import brave.Tracer
import com.syrobin.cloud.commons.loadbalancer.TracedCircuitBreakerRoundRobinLoadBalancer
import com.syrobin.cloud.commons.resilience4j.CircuitBreakerExtractor
import com.syrobin.cloud.webflux.webclient.WebClientNamedContextFactory
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.core.ConfigurationNotFoundException
import io.netty.handler.timeout.ReadTimeoutException
import org.assertj.core.util.Lists
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.client.DefaultServiceInstance
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

@SpringBootTest(
		properties = [
				"webclient.configs.testServiceWithCannotConnect.baseUrl=http://testServiceWithCannotConnect",
				"webclient.configs.testServiceWithCannotConnect.serviceName=testServiceWithCannotConnect",
				"webclient.configs.testService.baseUrl=http://testService",
				"webclient.configs.testService.serviceName=testService",
				"webclient.configs.testService.responseTimeout=1500ms",
				"webclient.configs.testService.retryablePaths[0]=/delay/3",
				"webclient.configs.testService.retryablePaths[1]=/status/4*",
				"spring.cloud.loadbalancer.zone=zone1",
				"resilience4j.retry.configs.default.maxAttempts=3",
				"resilience4j.circuitbreaker.configs.default.failureRateThreshold=50",
				"resilience4j.circuitbreaker.configs.default.slidingWindowType=TIME_BASED",
				"resilience4j.circuitbreaker.configs.default.slidingWindowSize=5",
				//因为重试是 3 次，为了防止断路器打开影响测试，设置为正好比重试多一次的次数，防止触发
				//同时我们在测试的时候也需要手动清空断路器统计
				"resilience4j.circuitbreaker.configs.default.minimumNumberOfCalls=4",
				"resilience4j.circuitbreaker.configs.default.recordExceptions=java.lang.Exception"
		],
		classes = MockConfig
)
class WebClientUnitTest extends Specification {

	@SpringBootApplication
	static class MockConfig {
	}
	@SpringBean
	private LoadBalancerClientFactory loadBalancerClientFactory = Mock()

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry
	@Autowired
	private Tracer tracer
	@Autowired
	private WebClientNamedContextFactory webClientNamedContextFactory
	@Autowired
	private CircuitBreakerExtractor circuitBreakerExtractor;

	//不同的测试方法的类对象不是同一个对象，会重新生成，保证互相没有影响
	def zone1Instance1 = new DefaultServiceInstance(instanceId: "instance1", host: "www.httpbin.org", port: 80, metadata: Map.ofEntries(Map.entry("zone", "zone1")))
	def zone1Instance2 = new DefaultServiceInstance(instanceId: "instance2", host: "www.httpbin.org", port: 8081, metadata: Map.ofEntries(Map.entry("zone", "zone1")))
	def zone1Instance3 = new DefaultServiceInstance(instanceId: "instance3", host: "httpbin.org", port: 80, metadata: Map.ofEntries(Map.entry("zone", "zone1")))


	TracedCircuitBreakerRoundRobinLoadBalancer loadBalancerClientFactoryInstance = Spy();
	ServiceInstanceListSupplier serviceInstanceListSupplier = Spy();

	//所有测试的方法执行前会调用的方法
	def setup() {
		//初始化 loadBalancerClientFactoryInstance 负载均衡器
		loadBalancerClientFactoryInstance.setTracer(tracer)
		loadBalancerClientFactoryInstance.setCircuitBreakerRegistry(circuitBreakerRegistry)
		loadBalancerClientFactoryInstance.setCircuitBreakerExtractor(circuitBreakerExtractor)
		loadBalancerClientFactoryInstance.setServiceInstanceListSupplier(serviceInstanceListSupplier)
	}

	def "测试断路器异常重试以及断路器级别"() {
		given: "设置 testService 的实例都是正常实例"
			loadBalancerClientFactory.getInstance("testService") >> loadBalancerClientFactoryInstance
			serviceInstanceListSupplier.get() >> Flux.just(Lists.newArrayList(zone1Instance1, zone1Instance3))
		when: "断路器打开"
			//清除断路器影响
			circuitBreakerRegistry.getAllCircuitBreakers().forEach({ c -> c.reset() })
			loadBalancerClientFactoryInstance = (TracedCircuitBreakerRoundRobinLoadBalancer) loadBalancerClientFactory.getInstance("testService")
			def breaker
			try {
				breaker = circuitBreakerRegistry.circuitBreaker("httpbin.org:80/anything", "testService")
			} catch (ConfigurationNotFoundException e) {
				breaker = circuitBreakerRegistry.circuitBreaker("httpbin.org:80/anything")
			}
			//打开实例 3 的断路器
			breaker.transitionToOpenState()
			//调用 10 次
			for (i in 0..<10) {
				Mono<String> stringMono = webClientNamedContextFactory.getWebClient("testService")
																	  .get().uri("/anything").retrieve()
																	  .bodyToMono(String.class)
				println(stringMono.block())
			}
		then:"调用至少 10 次负载均衡器且没有异常即成功，考虑可能会有重试"
			(10.._) * loadBalancerClientFactoryInstance.getInstanceResponseByRoundRobin(*_)
		when: "调用不同的路径，验证断路器在这个路径上都是关闭"
			//调用 10 次
			for (i in 0..<10) {
				Mono<String> stringMono = webClientNamedContextFactory.getWebClient("testService")
																	  .get().uri("/status/200").retrieve()
																	  .bodyToMono(String.class)
				println(stringMono.block())
			}
		then: "调用必须为正好 10 次代表没有重试，一次成功，断路器之间相互隔离"
			10 * loadBalancerClientFactoryInstance.getInstanceResponseByRoundRobin(*_)
	}

	def "测试针对 connectTimeout 重试"() {
		given: "设置微服务 testServiceWithCannotConnect 一个实例正常，另一个实例会连接超时"
			loadBalancerClientFactory.getInstance("testServiceWithCannotConnect") >> loadBalancerClientFactoryInstance
			serviceInstanceListSupplier.get() >> Flux.just(Lists.newArrayList(zone1Instance1, zone1Instance2))
		when:
			//由于我们针对 testService 返回了两个实例，一个可以正常连接，一个不可以，但是我们配置了重试 3 次，所以每次请求应该都能成功，并且随着程序运行，后面的调用不可用的实例还会被断路
			//这里主要测试针对 connect time out 还有 断路器打开的情况都会重试，并且无论是 GET 方法还是其他的
			Span span = tracer.nextSpan()
			for (i in 0..<10) {
				Tracer.SpanInScope cleared = tracer.withSpanInScope(span)
				try {
					//测试 get 方法（默认 get 方法会重试）
					Mono<String> stringMono = webClientNamedContextFactory.getWebClient("testServiceWithCannotConnect")
																		  .get().uri("/anything").retrieve()
																		  .bodyToMono(String.class)
					println(stringMono.block())
					//测试 post 方法（默认 post 方法针对请求已经发出的不会重试，这里没有发出请求所以还是会重试的）
					stringMono = webClientNamedContextFactory.getWebClient("testServiceWithCannotConnect")
															 .post().uri("/anything").retrieve()
															 .bodyToMono(String.class)
					println(stringMono.block())
				}
				finally {
					cleared.close()
				}
			}
		then:"调用至少 20 次负载均衡器且没有异常即成功"
			(20.._) * loadBalancerClientFactoryInstance.getInstanceResponseByRoundRobin(*_)
	}

	def "测试针对 readTimeout 重试"() {
		given: "设置 testService 的实例都是正常实例"
			loadBalancerClientFactory.getInstance("testService") >> loadBalancerClientFactoryInstance
			serviceInstanceListSupplier.get() >> Flux.just(Lists.newArrayList(zone1Instance1, zone1Instance3))
		when: "测试 GET 延迟 2 秒返回，超过读取超时"
			//清除断路器影响
			circuitBreakerRegistry.getAllCircuitBreakers().forEach({ c -> c.reset() })
			try {
				webClientNamedContextFactory.getWebClient("testService")
											.get().uri("/delay/2").retrieve()
											.bodyToMono(String.class).block();
			} catch (WebClientRequestException e) {
				if (e.getCause() in  ReadTimeoutException) {
					//读取超时忽略
				} else {
					throw e;
				}
			}
		then: "每次都会超时所以会重试，根据配置一共有 3 次"
			3 * loadBalancerClientFactoryInstance.getInstanceResponseByRoundRobin(*_)
		when: "测试 POST 延迟 3 秒返回，超过读取超时，同时路径在重试路径中"
			//清除断路器影响
			circuitBreakerRegistry.getAllCircuitBreakers().forEach({ c -> c.reset() })
			try {
				webClientNamedContextFactory.getWebClient("testService")
											.post().uri("/delay/3").retrieve()
											.bodyToMono(String.class).block();
			} catch (WebClientRequestException e) {
				if (e.getCause() in  ReadTimeoutException) {
					//读取超时忽略
				} else {
					throw e;
				}
			}
		then: "每次都会超时所以会重试，根据配置一共有 3 次"
			3 * loadBalancerClientFactoryInstance.getInstanceResponseByRoundRobin(*_)
		when: "测试 POST 延迟 2 秒返回，超过读取超时，这个不能重试"
			//清除断路器影响
			circuitBreakerRegistry.getAllCircuitBreakers().forEach({ c -> c.reset() })
			try {
				webClientNamedContextFactory.getWebClient("testService")
											.post().uri("/delay/2").retrieve()
											.bodyToMono(String.class).block();
			} catch (WebClientRequestException e) {
				if (e.getCause() in  ReadTimeoutException) {
					//读取超时忽略
				} else {
					throw e;
				}
			}
		then: "没有重试，只有一次调用"
			1 * loadBalancerClientFactoryInstance.getInstanceResponseByRoundRobin(*_)
	}

	def "测试非 200 响应码返回" () {
		given: "设置 testService 的实例都是正常实例"
			loadBalancerClientFactory.getInstance("testService") >> loadBalancerClientFactoryInstance
			serviceInstanceListSupplier.get() >> Flux.just(Lists.newArrayList(zone1Instance1, zone1Instance3))
		when: "测试 GET 返回 500"
			//清除断路器影响
			circuitBreakerRegistry.getAllCircuitBreakers().forEach({ c -> c.reset() })
			try {
				webClientNamedContextFactory.getWebClient("testService")
											.get().uri("/status/500").retrieve()
											.bodyToMono(String.class).block();
			} catch (WebClientResponseException e) {
				if (e.getStatusCode().is5xxServerError()) {
					//5xx忽略
				} else {
					throw e;
				}
			}
		then: "每次都没有返回 2xx 所以会重试，根据配置一共有 3 次"
			3 * loadBalancerClientFactoryInstance.getInstanceResponseByRoundRobin(*_)
		when: "测试 POST 返回 500"
			//清除断路器影响
			circuitBreakerRegistry.getAllCircuitBreakers().forEach({ c -> c.reset() })
			try {
				webClientNamedContextFactory.getWebClient("testService")
											.post().uri("/status/500").retrieve()
											.bodyToMono(String.class).block();
			} catch (WebClientResponseException e) {
				if (e.getStatusCode().is5xxServerError()) {
					//5xx忽略
				} else {
					throw e;
				}
			}
		then: "POST 默认不重试，所以只会调用一次"
			1 * loadBalancerClientFactoryInstance.getInstanceResponseByRoundRobin(*_)
		when: "测试 POST 返回 400，这个请求路径在重试路径中"
			//清除断路器影响
			circuitBreakerRegistry.getAllCircuitBreakers().forEach({ c -> c.reset() })
			try {
				webClientNamedContextFactory.getWebClient("testService")
											.post().uri("/status/400").retrieve()
											.bodyToMono(String.class).block();
			} catch (WebClientResponseException e) {
				if (e.getStatusCode().is4xxClientError()) {
					//4xx忽略
				} else {
					throw e;
				}
			}
		then: "路径在重试路径中，所以会重试"
			3 * loadBalancerClientFactoryInstance.getInstanceResponseByRoundRobin(*_)
	}
}