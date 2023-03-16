package com.syrobin.cloud.service.consumer.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.net.URL;
import java.util.Map;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-08-09 17:22
 */
@FeignClient(name = "service-provider",contextId = "ServiceProviderClient",url = "dynamicUrl")
public interface ServiceProviderClient {

    @GetMapping("/test-simple")
    String test(@RequestBody Map<String,String> body);

    @GetMapping("/dynamicUrl")
    String dynamicUrl(URL url, @RequestBody Map<String, String> body);
}
