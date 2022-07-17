package com.syrobin.cloud.webmvc.undertow.jfr;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.StackTrace;

import javax.servlet.ServletRequest;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-07-13 21:08
 */
@Category({"Http Request"})
@Label("Http Request Received")
@StackTrace(false)
public class HttpRequestReceivedJFREvent extends Event {

    //请求的 traceId，来自于 sleuth
    private final String traceId;
    //请求的 spanId，来自于 sleuth
    private final String spanId;

    public HttpRequestReceivedJFREvent(ServletRequest servletRequest, String traceId, String spanId) {
        this.traceId = traceId;
        this.spanId = spanId;
    }
}
