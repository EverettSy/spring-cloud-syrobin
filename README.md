## 简介
spring-cloud-syrobin 项目是一个完整的微服务学习体系，用户可以通过使用 Spring Cloud 快速搭建一个自己的微服务系统。
包含如下组件：

* 服务发现：DiscoveryClient，从注册中心发现微服务。
* 服务注册：ServiceRegistry，注册微服务到注册中心。
* 负载均衡：LoadBalancerClient，客户端调用负载均衡。其中，重试策略从spring-cloud-commons-2.2.6加入了负载均衡的抽象中。
* 断路器：CircuitBreaker，负责什么情况下将服务断路并降级
* 调用 http 客户端：内部 RPC 调用都是 http 调用
