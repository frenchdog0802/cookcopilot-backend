package com.lardermind.config;

import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class Utf8ServerSentEventParserTest {

    @Test
    void parsesChineseUtf8Tokens() {
        String payload = "data:你好\n\n";
        byte[] utf8Bytes = payload.getBytes(StandardCharsets.UTF_8);

        List<String> events = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        new Utf8ServerSentEventParser().parse(new ByteArrayInputStream(utf8Bytes), new ServerSentEventListener() {
            @Override
            public void onEvent(ServerSentEvent event) {
                events.add(event.data());
            }

            @Override
            public void onError(Throwable throwable) {
                error.set(throwable);
            }
        });

        assertNull(error.get());
        assertEquals(1, events.size());
        assertEquals("你好", events.get(0));
    }
}
