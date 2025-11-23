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

import com.azure.ai.voicelive.VoiceLiveAsyncClient;
import com.azure.ai.voicelive.VoiceLiveClientBuilder;
import com.azure.ai.voicelive.VoiceLiveServiceVersion;
import com.azure.ai.voicelive.VoiceLiveSessionAsyncClient;
import com.azure.core.credential.KeyCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Voice Live client using official Azure SDK.
 * 
 * Voice Live API provides enhanced real-time speech capabilities including:
 * - Azure semantic VAD with filler word removal
 * - Deep noise suppression
 * - Server-side echo cancellation
 * - End-of-utterance detection
 * - Azure TTS with HD voices
 * - Audio timestamps and visemes
 * 
 * This implementation uses the official Azure SDK which eliminates:
 * - Custom WebSocket handling
 * - Manual JSON parsing
 * - Circular dependency issues
 */
public class VoiceLiveClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(VoiceLiveClient.class);
    
    private final VoiceLiveAsyncClient client;
    private VoiceLiveSessionAsyncClient session;
    private final VoiceLiveConfig config;
    
    /**
     * Creates a Voice Live client with API key authentication.
     */
    public VoiceLiveClient(VoiceLiveConfig config) {
        this.config = config;
        this.client = new VoiceLiveClientBuilder()
            .endpoint(config.getEndpoint())
            .credential(new KeyCredential(config.getApiKey()))
            .serviceVersion(VoiceLiveServiceVersion.V2025_10_01)
            .buildAsyncClient();
        
        LOG.info("Voice Live client initialized with endpoint: {}", config.getEndpoint());
    }
    
    /**
     * Starts a session with the specified model and returns the session client.
     * The session client can be used to send audio, receive events, and manage the conversation.
     * 
     * @param model The model to use (e.g., "gpt-4o-realtime-preview")
     * @return Mono that emits the session client when ready
     */
    public Mono<VoiceLiveSessionAsyncClient> startSession(String model) {
        LOG.info("Starting Voice Live session with model: {}", model);
        return client.startSession(model)
            .doOnSuccess(s -> {
                this.session = s;
                LOG.info("Voice Live session started successfully");
            })
            .doOnError(error -> LOG.error("Failed to start Voice Live session", error));
    }
    
    /**
     * Gets the current session client.
     * 
     * @return The session client, or null if no session is active
     */
    public VoiceLiveSessionAsyncClient getSession() {
        return session;
    }
    
    /**
     * Gets the Voice Live configuration.
     */
    public VoiceLiveConfig getConfig() {
        return config;
    }
    
    /**
     * Closes the client and releases resources.
     */
    public void close() {
        if (client != null) {
            LOG.info("Closing Voice Live client");
            // Azure SDK handles cleanup automatically
        }
    }
}

