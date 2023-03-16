package com.syrobin.cloud.webflux.auto;

import com.syrobin.cloud.webflux.config.WebClientConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-08-17 17:02
 */
@Import(WebClientConfiguration.class)
@Configuration(proxyBeanMethods = false)
public class WebClientAutoConfiguration {

}
