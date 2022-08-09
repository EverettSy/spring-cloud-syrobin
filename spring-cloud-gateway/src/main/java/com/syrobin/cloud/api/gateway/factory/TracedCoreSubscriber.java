package com.syrobin.cloud.api.gateway.factory;



import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;

/**
 * @author syrobin
 * @version v1.0
 * @description: 链路追踪的 Subscriber
 * @date 2022-08-05 17:06
 */
public class TracedCoreSubscriber<T> implements Subscriber<T> {

    private final Subscriber<T> delegate;
    private final Tracer tracer;
    private final CurrentTraceContext currentTraceContext;
    private final Span span;

    public TracedCoreSubscriber(Subscriber<T> delegate, Tracer tracer, CurrentTraceContext currentTraceContext, Span span) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.currentTraceContext = currentTraceContext;
        this.span = span;
    }


    @Override
    public void onSubscribe(Subscription s) {
        executeWithinScope(() -> delegate.onSubscribe(s));
    }

    @Override
    public void onNext(T t) {
        executeWithinScope(() -> delegate.onNext(t));
    }

    @Override
    public void onError(Throwable t) {
        executeWithinScope(() -> delegate.onError(t));
    }

    @Override
    public void onComplete() {
        executeWithinScope(delegate::onComplete);
    }

    private void executeWithinScope(Runnable runnable) {
        //如果当前没有链路信息，强制包裹
        if (tracer.currentSpan() == null){
            try(CurrentTraceContext.Scope scope = this.currentTraceContext.maybeScope(this.span.context())){
                runnable.run();
            }
        }else {
            //如果当前已有链路信息，则直接执行
            runnable.run();
        }
    }
}
