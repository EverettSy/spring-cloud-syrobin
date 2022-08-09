package com.syrobin.cloud.api.gateway.factory;

import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-08-05 17:24
 */
public class TracedMono<T> extends Mono<T> {

    private final Mono<T> delegate;
    private final Tracer tracer;
    private final CurrentTraceContext currentTraceContext;
    private final Span span;

    public TracedMono(Mono<T> delegate, Tracer tracer, CurrentTraceContext currentTraceContext, Span span) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.currentTraceContext = currentTraceContext;
        this.span = span;
    }

    @Override
    public void subscribe(CoreSubscriber<? super T> actual) {
        delegate.subscribe(new TracedCoreSubscriber<>(actual, tracer, currentTraceContext, span));
    }
}
