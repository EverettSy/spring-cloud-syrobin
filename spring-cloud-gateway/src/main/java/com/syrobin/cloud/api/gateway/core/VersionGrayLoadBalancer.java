package com.syrobin.cloud.api.gateway.core;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author syrobin
 * @version v1.0
 * @description: 自定义灰度负载均衡器
 * 通过给请求头添加Version 与 Service Instance 元数据属性进行对比
 * @date 2022-03-22 16:43
 */
@Slf4j
public class VersionGrayLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    final String serviceId;
    final AtomicInteger position;
    ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;


    public VersionGrayLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider,
                                   String serviceId) {
        this(serviceInstanceListSupplierProvider, serviceId, new Random().nextInt(1000));
    }

    public VersionGrayLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider,
                                   String serviceId, int position) {
        this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
        this.serviceId = serviceId;
        this.position = new AtomicInteger(position);
    }


    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {

        //获取服务实例列表
        ServiceInstanceListSupplier supplier =
                this.serviceInstanceListSupplierProvider.getIfAvailable(NoopServiceInstanceListSupplier::new);
        return supplier
                .get(request)
                .next()
                //从列表中选择一个实例
                .map(serviceInstances -> processInstanceResponse(serviceInstances, request));
    }

    private Response<ServiceInstance> processInstanceResponse(List<ServiceInstance> instances, Request request) {
        if (instances.isEmpty()) {
            log.warn("No servers available for service: " + this.serviceId);
            return new EmptyResponse();
        } else {
            DefaultRequestContext requestContext = (DefaultRequestContext) request.getContext();
            RequestData clientRequest = (RequestData) requestContext.getClientRequest();
            HttpHeaders headers = clientRequest.getHeaders();

            // 获取请求头
            String reqVersion = headers.getFirst("version");
            if (StringUtils.isEmpty(reqVersion)) {
                return processRibbonInstanceResponse(instances);
            }

            log.info("request header version : {}", reqVersion);
            //过滤服务实例
            List<ServiceInstance> serviceInstances = instances.stream()
                    .filter(instance -> reqVersion.equals(instance.getMetadata().get("version")))
                    .collect(Collectors.toList());
            if (!serviceInstances.isEmpty()){
                return processRibbonInstanceResponse(serviceInstances);
            }else {
                return processRibbonInstanceResponse(instances);
            }

        }
    }

    /**
     * 负载均衡器
     * 考 org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer#getInstanceResponse
     * @param instances
     * @return
     */
    private Response<ServiceInstance> processRibbonInstanceResponse(List<ServiceInstance> instances) {
        int pos = Math.abs(this.position.incrementAndGet());
        ServiceInstance instance = instances.get(pos % instances.size());
        return new DefaultResponse(instance);
    }

}
