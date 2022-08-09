package com.syrobin.cloud.api.gateway.filter;

import com.syrobin.cloud.api.gateway.factory.TracedPublisherFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-08-05 17:32
 */
public abstract class AbstractTracedFilter implements GlobalFilter {

    @Autowired
    protected TracedPublisherFactory tracedPublisherFactory;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return tracedPublisherFactory.getTracedMono(traced(exchange, chain), exchange);
    }

    protected abstract Mono<Void> traced(ServerWebExchange exchange, GatewayFilterChain chain);
}
