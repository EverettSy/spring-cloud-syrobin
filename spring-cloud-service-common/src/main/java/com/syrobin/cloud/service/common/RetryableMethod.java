package com.syrobin.cloud.service.common;

import java.lang.annotation.*;

/**
 * @author syrobin
 * @version v1.0
 * @description: 标注这个 feign 方法或者 feign 类里面的所有方法都是可以重试
 * @date 2022-07-11 17:01
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RetryableMethod {
}
