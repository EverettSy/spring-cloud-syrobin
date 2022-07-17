package com.syrobin.cloud.webmvc.misc;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-07-11 17:48
 */
public enum SpecialHttpStatus {

    /**
     * 断路器打开
     */
    CIRCUIT_BREAKER_ON(581),
    /**
     * 可以重试的异常
     */
    RETRYABLE_IO_EXCEPTION(582),
    /**
     * 不能重试的异常
     */
    NOT_RETRYABLE_IO_EXCEPTION(583),
    ;
    private int value;

    SpecialHttpStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;

    }
}