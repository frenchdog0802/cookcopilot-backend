package com.lardermind.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Logs encoding at startup so MS950/Big5 misconfiguration is obvious.
 * Streaming Chinese is also protected by {@link Utf8LangChainHttpConfig}.
 */
@Component
@Slf4j
public class EncodingStartupCheck {

    @PostConstruct
    void logEncoding() {
        Charset defaults = Charset.defaultCharset();
        if (!StandardCharsets.UTF_8.equals(defaults)) {
            log.warn(
                    "JVM default charset is {} (not UTF-8). "
                            + "HTTP servlet encoding is forced to UTF-8; LangChain4j streaming uses Utf8ServerSentEventParser. "
                            + "Prefer starting via run.cmd / mvnw (see .mvn/jvm.config) or IDE launch with -Dfile.encoding=UTF-8.",
                    defaults.name());
        } else {
            log.info("JVM default charset is UTF-8");
        }
    }
}
