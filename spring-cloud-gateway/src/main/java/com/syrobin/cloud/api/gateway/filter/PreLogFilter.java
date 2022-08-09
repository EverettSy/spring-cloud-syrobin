package com.syrobin.cloud.api.gateway.filter;

import lombok.extern.log4j.Log4j2;
import org.springframework.cloud.gateway.filter.AdaptCachedBodyGlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * @author syrobin
 * @version v1.0
 * @description: 日志前过滤器
 * @date 2022-08-05 16:39
 */
@Log4j2
@Component
public class PreLogFilter implements GlobalFilter, Ordered {

    public static final int ORDER = new AdaptCachedBodyGlobalFilter().getOrder() - 1;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("before AdaptCachedBodyGlobalFilter");
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
