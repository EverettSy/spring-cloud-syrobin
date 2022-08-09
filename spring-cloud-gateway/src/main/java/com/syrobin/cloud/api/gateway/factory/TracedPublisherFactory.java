package com.syrobin.cloud.api.gateway.factory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-08-05 17:27
 */
@Component
public class TracedPublisherFactory {

    protected static final String TRACE_REQUEST_ATTR = Span.class.getName();

    @Autowired
    private Tracer tracer;

    @Autowired
    private CurrentTraceContext currentTraceContext;

    public <T> Flux<T> getTracedFlux(Flux<T> publisher, ServerWebExchange exchange) {
        return new TracedFlux<>(publisher, tracer, currentTraceContext,
                (Span) exchange.getAttributes().get(TRACE_REQUEST_ATTR));
    }

    public <T> Mono<T> getTracedMono(Mono<T> publisher, ServerWebExchange exchange) {
        return new TracedMono<>(publisher, tracer, currentTraceContext,
                (Span) exchange.getAttributes().get(TRACE_REQUEST_ATTR));
    }
}
