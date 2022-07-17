package com.syrobin.cloud.webmvc.auto;

import com.syrobin.cloud.webmvc.config.WebServerConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-06-24 17:24
 */
@Configuration(proxyBeanMethods = false)
@Import(WebServerConfiguration.class)
public class UndertowAutoConfiguration {
}
