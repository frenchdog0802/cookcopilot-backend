package com.lardermind.config;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

/**
 * Forces UTF-8 SSE parsing for LangChain4j streaming calls.
 */
public class Utf8HttpClient implements HttpClient {

    private static final ServerSentEventParser UTF8_PARSER = new Utf8ServerSentEventParser();

    private final HttpClient delegate;

    public Utf8HttpClient(HttpClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) throws HttpException, RuntimeException {
        return delegate.execute(request);
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventListener listener) {
        delegate.execute(request, UTF8_PARSER, listener);
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        delegate.execute(request, parser != null ? parser : UTF8_PARSER, listener);
    }
}
