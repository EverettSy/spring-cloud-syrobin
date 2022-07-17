package com.syrobin.cloud.webmvc.config;

import com.syrobin.cloud.webmvc.undertow.jfr.LazyJFRTracingFilter;
import com.syrobin.cloud.webmvc.undertow.DefaultWebServerFactoryCustomizer;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.embedded.undertow.ConfigurableUndertowWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.sleuth.autoconfig.instrument.web.SleuthWebProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.DispatcherType;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-06-24 17:31
 */
@Configuration(proxyBeanMethods = false)
public class WebServerConfiguration {

    @Bean
    public WebServerFactoryCustomizer<ConfigurableUndertowWebServerFactory> undertowWebServerAccessLogTimingEnabler(ServerProperties serverProperties) {
        return new DefaultWebServerFactoryCustomizer(serverProperties);
    }

    @Bean
    FilterRegistrationBean jfrTracingFilter(BeanFactory beanFactory, SleuthWebProperties webProperties) {
        FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean(new LazyJFRTracingFilter(beanFactory));
        filterRegistrationBean.setDispatcherTypes(DispatcherType.ASYNC, DispatcherType.ERROR, DispatcherType.FORWARD,
                DispatcherType.INCLUDE, DispatcherType.REQUEST);
        //需要 sleuth 的 Filter 之后
        filterRegistrationBean.setOrder(webProperties.getFilterOrder() + 1);
        return filterRegistrationBean;
    }
}