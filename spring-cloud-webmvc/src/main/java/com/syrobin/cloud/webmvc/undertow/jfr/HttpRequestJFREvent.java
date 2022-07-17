package com.syrobin.cloud.webmvc.undertow.jfr;

import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.StackTrace;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.Enumeration;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-07-13 17:18
 */
@Category({"Http Request"})
@Label("Http Request JFR Event")
@StackTrace(false)
public class HttpRequestJFREvent extends Event {

    //请求的http方法
    private final String method;
    //请求的路径
    private final String path;
    //请求的查询参数
    private final String query;
    //请求的 traceId，来自于 sleuth
    private String traceId;
    //请求的 spanId，来自于 sleuth
    private String spanId;
    //发生的异常
    private String exception;
    //http 响应码
    private int responseStatus;

    public HttpRequestJFREvent(ServletRequest servletRequest,String traceId, String spanId) {
        HttpServletRequestImpl httpServletRequest = (HttpServletRequestImpl) servletRequest;
        this.method = httpServletRequest.getMethod();
        this.path = httpServletRequest.getRequestURI();
        this.query = httpServletRequest.getQueryParameters().toString();
        Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
        StringBuilder stringBuilder = new StringBuilder();
        headerNames.asIterator().forEachRemaining(s -> stringBuilder.append(s).append(":").append(httpServletRequest.getHeader(s)).append("\n"));
        this.traceId = traceId;
        this.spanId = spanId;
    }

    public void setResponseStatus(ServletResponse servletResponse,Throwable throwable) {
       this.responseStatus = ((HttpServletResponseImpl)servletResponse).getStatus();
       this.exception = throwable != null ? throwable.toString() : null;
    }

}
