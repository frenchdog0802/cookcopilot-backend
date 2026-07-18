package com.lardermind.config;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.spring.restclient.SpringRestClient;
import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.client.RestClient;

/**
 * Replaces LangChain4j's default streaming HTTP client builder so SSE bodies
 * are decoded as UTF-8 on Windows locales that default to MS950/Big5.
 */
@Configuration
public class Utf8LangChainHttpConfig {

    @Bean(name = "openAiStreamingChatModelHttpClientBuilder")
    HttpClientBuilder openAiStreamingChatModelHttpClientBuilder(
            ObjectProvider<RestClient.Builder> restClientBuilderProvider,
            @Qualifier("openAiStreamingChatModelTaskExecutor") AsyncTaskExecutor streamingRequestExecutor) {
        SpringRestClientBuilder springBuilder = SpringRestClient.builder()
                .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder))
                .streamingRequestExecutor(streamingRequestExecutor);
        return new Utf8HttpClientBuilder(springBuilder);
    }
}
