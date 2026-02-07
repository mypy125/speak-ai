package com.mygitgor.ai;

import java.util.Properties;

public class AIServiceFactory {

    public static AiService createService(Properties config) {
        String apiKey = config.getProperty("ai.api.key", "").trim();
        String provider = config.getProperty("ai.provider", "groq").toLowerCase();
        String model = config.getProperty("ai.model", "").trim();
        String customUrl = config.getProperty("ai.custom.url", "").trim();

        if (apiKey.isEmpty() || apiKey.equals("your-api-key-here")) {
            return new MockAiService();
        }

        if (provider.equals("auto")) {
            provider = detectProviderByKey(apiKey);
        }

        String apiUrl = customUrl.isEmpty() ? getDefaultUrlForProvider(provider) : customUrl;

        if (model.isEmpty()) {
            model = getDefaultModelForProvider(provider);
        }

        double temperature = Double.parseDouble(config.getProperty("ai.temperature", "0.7"));
        int maxTokens = Integer.parseInt(config.getProperty("ai.max.tokens", "1500"));

        return new UniversalAIService(apiKey, provider, model, apiUrl, temperature, maxTokens);
    }

    private static String detectProviderByKey(String apiKey) {
        if (apiKey.startsWith("sk-")) {
            if (apiKey.startsWith("sk-deepseek-")) {
                return "deepseek";
            } else if (apiKey.startsWith("sk-ant-")) {
                return "anthropic";
            } else {
                return "openai";
            }
        } else if (apiKey.startsWith("gsk_")) {
            return "groq";
        } else if (apiKey.contains("AIza")) {
            return "google";
        } else if (apiKey.equals("ollama")) {
            return "ollama";
        }
        return "groq"; // По умолчанию Groq
    }

    private static String getDefaultUrlForProvider(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> "https://api.openai.com/v1/chat/completions";
            case "groq" -> "https://api.groq.com/openai/v1/chat/completions";
            case "deepseek" -> "https://api.deepseek.com/chat/completions";
            case "anthropic" -> "https://api.anthropic.com/v1/messages";
            case "together" -> "https://api.together.xyz/v1/chat/completions";
            case "ollama" -> "http://localhost:11434/api/chat";
            default -> "https://api.groq.com/openai/v1/chat/completions";
        };
    }

    private static String getDefaultModelForProvider(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> "gpt-3.5-turbo";
            case "groq" -> "llama-3.1-8b-instant";
            case "deepseek" -> "deepseek-chat";
            case "anthropic" -> "claude-3-haiku-20240307";
            case "together" -> "mistralai/Mixtral-8x7B-Instruct-v0.1";
            case "ollama" -> "llama3.2";
            default -> "llama-3.1-8b-instant";
        };
    }
}
