package com.syrobin.cloud.service.consumer.controller;

import com.syrobin.cloud.service.consumer.feign.ServiceProviderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-08-09 16:50
 */
@Slf4j
@RestController
public class TestController {

    @Autowired
    private ServiceProviderClient serviceProviderClient;


    @GetMapping("/test")
    public void test() {
        serviceProviderClient.test(Map.of());
    }
}
