package com.syrobin.cloud.service.consumer.feign;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-08-09 17:24
 */
@Slf4j
@Component
public class TestFeignClients implements ApplicationListener<ApplicationReadyEvent> {

    @Autowired
    private ServiceProviderClient serviceProviderClient;


    @SneakyThrows
    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        try {
            serviceProviderClient.test(Map.of());
        } catch (Exception e) {
            log.error("error: {}", e.toString());
        }
    }
}
