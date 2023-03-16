package com.syrobin.cloud.service.common;

import com.syrobin.cloud.webmvc.feign.OpenfeignUtil;
import com.syrobin.cloud.webmvc.misc.SpecialHttpStatus;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;

import static feign.FeignException.errorStatus;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-08-09 11:20
 */
public class DefaultErrorDecoder implements ErrorDecoder {
    @Override
    public Exception decode(String methodKey, Response response) {
        boolean queryRequest = OpenfeignUtil.isRetryableRequest(response.request());
        //对于查询请求重试，即抛出可重试异常
        if (queryRequest || response.status() == SpecialHttpStatus.CIRCUIT_BREAKER_ON.getValue()) {
            throw new RetryableException(response.status(), response.reason(), response.request().httpMethod(), null, response.request());
        } else {
            throw errorStatus(methodKey, response);
        }
    }
}
