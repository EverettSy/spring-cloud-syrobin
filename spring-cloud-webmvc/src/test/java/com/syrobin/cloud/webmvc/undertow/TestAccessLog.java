package com.syrobin.cloud.webmvc.undertow;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author syrobin
 * @version v1.0
 * @description: 测试访问日志
 * @date 2022-07-17 22:08
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {"server.undertow.accesslog.pattern=%D"})
public class TestAccessLog  implements ApplicationContextAware {
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        System.out.println();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    public static  class SampleConfig {

    }

    @Autowired
    private DefaultWebServerFactoryCustomizer customizer;

    @Test
    public void testLogContainsResponseTime() throws NoSuchFieldError{
        Assertions.assertNotNull(this.customizer);
    }
}
