package com.syrobin.cloud.service.provider.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-08-09 15:15
 */
@Log4j2
@RestController
public class TestController {

    @Value("${eureka.instance.metadata-map:zone}")
    private String zone;
    @Value("${test.read-timeout:true}")
    private Boolean shouldTimeout;
    @Value("${test.exception-throw:true}")
    private Boolean shouldThrowException;

    @RequestMapping(value = "/test-simple", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public String test(HttpServletRequest httpServletRequest, @RequestBody Map<String, String> body) {
        log.info("test called {}, body {}", httpServletRequest.getMethod(), body);
        return zone;
    }

    @RequestMapping(value = "/test-read-time-out", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public String testReadTimeOut(HttpServletRequest httpServletRequest) throws Exception {
        log.info("testReadTimeOut called {}", httpServletRequest.getMethod());
        if (shouldTimeout) {
            //设置接口延迟5秒返回，超过了readTimeout
            TimeUnit.SECONDS.sleep(5);
        }
        return zone;
    }

    @RequestMapping(value = "/test-exception-thrown", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public String testExceptionThrown(HttpServletRequest httpServletRequest, @RequestBody Map<String, String> body) {
        log.info("testExceptionThrow called {}, {}", httpServletRequest.getMethod(), body);
        if (shouldThrowException) {
            throw new IllegalStateException();
        }
        return zone;
    }
}
