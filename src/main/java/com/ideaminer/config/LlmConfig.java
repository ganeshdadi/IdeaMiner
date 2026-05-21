package com.ideaminer.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String openAiApiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String modelName;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(modelName)
                .temperature(0.2) // Low temperature for analytical tasks
                .maxTokens(4000)
                .build();
    }
}
