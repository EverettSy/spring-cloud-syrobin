package com.syrobin.cloud.webflux.config;

import com.syrobin.cloud.commons.resilience4j.CircuitBreakerExtractor;
import com.syrobin.cloud.webflux.webclient.WebClientNamedContextFactory;
import com.syrobin.cloud.webflux.webclient.WebClientRequestCircuitBreakerExtractor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-08-17 21:08
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebClientConfigurationProperties.class)
public class WebClientConfiguration {

    @Bean
    public WebClientNamedContextFactory getWebClientNamedContextFactory() {
        return new WebClientNamedContextFactory();
    }

    @Bean
    public CircuitBreakerExtractor webClientRequestCircuitBreakerExtractor() {
        return new WebClientRequestCircuitBreakerExtractor();
    }
}
