package com.syrobin.cloud.api.gateway.factory;


import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-08-05 17:20
 */
public class TracedFlux<T> extends Flux<T> {

    private final Flux<T> delegate;
    private final Tracer tracer;
    private final CurrentTraceContext currentTraceContext;
    private final Span span;

    public TracedFlux(Flux<T> delegate, Tracer tracer, CurrentTraceContext currentTraceContext, Span span) {
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
