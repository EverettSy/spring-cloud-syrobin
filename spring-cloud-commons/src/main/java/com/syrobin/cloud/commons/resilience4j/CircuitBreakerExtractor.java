package com.syrobin.cloud.commons.resilience4j;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import org.springframework.cloud.client.loadbalancer.Request;

public interface CircuitBreakerExtractor<T> {
	/**
	 * 通过负载均衡请求，以及实例信息，获取对应的 CircuitBreaker
	 * @param circuitBreakerRegistry
	 * @param request
	 * @param host
	 * @param port
	 * @return
	 */
	CircuitBreaker getCircuitBreaker(
			CircuitBreakerRegistry circuitBreakerRegistry,
			Request<T> request,
			String host,
			int port
			);
}
