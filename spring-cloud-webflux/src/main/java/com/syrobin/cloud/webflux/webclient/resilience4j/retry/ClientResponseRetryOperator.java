package com.syrobin.cloud.webflux.webclient.resilience4j.retry;

import io.github.resilience4j.reactor.IllegalPublisherException;
import io.github.resilience4j.retry.Retry;
import lombok.extern.log4j.Log4j2;
import org.reactivestreams.Publisher;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.UnaryOperator;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * 在官方原始版本的基础上，特定了形参并增加了日志
 * @see io.github.resilience4j.reactor.retry.RetryOperator
 * @date 2022-09-12 11:36
 */
@Log4j2
public class ClientResponseRetryOperator implements UnaryOperator<Publisher<ClientResponse>> {
    private final Retry retry;

    private ClientResponseRetryOperator(Retry retry) {
        this.retry = retry;
    }

    public static ClientResponseRetryOperator of(Retry retry) {
        return new ClientResponseRetryOperator(retry);
    }

    @Override
    public Publisher<ClientResponse> apply(Publisher<ClientResponse> publisher) {
        if (publisher instanceof Mono) {
            ClientResponseContext clientResponseContext = new ClientResponseContext(retry.asyncContext());
            Mono<ClientResponse> upstream = (Mono<ClientResponse>) publisher;
            return upstream.doOnNext(clientResponseContext::handleResult)
                    .retryWhen(reactor.util.retry.Retry.withThrowable(errors -> errors.flatMap(clientResponseContext::handleErrors)))
                    .doOnSuccess(t -> clientResponseContext.onComplete());
        } else if (publisher instanceof Flux) {
            ClientResponseContext clientResponseContext = new ClientResponseContext(retry.asyncContext());
            Flux<ClientResponse> upstream = (Flux<ClientResponse>) publisher;
            return upstream.doOnNext(clientResponseContext::handleResult)
                    .retryWhen(reactor.util.retry.Retry.withThrowable(errors -> errors.flatMap(clientResponseContext::handleErrors)))
                    .doOnComplete(clientResponseContext::onComplete);
        } else {
            throw new IllegalPublisherException(publisher);
        }
    }

    private static class ClientResponseContext {

        private final Retry.AsyncContext<ClientResponse> retryContext;

        ClientResponseContext(Retry.AsyncContext<ClientResponse> retryContext) {
            this.retryContext = retryContext;
        }

        void onComplete() {
            this.retryContext.onComplete();
        }

        void handleResult(ClientResponse result) {
            long waitDurationMillis = retryContext.onResult(result);
            if (waitDurationMillis != -1) {
                throw new RetryDueToResultException(waitDurationMillis);
            }
        }

        Publisher<Long> handleErrors(Throwable throwable) {
            if (throwable instanceof RetryDueToResultException) {
                long waitDurationMillis = ((RetryDueToResultException) throwable).waitDurationMillis;
                log.info("web client retry: got RetryDueToResultException: {}, retry waitDurationMillis: {}", throwable.getLocalizedMessage(), waitDurationMillis);
                return Mono.delay(Duration.ofMillis(waitDurationMillis));
            }
            // Filter Error to not retry on it
            if (throwable instanceof Error) {
                log.info("web client retry: will not retry: {}", throwable.toString());
                throw (Error) throwable;
            }

            long waitDurationMillis = retryContext.onError(throwable);
            log.info("web client retry: got exception: {}, retry waitDurationMillis: {}", throwable.toString(), waitDurationMillis);
            if (waitDurationMillis == -1) {
                return Mono.error(throwable);
            }

            return Mono.delay(Duration.ofMillis(waitDurationMillis));
        }

        private static class RetryDueToResultException extends RuntimeException {
            private final long waitDurationMillis;

            RetryDueToResultException(long waitDurationMillis) {
                super("retry due to retryOnResult predicate");
                this.waitDurationMillis = waitDurationMillis;
            }
        }
    }
}
