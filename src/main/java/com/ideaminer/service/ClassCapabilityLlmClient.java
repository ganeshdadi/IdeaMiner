package com.ideaminer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClassCapabilityLlmClient {

    private static final List<String> REQUIRED_FIELDS = List.of(
            "classPurpose",
            "businessCapability",
            "domainConcepts",
            "businessRules",
            "decisionsMade",
            "workflowsTouched",
            "dataTouched",
            "externalSystemsTouched",
            "sideEffects",
            "opportunityHints",
            "confidence"
    );

    private final ObjectProvider<ChatLanguageModel> chatLanguageModelProvider;
    private final ObjectMapper objectMapper;
    private final String openAiApiKey;
    private final boolean enabled;

    public ClassCapabilityLlmClient(ObjectProvider<ChatLanguageModel> chatLanguageModelProvider,
                                    ObjectMapper objectMapper,
                                    @Value("${langchain4j.open-ai.chat-model.api-key:demo}") String openAiApiKey,
                                    @Value("${ideaminer.llm.discovery.class-summary.enabled:true}") boolean enabled) {
        this.chatLanguageModelProvider = chatLanguageModelProvider;
        this.objectMapper = objectMapper;
        this.openAiApiKey = openAiApiKey;
        this.enabled = enabled;
    }

    public ClassCapabilitySummaryResult summarize(ClassCapabilityPromptInput input, String fallbackJson) {
        if (!canCallModel(input)) {
            return ClassCapabilitySummaryResult.fallback(fallbackJson, "LLM class summary disabled, unconfigured, or missing source context.");
        }

        try {
            String response = chatLanguageModelProvider.getObject().generate(prompt(input));
            ObjectNode fallback = (ObjectNode) objectMapper.readTree(fallbackJson);
            ObjectNode generated = normalize(extractJsonObject(response), fallback);
            return new ClassCapabilitySummaryResult(generated.toString(), "llm", null);
        } catch (Exception exception) {
            return ClassCapabilitySummaryResult.fallback(fallbackJson, exception.getMessage());
        }
    }

    private boolean canCallModel(ClassCapabilityPromptInput input) {
        return enabled
                && openAiApiKey != null
                && !openAiApiKey.isBlank()
                && !"demo".equalsIgnoreCase(openAiApiKey)
                && input.sourceContext() != null
                && !input.sourceContext().isBlank()
                && !"metadata_only".equals(input.contextMode());
    }

    private String prompt(ClassCapabilityPromptInput input) {
        return """
                You are analyzing Java source code for IdeaMiner, a code intelligence system that discovers modernization, automation, AI, and business opportunities.

                Produce exactly one JSON object. Do not include Markdown, comments, explanations, or fields outside the requested schema.

                Required JSON schema:
                {
                  "classPurpose": "short description of what this class does",
                  "businessCapability": "business or technical capability represented by this class",
                  "domainConcepts": ["domain nouns or concepts"],
                  "businessRules": ["rules, validations, thresholds, eligibility, approval, routing, or policy logic"],
                  "decisionsMade": ["meaningful decisions or branches this class appears to make"],
                  "workflowsTouched": ["workflow names or generic workflow categories"],
                  "dataTouched": ["entities, DTOs, tables, files, external data, or persisted data"],
                  "externalSystemsTouched": ["external APIs, queues, files, databases, or not detected"],
                  "sideEffects": ["writes, sends, calls, publishes, persists, schedules, or not detected"],
                  "opportunityHints": ["modernization_opportunity, automation_opportunity, ai_use_case, new_business_use_case, operational_improvement, etc."],
                  "confidence": 0.0
                }

                Rules:
                - Ground every field in the supplied source code and metadata.
                - Prefer business meaning over line-by-line code explanation.
                - If evidence is weak, use "not detected" and lower confidence.
                - confidence must be a number from 0.0 to 1.0.
                - Keep arrays concise.

                Class metadata:
                %s

                Full or reduced Java source context:
                ```java
                %s
                ```
                """.formatted(input.metadata(), input.sourceContext());
    }

    private JsonNode extractJsonObject(String response) throws Exception {
        String trimmed = response == null ? "" : response.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            trimmed = trimmed.substring(start, end + 1);
        }
        JsonNode node = objectMapper.readTree(trimmed);
        if (!node.isObject()) {
            throw new IllegalArgumentException("LLM response was not a JSON object");
        }
        return node;
    }

    private ObjectNode normalize(JsonNode generated, ObjectNode fallback) {
        ObjectNode normalized = objectMapper.createObjectNode();
        for (String field : REQUIRED_FIELDS) {
            JsonNode value = generated.get(field);
            normalized.set(field, value == null || value.isNull() ? fallback.get(field) : value);
        }
        normalized.set("evidence", fallback.get("evidence"));
        return normalized;
    }

    public record ClassCapabilityPromptInput(String metadata, String sourceContext, String contextMode) {
    }

    public record ClassCapabilitySummaryResult(String summaryJson, String mode, String error) {
        private static ClassCapabilitySummaryResult fallback(String summaryJson, String error) {
            return new ClassCapabilitySummaryResult(summaryJson, "deterministic_fallback", error);
        }
    }
}
