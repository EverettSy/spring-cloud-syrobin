package com.syrobin.cloud.webmvc.undertow.jfr;

import lombok.extern.log4j.Log4j2;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;

import javax.servlet.*;
import java.io.IOException;

/**
 * @author syrobin
 * @version v1.0
 * @description: JFR 跟踪过滤器
 * @date 2022-07-13 21:11
 */
@Log4j2
public class JFRTracingFilter implements Filter {

    private final Tracer tracer;

    public JFRTracingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException,
            ServletException {

        HttpRequestJFREvent httpRequestJFREvent = null;
        try {
            TraceContext traceContext = tracer.currentSpan().context();
            String traceId = traceContext.traceId();
            String spanId = traceContext.spanId();
            //收到请求就创建 HttpRequestReceivedJFREvent 并直接提交
            HttpRequestReceivedJFREvent httpRequestReceivedJFREvent = new HttpRequestReceivedJFREvent(servletRequest, traceId, spanId);
            httpRequestReceivedJFREvent.commit();
            //收到请求就创建 HttpRequestJFREvent 并开始提交
            httpRequestJFREvent = new HttpRequestJFREvent(servletRequest, traceId, spanId);
            httpRequestJFREvent.begin();
        } catch (Exception e) {
            log.error("JFRTracingFilter-doFilter failed: {}", e.getMessage(), e);
        }

        Throwable throwable = null;
        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } catch (IOException | ServletException t) {
            throwable = t;
            throw t;
        } finally {
            try {
                //无论如何，都会提交 httpRequestJFREvent
                if (httpRequestJFREvent != null) {
                    httpRequestJFREvent.setResponseStatus(servletResponse, throwable);
                    httpRequestJFREvent.commit();
                }
            } catch (Exception e) {
                log.error("JFRTracingFilter-doFilter final failed: {}", e.getMessage(), e);
            }
        }
    }
}
