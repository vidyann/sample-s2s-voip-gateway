package com.example.s2s.voipgateway.voicelive;

/**
 * Configuration for Azure Speech Voice Live API.
 * Supports both Azure AI Foundry and Azure AI Speech Services resources.
 */
public class VoiceLiveConfig {
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final String apiVersion;

    /**
     * Creates a VoiceLiveConfig from environment variables.
     * 
     * Required environment variables:
     * - VOICE_LIVE_ENDPOINT: Azure AI endpoint (e.g., https://your-resource.services.ai.azure.com)
     * - VOICE_LIVE_API_KEY: API key for authentication
     * - VOICE_LIVE_MODEL: Model to use (e.g., gpt-realtime, gpt-4o, phi4-mm-realtime)
     * 
     * Optional:
     * - VOICE_LIVE_API_VERSION: API version (default: 2025-10-01)
     */
    public VoiceLiveConfig() {
        this.endpoint = getRequiredEnv("VOICE_LIVE_ENDPOINT");
        this.apiKey = getRequiredEnv("VOICE_LIVE_API_KEY");
        this.model = getRequiredEnv("VOICE_LIVE_MODEL");
        this.apiVersion = System.getenv().getOrDefault("VOICE_LIVE_API_VERSION", "2025-10-01");
        
        // Validate endpoint format
        if (!endpoint.startsWith("https://") && !endpoint.startsWith("wss://")) {
            throw new IllegalArgumentException("VOICE_LIVE_ENDPOINT must start with https:// or wss://");
        }
    }

    /**
     * Creates a VoiceLiveConfig with explicit values.
     */
    public VoiceLiveConfig(String endpoint, String apiKey, String model, String apiVersion) {
        if (endpoint == null || endpoint.isEmpty()) {
            throw new IllegalArgumentException("endpoint cannot be null or empty");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("apiKey cannot be null or empty");
        }
        if (model == null || model.isEmpty()) {
            throw new IllegalArgumentException("model cannot be null or empty");
        }
        
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.apiVersion = apiVersion != null ? apiVersion : "2025-10-01";
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

    public String getApiVersion() {
        return apiVersion;
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
               ", apiVersion='" + apiVersion + '\'' +
               ", apiKey='***'" +
               '}';
    }
}
