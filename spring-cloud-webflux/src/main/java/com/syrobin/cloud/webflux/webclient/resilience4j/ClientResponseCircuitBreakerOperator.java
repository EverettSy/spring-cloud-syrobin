package com.syrobin.cloud.webflux.webclient.resilience4j;

import com.syrobin.cloud.webflux.config.WebClientConfigurationProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.IllegalPublisherException;
import org.reactivestreams.Publisher;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import java.util.function.UnaryOperator;

/**
 * @author syrobin
 * @version v1.0
 * 基于官方的 CircuitBreakerOperator 针对 ClientResponse 改造，基于 ClientResponse 的 http status code
 * @date 2022-09-12 11:21
 * @see io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator
 */
public class ClientResponseCircuitBreakerOperator implements UnaryOperator<Publisher<ClientResponse>> {
    private final CircuitBreaker circuitBreaker;
    private final ServiceInstance serviceInstance;
    private final WebClientConfigurationProperties.WebClientProperties webClientProperties;

    private ClientResponseCircuitBreakerOperator(CircuitBreaker circuitBreaker, ServiceInstance serviceInstance, WebClientConfigurationProperties.WebClientProperties webClientProperties) {
        this.circuitBreaker = circuitBreaker;
        this.serviceInstance = serviceInstance;
        this.webClientProperties = webClientProperties;
    }

    public static ClientResponseCircuitBreakerOperator of(CircuitBreaker circuitBreaker, ServiceInstance serviceInstance, WebClientConfigurationProperties.WebClientProperties webClientProperties) {
        return new ClientResponseCircuitBreakerOperator(circuitBreaker, serviceInstance, webClientProperties);
    }

    @Override
    public Publisher<ClientResponse> apply(Publisher<ClientResponse> clientResponsePublisher) {
        if (clientResponsePublisher instanceof Mono) {
            return new ClientResponseMonoCircuitBreaker((Mono<? extends ClientResponse>) clientResponsePublisher, circuitBreaker, serviceInstance, webClientProperties);
        } else if (clientResponsePublisher instanceof Flux) {
            return new ClientResponseFluxCircuitBreaker((Flux<? extends ClientResponse>) clientResponsePublisher, circuitBreaker, serviceInstance, webClientProperties);
        } else {
            throw new IllegalPublisherException(clientResponsePublisher);
        }
    }
}
