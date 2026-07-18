package com.cookcopilot.config;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder;

import java.time.Duration;

/**
 * Wraps {@link SpringRestClientBuilder} so built clients parse SSE as UTF-8.
 */
public class Utf8HttpClientBuilder implements HttpClientBuilder {

    private final SpringRestClientBuilder delegate;

    public Utf8HttpClientBuilder(SpringRestClientBuilder delegate) {
        this.delegate = delegate;
    }

    public SpringRestClientBuilder delegate() {
        return delegate;
    }

    @Override
    public Duration connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public HttpClientBuilder connectTimeout(Duration connectTimeout) {
        delegate.connectTimeout(connectTimeout);
        return this;
    }

    @Override
    public Duration readTimeout() {
        return delegate.readTimeout();
    }

    @Override
    public HttpClientBuilder readTimeout(Duration readTimeout) {
        delegate.readTimeout(readTimeout);
        return this;
    }

    @Override
    public HttpClient build() {
        return new Utf8HttpClient(delegate.build());
    }
}
