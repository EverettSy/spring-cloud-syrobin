package com.syrobin.cloud.webmvc.undertow.jfr;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Tracer;

import javax.servlet.*;
import java.io.IOException;

/**
 * @author syrobin
 * @version v1.0
 * @description: 惰性 JFR 跟踪过滤器
 * @date 2022-07-13 21:24
 */
public class LazyJFRTracingFilter implements Filter {
    private final BeanFactory beanFactory;

    private JFRTracingFilter jfrTracingFilter;

    public LazyJFRTracingFilter(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        jfrTracingFilter().doFilter(request, response, chain);
    }

    private Filter jfrTracingFilter() {
        if (this.jfrTracingFilter == null) {
            this.jfrTracingFilter = new JFRTracingFilter(this.beanFactory.getBean(Tracer.class));
        }
        return this.jfrTracingFilter;
    }
}
