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

import com.google.gson.JsonObject;

/**
 * Interface for handling Voice Live API events.
 * Voice Live API uses the same events as Azure OpenAI Realtime API with some enhancements.
 */
public interface VoiceLiveEventHandler {
    
    /**
     * Called when a session is created.
     * Event type: session.created
     */
    void onSessionCreated(String sessionId, JsonObject session);
    
    /**
     * Called when a session is updated.
     * Event type: session.updated
     */
    void onSessionUpdated(String sessionId, JsonObject session);
    
    /**
     * Called when audio response delta is received.
     * Event type: response.audio.delta
     * 
     * @param responseId The response ID
     * @param itemId The item ID
     * @param outputIndex The output index
     * @param contentIndex The content index
     * @param delta Base64 encoded audio delta (PCM16)
     */
    void onResponseAudioDelta(String responseId, String itemId, int outputIndex, int contentIndex, String delta);
    
    /**
     * Called when audio response is done.
     * Event type: response.audio.done
     */
    void onResponseAudioDone(String responseId, String itemId);
    
    /**
     * Called when audio timestamp delta is received (if configured).
     * Event type: response.audio_timestamp.delta
     * 
     * @param responseId The response ID
     * @param itemId The item ID
     * @param audioOffsetMs Audio offset in milliseconds
     * @param audioDurationMs Audio duration in milliseconds
     * @param text The text corresponding to the audio
     * @param timestampType The type of timestamp (e.g., "word")
     */
    void onAudioTimestamp(String responseId, String itemId, int audioOffsetMs, int audioDurationMs, 
                          String text, String timestampType);
    
    /**
     * Called when viseme animation data is received (if configured).
     * Event type: response.animation_viseme.delta
     * 
     * @param responseId The response ID
     * @param itemId The item ID
     * @param audioOffsetMs Audio offset in milliseconds
     * @param visemeId The viseme ID
     */
    void onViseme(String responseId, String itemId, int audioOffsetMs, int visemeId);
    
    /**
     * Called when text delta is received.
     * Event type: response.text.delta
     */
    void onResponseTextDelta(String responseId, String itemId, int outputIndex, int contentIndex, String delta);
    
    /**
     * Called when transcription is received.
     * Event type: conversation.item.input_audio_transcription.completed
     */
    void onTranscriptionCompleted(String itemId, String transcript);
    
    /**
     * Called when an error occurs.
     * Event type: error
     */
    void onError(String type, String code, String message, JsonObject event);
    
    /**
     * Called for any unhandled event.
     */
    void onUnhandledEvent(String type, JsonObject event);
}
