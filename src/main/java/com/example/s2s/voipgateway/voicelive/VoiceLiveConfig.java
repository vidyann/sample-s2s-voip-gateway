/*
 * Copyright (c) 2024 Amazon.com, Inc. or its affiliates.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.example.s2s.voipgateway.voicelive;

/**
 * Configuration for Azure Speech Voice Live API.
 * Supports both Azure AI Foundry and Azure AI Speech Services resources.
 */
public class VoiceLiveConfig {
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final String voice;
    private final String instructions;
    private final String transcriptionModel;
    private final String transcriptionLanguage;
    private final String apiVersion;
    private final Integer maxResponseOutputTokens;
    private final String proactiveGreeting;
    private final boolean proactiveGreetingEnabled;

    /**
     * Creates a VoiceLiveConfig from environment variables.
     * 
     * Required environment variables:
     * - VOICE_LIVE_ENDPOINT: Azure AI endpoint (e.g., https://your-resource.services.ai.azure.com)
     * - VOICE_LIVE_API_KEY: API key for authentication
     * - VOICE_LIVE_MODEL: Model to use (e.g., gpt-realtime, gpt-4o, phi4-mm-realtime)
     * - VOICE_LIVE_VOICE: Voice to use (e.g., en-US-Ava:DragonHDLatestNeural)
     * 
     * Optional:
     * - VOICE_LIVE_INSTRUCTIONS: System prompt/instructions for the AI assistant
     * - VOICE_LIVE_API_VERSION: API version (default: 2025-10-01)
     */
    public VoiceLiveConfig() {
        this.endpoint = getRequiredEnv("VOICE_LIVE_ENDPOINT");
        this.apiKey = getRequiredEnv("VOICE_LIVE_API_KEY");
        this.model = getRequiredEnv("VOICE_LIVE_MODEL");
        this.voice = getRequiredEnv("VOICE_LIVE_VOICE");
        this.instructions = System.getenv().getOrDefault("VOICE_LIVE_INSTRUCTIONS", 
            "You are a helpful AI voice assistant. Keep responses VERY brief and concise. Answer in 1-2 sentences maximum. You MUST always respond in English only, regardless of the language spoken by the user.");
        this.transcriptionModel = System.getenv().getOrDefault("VOICE_LIVE_TRANSCRIPTION_MODEL", "AZURE_SPEECH");
        this.transcriptionLanguage = System.getenv().getOrDefault("VOICE_LIVE_TRANSCRIPTION_LANGUAGE", "en-US");
        this.apiVersion = System.getenv().getOrDefault("VOICE_LIVE_API_VERSION", "2025-10-01");
        
        // Max response output tokens (default: 200, ~40 words = 1-2 sentences)
        String maxTokensEnv = System.getenv("VOICE_LIVE_MAX_RESPONSE_OUTPUT_TOKENS");
        this.maxResponseOutputTokens = (maxTokensEnv != null && !maxTokensEnv.isEmpty()) 
            ? Integer.parseInt(maxTokensEnv) 
            : 200;
        
        // Proactive greeting configuration
        this.proactiveGreetingEnabled = Boolean.parseBoolean(
            System.getenv().getOrDefault("VOICE_LIVE_PROACTIVE_GREETING_ENABLED", "true"));
        this.proactiveGreeting = System.getenv().getOrDefault(
            "VOICE_LIVE_PROACTIVE_GREETING", 
            "Hello! How can I help you today?");
        
        // Validate endpoint format
        if (!endpoint.startsWith("https://") && !endpoint.startsWith("wss://")) {
            throw new IllegalArgumentException("VOICE_LIVE_ENDPOINT must start with https:// or wss://");
        }
    }

    /**
     * Creates a VoiceLiveConfig with explicit values.
     */
    public VoiceLiveConfig(String endpoint, String apiKey, String model, String voice, String instructions, String transcriptionModel, String transcriptionLanguage, String apiVersion, Integer maxResponseOutputTokens, String proactiveGreeting, boolean proactiveGreetingEnabled) {
        if (endpoint == null || endpoint.isEmpty()) {
            throw new IllegalArgumentException("endpoint cannot be null or empty");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("apiKey cannot be null or empty");
        }
        if (model == null || model.isEmpty()) {
            throw new IllegalArgumentException("model cannot be null or empty");
        }
        if (voice == null || voice.isEmpty()) {
            throw new IllegalArgumentException("voice cannot be null or empty");
        }
        
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.voice = voice;
        this.instructions = instructions != null ? instructions : "You are a helpful AI voice assistant. Keep responses VERY brief and concise. Answer in 1-2 sentences maximum. You MUST always respond in English only, regardless of the language spoken by the user.";
        this.transcriptionModel = transcriptionModel != null ? transcriptionModel : "AZURE_SPEECH";
        this.transcriptionLanguage = transcriptionLanguage != null ? transcriptionLanguage : "en-US";
        this.apiVersion = apiVersion != null ? apiVersion : "2025-10-01";
        this.maxResponseOutputTokens = (maxResponseOutputTokens != null) ? maxResponseOutputTokens : 200;
        this.proactiveGreeting = proactiveGreeting != null ? proactiveGreeting : "Hello! How can I help you today?";
        this.proactiveGreetingEnabled = proactiveGreetingEnabled;
    }

    /**
     * Builds the WebSocket URL for Voice Live API connection.
     * Format: wss://<resource>.services.ai.azure.com/voice-live/realtime?api-version=2025-10-01&model=<model>
     */
    public String buildWebSocketUrl() {
        String baseUrl = endpoint;
        
        // Convert https:// to wss://
        if (baseUrl.startsWith("https://")) {
            baseUrl = "wss://" + baseUrl.substring(8);
        }
        
        // Remove trailing slash
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        // Build WebSocket URL
        return String.format("%s/voice-live/realtime?api-version=%s&model=%s", 
                             baseUrl, apiVersion, model);
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModel() {
        return model;
    }

    public String getVoice() {
        return voice;
    }

    public String getInstructions() {
        return instructions;
    }

    public String getTranscriptionModel() {
        return transcriptionModel;
    }

    public String getTranscriptionLanguage() {
        return transcriptionLanguage;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public Integer getMaxResponseOutputTokens() {
        return maxResponseOutputTokens;
    }

    public String getProactiveGreeting() {
        return proactiveGreeting;
    }

    public boolean isProactiveGreetingEnabled() {
        return proactiveGreetingEnabled;
    }

    private String getRequiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Environment variable " + key + " is required but not set");
        }
        return value;
    }

    @Override
    public String toString() {
        return "VoiceLiveConfig{" +
               "endpoint='" + endpoint + '\'' +
               ", model='" + model + '\'' +
               ", voice='" + voice + '\'' +
               ", apiVersion='" + apiVersion + '\'' +
               ", apiKey='***'" +
               '}';
    }
}
