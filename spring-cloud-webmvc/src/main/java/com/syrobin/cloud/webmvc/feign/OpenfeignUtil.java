package com.syrobin.cloud.webmvc.feign;

import feign.Request;
import feign.Response;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Supplier;

import static feign.FeignException.errorStatus;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-07-11 16:56
 */
public class OpenfeignUtil {

    /**
     * 判断一个 OpenFeign 的请求是否是可以重试类型的请求
     * 根据方法是否为 GET，以及方法和方法所在类上面是否有 RetryableMethod 注解来判定
     *
     * @param request
     * @return
     */
    public static boolean isRetryableRequest(Request request) {
        Request.HttpMethod httpMethod = request.httpMethod();
        if (Objects.equals(httpMethod, Request.HttpMethod.GET)) {
            return true;
        }
        Method method = request.requestTemplate().methodMetadata().method();
        RetryableMethod annotation = method.getAnnotation(RetryableMethod.class);
        if (annotation == null) {
            annotation = method.getDeclaringClass().getAnnotation(RetryableMethod.class);
        }
        //如果类上面或者方法上面有注解，则为查询类型的请求，是可以重试的
        // 如果方法上没有RetryableMethod注解，则默认为不重试
        return annotation != null;

    }

    /**
     * 针对 OpenFeign 的 circuitBreaker 封装，根据响应进行断路
     * @param circuitBreaker
     * @param supplier
     * @return
     */
    public static Supplier<Response> decorateSupplier(CircuitBreaker circuitBreaker, Supplier<Response> supplier) {
        return () -> {
            circuitBreaker.acquirePermission();
            long start = circuitBreaker.getCurrentTimestamp();

            long duration;
            try {
                Response result = supplier.get();
                HttpStatus httpStatus = HttpStatus.valueOf(result.status());
                duration = circuitBreaker.getCurrentTimestamp() - start;
                if (httpStatus.is2xxSuccessful()) {
                    circuitBreaker.onResult(duration, circuitBreaker.getTimestampUnit(), result);
                }else {
                    circuitBreaker.onError(duration, circuitBreaker.getTimestampUnit(), errorStatus("not useful", result));
                }
                return result;
            } catch (Exception var7) {
                duration = circuitBreaker.getCurrentTimestamp() - start;
                circuitBreaker.onError(duration, circuitBreaker.getTimestampUnit(), var7);
                throw var7;
            }
        };
    }
}
