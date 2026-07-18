package com.lardermind.config;

import com.lardermind.service.ChatMemorySeeder;
import com.lardermind.service.CookingTools;
import com.lardermind.service.ai.CookingAssistant;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangChain4jConfig {

    @Bean
    CookingAssistant cookingAssistant(
            OpenAiChatModel model,
            OpenAiStreamingChatModel streamingModel,
            CookingTools tools,
            ChatMemorySeeder chatMemorySeeder) {
        return AiServices.builder(CookingAssistant.class)
                .chatModel(model)
                .streamingChatModel(streamingModel)
                .chatMemoryProvider(chatMemorySeeder::getOrCreateMemory)
                .tools(tools)
                .build();
    }
}
