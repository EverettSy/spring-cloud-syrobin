package com.syrobin.cloud.webmvc.auto;

import com.syrobin.cloud.webmvc.config.CommonOpenFeignConfiguration;
import com.syrobin.cloud.webmvc.config.DefaultOpenFeignConfiguration;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.cloud.commons.httpclient.DefaultOkHttpClientFactory;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-06-23 17:37
 */
@Configuration(proxyBeanMethods = false)
@Import(CommonOpenFeignConfiguration.class)
@EnableFeignClients(value = "com.syrobin.cloud.webmvc",defaultConfiguration = DefaultOpenFeignConfiguration.class)
public class OpenFeignAutoConfiguration {
}
