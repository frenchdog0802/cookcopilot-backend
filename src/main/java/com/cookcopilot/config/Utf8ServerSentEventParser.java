package com.cookcopilot.config;

import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventListenerUtils;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Same as LangChain4j's DefaultServerSentEventParser, but always decodes as UTF-8.
 * Default parser uses {@code new InputStreamReader(stream)} which picks the JVM
 * default charset (MS950 on Traditional Chinese Windows) and garbles Chinese tokens.
 */
public class Utf8ServerSentEventParser implements ServerSentEventParser {

    @Override
    public void parse(InputStream httpResponseBody, ServerSentEventListener listener) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(httpResponseBody, StandardCharsets.UTF_8))) {

            String event = null;
            StringBuilder data = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        ServerSentEvent sse = new ServerSentEvent(event, data.toString());
                        ServerSentEventListenerUtils.ignoringExceptions(() -> listener.onEvent(sse));
                        event = null;
                        data.setLength(0);
                    }
                    continue;
                }

                if (line.startsWith("event:")) {
                    event = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    String content = line.substring("data:".length());
                    if (!data.isEmpty()) {
                        data.append("\n");
                    }
                    data.append(content.trim());
                }
            }

            if (!data.isEmpty()) {
                ServerSentEvent sse = new ServerSentEvent(event, data.toString());
                ServerSentEventListenerUtils.ignoringExceptions(() -> listener.onEvent(sse));
            }
        } catch (IOException e) {
            ServerSentEventListenerUtils.ignoringExceptions(() -> listener.onError(e));
        }
    }
}
