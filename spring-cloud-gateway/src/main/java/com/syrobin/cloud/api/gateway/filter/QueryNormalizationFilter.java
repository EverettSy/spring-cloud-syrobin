package com.syrobin.cloud.api.gateway.filter;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-08-15 21:09
 */
@Log4j2
@Component
public class QueryNormalizationFilter extends AbstractTracedFilter {

    @SneakyThrows
    @Override
    protected Mono<Void> traced(ServerWebExchange exchange, GatewayFilterChain chain) {
        String originUriString = exchange.getRequest().getURI().toString();
        if (originUriString.contains("%23")) {
            //将编码后的 %23 替换为 #，重新用这个字符串生成 URI
            URI replaced = new URI(originUriString.replace("%23", "#"));
            return chain.filter(exchange.mutate()
                    .request(new ServerHttpRequestDecorator(exchange.getRequest()) {
                        /**
                         * 这个是影响转发到后台服务的 uri
                         *
                         * @return
                         */
                        @Override
                        public URI getURI() {
                            return replaced;
                        }

                        /**
                         * 修改这个主要为了后面的 Filter 获取查询参数是准确的
                         *
                         * @return
                         */
                        @Override
                        public MultiValueMap<String, String> getQueryParams() {
                            return UriComponentsBuilder.fromUri(getURI()).build().getQueryParams();
                        }
                    }).build());
        } else {
            return chain.filter(exchange);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Autowired
    ReactorLoadBalancerExchangeFilterFunction loadBalancerExchangeFilterFunction;

    //使用 WebClient 的 Builder 创建 WebClient
    WebClient client = WebClient.builder()
            //指定基础地址
            .baseUrl("http://localhost:8080")
            //可以指定一些默认的参数,例如默认Cookie,默认HttpHeader 等等
            .defaultCookie("cookieKey", "cookieValue")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            //负载均衡器，改写url
            .filter(loadBalancerExchangeFilterFunction)
            .build();

    // GET 请求 /test 并将 body 转化为 String
    Mono<String> stringMono = client.get().uri("/test").retrieve().bodyToMono(String.class);
    //采用阻塞获取
    String block = stringMono.block();


}
