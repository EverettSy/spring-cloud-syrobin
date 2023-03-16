import com.syrobin.cloud.commons.loadbalancer.TracedCircuitBreakerRoundRobinLoadBalancer
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.springframework.cloud.client.DefaultServiceInstance
import org.springframework.cloud.client.ServiceInstance
import spock.lang.Specification

class TracedCircuitBreakerRoundRobinLoadBalancerUnitTest extends Specification {
	//groovy 中，赋值变量声明必须按顺序，否则会为 null，因为赋值时也可以直接初始化
	ServiceInstance service1Instance1 = new DefaultServiceInstance(host: "10.238.1.1", port: 80)
	ServiceInstance service1Instance2 = new DefaultServiceInstance(host: "10.238.2.2", port: 80)
	ServiceInstance service1Instance3 = new DefaultServiceInstance(host: "10.238.3.3", port: 80)
	ServiceInstance service1Instance4 = new DefaultServiceInstance(host: "10.238.4.4", port: 80)
	CircuitBreaker circuitBreaker1 = Stub()
	CircuitBreaker.Metrics metrics1 = Stub()
	CircuitBreaker circuitBreaker2 = Stub()
	CircuitBreaker.Metrics metrics2 = Stub()
	CircuitBreaker circuitBreaker3 = Stub()
	CircuitBreaker.Metrics metrics3 = Stub()
	CircuitBreaker circuitBreaker4 = Stub()
	CircuitBreaker.Metrics metrics4 = Stub()
	List<ServiceInstance> serviceInstances = [service1Instance1, service1Instance2, service1Instance3, service1Instance4]
	TracedCircuitBreakerRoundRobinLoadBalancer tracedCircuitBreakerRoundRobinLoadBalancer = Spy()
	Map<ServiceInstance, CircuitBreaker> serviceInstanceCircuitBreakerMap = Map.ofEntries(
			Map.entry(service1Instance1, circuitBreaker1),
			Map.entry(service1Instance2, circuitBreaker2),
			Map.entry(service1Instance3, circuitBreaker3),
			Map.entry(service1Instance4, circuitBreaker4)
	)
	//所有测试的方法执行前会调用的方法
	def setup() {
		//初始化 loadBalancerClientFactoryInstance 负载均衡器
		circuitBreaker1.getMetrics() >> metrics1
		circuitBreaker2.getMetrics() >> metrics2
		circuitBreaker3.getMetrics() >> metrics3
		circuitBreaker4.getMetrics() >> metrics4
	}

	def "测试实例排序符合期望1"() {
		given: "期望顺序，1，2，3，4，随机。。。。"
			circuitBreaker1.getState() >> CircuitBreaker.State.CLOSED
			circuitBreaker2.getState() >> CircuitBreaker.State.CLOSED
			circuitBreaker3.getState() >> CircuitBreaker.State.CLOSED
			circuitBreaker4.getState() >> CircuitBreaker.State.CLOSED
			metrics1.getNumberOfBufferedCalls() >> 1
			metrics2.getNumberOfBufferedCalls() >> 2
			metrics3.getNumberOfBufferedCalls() >> 1
			metrics4.getNumberOfBufferedCalls() >> 1
			metrics1.getFailureRate() >> 0.1f
			metrics2.getFailureRate() >> 0.1f
			metrics3.getFailureRate() >> 0.2f
			metrics4.getFailureRate() >> 0.3f
		expect:
			long traceId = 1234;
			//第一次调用，实例 1 和实例 2 错误率 最少，同时实例 1 的调用小于实例 2，所以返回实例 1
			tracedCircuitBreakerRoundRobinLoadBalancer.getInstanceResponseByRoundRobin(traceId, serviceInstances, serviceInstanceCircuitBreakerMap).server == service1Instance1
			//由于实例 1 已经调用过，这次调用的是实例 2
			tracedCircuitBreakerRoundRobinLoadBalancer.getInstanceResponseByRoundRobin(traceId, serviceInstances, serviceInstanceCircuitBreakerMap).server == service1Instance2
			//这时候还没调用过实例 3，所以返回实例 3
			tracedCircuitBreakerRoundRobinLoadBalancer.getInstanceResponseByRoundRobin(traceId, serviceInstances, serviceInstanceCircuitBreakerMap).server == service1Instance3
			//这时候还没调用过实例 4，所以返回实例 4
			tracedCircuitBreakerRoundRobinLoadBalancer.getInstanceResponseByRoundRobin(traceId, serviceInstances, serviceInstanceCircuitBreakerMap).server == service1Instance4
			//所有实例都调用过，这时候就随机了
			tracedCircuitBreakerRoundRobinLoadBalancer.getInstanceResponseByRoundRobin(traceId, serviceInstances, serviceInstanceCircuitBreakerMap).server in serviceInstances
	}
}
