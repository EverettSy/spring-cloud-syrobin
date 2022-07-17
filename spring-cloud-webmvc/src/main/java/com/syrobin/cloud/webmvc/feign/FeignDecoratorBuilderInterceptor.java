package com.syrobin.cloud.webmvc.feign;

import io.github.resilience4j.feign.FeignDecorators;

/**
 * @author syrobin
 * @version v1.0
 * 用于包装FeignDecoratorBuilder
 * 可以用于实现 fallback
 * @date 2022-07-05 23:10
 */
@FunctionalInterface
public interface FeignDecoratorBuilderInterceptor {

    void intercept(FeignDecorators.Builder builder);
}
