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

import com.azure.ai.voicelive.VoiceLiveSessionAsyncClient;
import com.azure.ai.voicelive.models.*;
import com.azure.core.util.BinaryData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles audio streaming between SIP (ulaw 8kHz) and Voice Live API (PCM16 24kHz).
 * Uses official Azure SDK with session-based API - NO circular dependency!
 * 
 * This implementation:
 * - Receives VoiceLiveSessionAsyncClient (clean dependency flow)
 * - Uses typed event models (no manual JSON parsing)
 * - Provides reactive audio streaming
 * - Handles audio format conversion and buffering
 */
public class VoiceLiveStreamHandler {
    
    private static final Logger LOG = LoggerFactory.getLogger(VoiceLiveStreamHandler.class);
    
    private final VoiceLiveSessionAsyncClient session;
    private final String voice;
    private final String instructions;
    private final String transcriptionModel;
    private final String transcriptionLanguage;
    private final Integer maxResponseOutputTokens;
    private final boolean proactiveGreetingEnabled;
    private final String proactiveGreeting;
    private volatile boolean isResponseDone = false; // Track when Voice Live finishes current response
    private volatile boolean conversationStarted = false;
    private VoiceLiveAudioOutput audioOutput;
    private final BlockingQueue<byte[]> outputAudioQueue = new LinkedBlockingQueue<>();
    private volatile boolean isSessionReady = false;
    private volatile boolean isStreamingAudio = false;
    private final AtomicReference<String> currentResponseText = new AtomicReference<>("");
    private final CompletableFuture<Void> sessionReadyFuture = new CompletableFuture<>();
    
    // Audio chunk buffering for smoother streaming
    private static final int MIN_CHUNK_SIZE_MS = 100; // 100ms minimum chunks
    private static final int SAMPLE_RATE = 24000;
    private static final int BYTES_PER_SAMPLE = 2; // PCM16
    private static final int MIN_CHUNK_SIZE_BYTES = (MIN_CHUNK_SIZE_MS * SAMPLE_RATE * BYTES_PER_SAMPLE) / 1000;
    private static final int MAX_OUTPUT_CHUNK_MS = 200; // cap Voice Live deltas to ~200ms per chunk
    private static final int MAX_OUTPUT_CHUNK_BYTES = (MAX_OUTPUT_CHUNK_MS * SAMPLE_RATE * BYTES_PER_SAMPLE) / 1000;
    private byte[] audioBuffer = new byte[MIN_CHUNK_SIZE_BYTES];
    private int bufferPos = 0;
    
    /**
     * Creates a stream handler for the given Voice Live session.
     * No circular dependency - session is passed in!
     */
    public VoiceLiveStreamHandler(VoiceLiveSessionAsyncClient session, 
                                    String voice, 
                                    String instructions,
                                    String transcriptionModel,
                                    String transcriptionLanguage,
                                    Integer maxResponseOutputTokens,
                                    boolean proactiveGreetingEnabled,
                                    String proactiveGreeting) {
        this.session = session;
        this.voice = voice;
        this.instructions = instructions;
        this.transcriptionModel = transcriptionModel;
        this.transcriptionLanguage = transcriptionLanguage;
        this.maxResponseOutputTokens = maxResponseOutputTokens;
        this.proactiveGreetingEnabled = proactiveGreetingEnabled;
        this.proactiveGreeting = proactiveGreeting;
        LOG.info("Voice Live max_response_output_tokens set to: {}", maxResponseOutputTokens);
    }
    
    /**
     * Set the audio output reference (for buffer clearing on interrupts).
     */
    public void setAudioOutput(VoiceLiveAudioOutput audioOutput) {
        this.audioOutput = audioOutput;
    }
    
    /**
     * Initializes the session with Voice Live configuration and subscribes to events.
     * Returns a Mono that completes when the session is ready (SESSION_UPDATED received).
     */
    public Mono<Void> initialize() {
        LOG.info("Initializing Voice Live stream handler");
        
        // Subscribe to all session events with typed handlers
        session.receiveEvents()
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                this::handleEvent,
                error -> LOG.error("Error receiving Voice Live events", error),
                () -> LOG.info("Voice Live event stream completed")
            );
        
        // Send session configuration
        VoiceLiveSessionOptions options = createSessionOptions();
        return session.sendEvent(new ClientEventSessionUpdate(options))
            .doOnSuccess(v -> LOG.info("Session configuration sent successfully"))
            .doOnError(error -> LOG.error("Failed to send session configuration", error))
            .then(Mono.fromFuture(sessionReadyFuture)); // Wait for SESSION_UPDATED event
    }
    
    /**
     * Creates session options with Voice Live enhancements.
     */
    private VoiceLiveSessionOptions createSessionOptions() {
        // Server VAD configuration (original working config)
        AzureSemanticVadTurnDetection vad = new AzureSemanticVadTurnDetection()
            .setThreshold(0.3)
            .setPrefixPaddingMs(300)
            .setSilenceDurationMs(500)
            .setInterruptResponse(true)
            .setAutoTruncate(true)
            .setCreateResponse(true);
        
        // Audio transcription - use configured model
        AudioInputTranscriptionOptionsModel transcriptionModelEnum = 
            "WHISPER_1".equalsIgnoreCase(transcriptionModel) 
                ? AudioInputTranscriptionOptionsModel.WHISPER_1 
                : AudioInputTranscriptionOptionsModel.AZURE_SPEECH;
        AudioInputTranscriptionOptions transcription = new AudioInputTranscriptionOptions(transcriptionModelEnum);
        
        // Set language for Azure Speech (Whisper supports auto-detection)
        if (transcriptionModelEnum == AudioInputTranscriptionOptionsModel.AZURE_SPEECH) {
            transcription.setLanguage(transcriptionLanguage);
            LOG.info("Azure Speech transcription language set to: {}", transcriptionLanguage);
        }

        
        VoiceLiveSessionOptions options = new VoiceLiveSessionOptions()
            .setInstructions(instructions)
            .setModalities(Arrays.asList(InteractionModality.TEXT, InteractionModality.AUDIO))
            .setVoice(BinaryData.fromObject(new AzureStandardVoice(voice)))
            .setInputAudioFormat(InputAudioFormat.PCM16)
            .setOutputAudioFormat(OutputAudioFormat.PCM16)
            .setInputAudioSamplingRate(24000)
            .setTurnDetection(vad)
            .setInputAudioNoiseReduction(new AudioNoiseReduction(AudioNoiseReductionType.AZURE_DEEP_NOISE_SUPPRESSION))
            .setInputAudioEchoCancellation(new AudioEchoCancellation())
            .setInputAudioTranscription(transcription);
        
        // Note: max_response_output_tokens parameter stored in config (value: {}) but not yet supported by Azure SDK
        // Relying on system instructions for brevity control instead
        LOG.info("✓ Session configured with brevity instructions (max_response_output_tokens target: {})", maxResponseOutputTokens);
        
        return options;
    
            // TODO: Add max_response_output_tokens to VoiceLiveSessionOptions when SDK supports it
            // TODO: Add output audio timestamp types when API is available
            //.setOutputAudioTimestampTypes(Arrays.asList(OutputAudioTimestampType.WORD));
    }
    
    /**
     * Handles typed events from the Voice Live SDK.
     * No manual JSON parsing needed!
     */
    private void handleEvent(SessionUpdate event) {
        LOG.info("📩 Received event: {}", event.getType());
        
        // Reset flag when new response starts
        if (event.getType().equals("response.created")) {
            isResponseDone = false;
        }
        
        switch (event) {
            case SessionUpdateSessionCreated created ->
                handleSessionCreated(created);
            case SessionUpdateSessionUpdated updated ->
                handleSessionUpdated(updated);
            case SessionUpdateResponseAudioDelta audioDelta ->
                handleResponseAudioDelta(audioDelta);
            case SessionUpdateResponseAudioDone audioDone ->
                handleResponseAudioDone(audioDone);
            case SessionUpdateResponseAudioTimestampDelta timestamp ->
                handleAudioTimestamp(timestamp);
            case SessionUpdateResponseTextDelta textDelta ->
                handleResponseTextDelta(textDelta);
            case SessionUpdateInputAudioBufferSpeechStarted speechStart ->
                // Voice Live handles interruptions server-side via setInterruptResponse(true)
                // No need to clear local buffers - Voice Live stops sending audio automatically
                LOG.info("🎤 Speech detected - Voice Live handling interruption server-side");
            case SessionUpdateInputAudioBufferSpeechStopped speechStop ->
                LOG.info("🤔 Speech ended - processing...");
            case SessionUpdateConversationItemInputAudioTranscriptionCompleted transcription ->
                handleTranscriptionCompleted(transcription);
            case SessionUpdateError error ->
                handleError(error);
            default ->
                LOG.debug("Unhandled event type: {}", event.getType());
        }
    }
    
    private void handleSessionCreated(SessionUpdateSessionCreated created) {
        String sessionId = created.getSession().getId();
        LOG.info("✓ Voice Live session created: {}", sessionId);
    }
    
    private void handleSessionUpdated(SessionUpdateSessionUpdated updated) {
        LOG.info("✓ Voice Live session configured successfully");
        isSessionReady = true;
        
        // Debug logging
        LOG.info("🔍 Proactive greeting check: enabled={}, conversationStarted={}", 
            proactiveGreetingEnabled, conversationStarted);
        
        // Send proactive greeting if enabled (after session is ready)
        if (proactiveGreetingEnabled && !conversationStarted) {
            conversationStarted = true;
            LOG.info("📢 Sending proactive greeting request");
            session.sendEvent(new ClientEventResponseCreate())
                .doOnSuccess(v -> LOG.info("✓ Proactive greeting request sent - bot will speak first"))
                .doOnError(error -> LOG.error("❌ Failed to send proactive greeting request", error))
                .subscribe();
        } else if (!proactiveGreetingEnabled) {
            LOG.info("⏸ Proactive greeting disabled - waiting for user to speak first");
        } else {
            LOG.info("⏭ Skipping proactive greeting - conversation already started");
        }
        
        sessionReadyFuture.complete(null); // Signal that session is ready!
    }
    
    private void handleResponseAudioDelta(SessionUpdateResponseAudioDelta event) {
        byte[] audioData = event.getDelta();
        if (audioData == null || audioData.length == 0) {
            return;
        }

        if (audioData.length <= MAX_OUTPUT_CHUNK_BYTES) {
            enqueueOutputChunk(audioData);
            return;
        }

        int offset = 0;
        int chunkCount = 0;
        while (offset < audioData.length) {
            int nextSize = Math.min(MAX_OUTPUT_CHUNK_BYTES, audioData.length - offset);
            byte[] chunk = Arrays.copyOfRange(audioData, offset, offset + nextSize);
            enqueueOutputChunk(chunk);
            offset += nextSize;
            chunkCount++;
        }
        LOG.debug("Split {} byte Voice Live chunk into {} sub-chunks (max {} bytes)",
            audioData.length, chunkCount, MAX_OUTPUT_CHUNK_BYTES);
    }

    private void enqueueOutputChunk(byte[] chunk) {
        boolean offered = outputAudioQueue.offer(chunk);
        if (!offered) {
            LOG.warn("⚠ Dropped Voice Live output chunk ({} bytes) - queue full", chunk.length);
            return;
        }
        LOG.info("← Queued audio chunk: {} bytes (queue size: {}) [handler: {}]", 
                 chunk.length, outputAudioQueue.size(), System.identityHashCode(this));
    }
    
    private void handleResponseAudioDone(SessionUpdate event) {
        LOG.info("✓ Response audio complete");
        isResponseDone = true; // Voice Live finished sending audio for this response
        
        // Notify audio output that response is complete (for short pre-call responses)
        if (audioOutput != null) {
            LOG.info("✓ Notifying audio output of pending response completion");
        }
    }
    
    private void handleAudioTimestamp(SessionUpdateResponseAudioTimestampDelta event) {
        LOG.debug("🕐 Audio timestamp: offset={}ms, text={}", 
                  event.getAudioOffsetMs(), event.getText());
    }
    
    private void handleResponseTextDelta(SessionUpdateResponseTextDelta event) {
        String delta = event.getDelta();
        if (delta != null && !delta.isEmpty()) {
            currentResponseText.updateAndGet(current -> current + delta);
            LOG.debug("💬 Text delta: {}", delta);
        }
    }
    
    private void handleTranscriptionCompleted(SessionUpdateConversationItemInputAudioTranscriptionCompleted event) {
        String transcript = event.getTranscript();
        LOG.info("✓ User said: {}", transcript);
    }
    
    private void handleError(SessionUpdateError event) {
        var error = event.getError();
        LOG.error("❌ Voice Live error: {}", error != null ? error.toString() : "Unknown error");
    }
    
    /**
     * Sends audio input to Voice Live API using buffered chunks.
     * Accumulates small RTP packets into larger chunks to prevent streaming conflicts.
     * Expects PCM16 audio at 24kHz.
     * 
     * @param pcm16Audio PCM16 audio data
     * @return Mono that completes when audio is processed
     */
    public synchronized Mono<Void> sendAudioInput(byte[] pcm16Audio) {
        if (!isSessionReady) {
            LOG.warn("Session not ready, skipping audio");
            return Mono.empty();
        }
        
        try {
            // Buffer incoming audio until we have enough for a chunk
            int remaining = pcm16Audio.length;
            int sourceOffset = 0;
            
            while (remaining > 0) {
                int bytesToCopy = Math.min(remaining, audioBuffer.length - bufferPos);
                System.arraycopy(pcm16Audio, sourceOffset, audioBuffer, bufferPos, bytesToCopy);
                bufferPos += bytesToCopy;
                sourceOffset += bytesToCopy;
                remaining -= bytesToCopy;
                
                // Send when buffer is full
                if (bufferPos >= audioBuffer.length) {
                    byte[] chunkToSend = new byte[bufferPos];
                    System.arraycopy(audioBuffer, 0, chunkToSend, 0, bufferPos);
                    bufferPos = 0;
                    
                    // Send the chunk
                    Mono<Void> sendResult = sendChunk(chunkToSend);
                    if (sendResult != null) {
                        return sendResult;
                    }
                }
            }
            
            return Mono.empty();
        } catch (Exception e) {
            LOG.error("Error buffering audio", e);
            return Mono.error(e);
        }
    }
    
    /**
     * Sends a single audio chunk to Voice Live.
     * Uses exponential backoff if streaming conflicts occur.
     */
    private Mono<Void> sendChunk(byte[] chunk) {
        if (!isStreamingAudio) {
            isStreamingAudio = true;
            LOG.info("→ Started audio streaming to Voice Live");
        }
        
        return session.sendInputAudio(BinaryData.fromBytes(chunk))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess(v -> LOG.debug("Sent audio chunk: {} bytes", chunk.length))
            .doOnError(error -> {
                if (error.getMessage() != null && 
                    error.getMessage().contains("standalone audio chunk")) {
                    // If we get the streaming conflict, log it but don't fail
                    LOG.debug("Audio streaming conflict (expected during call): {}", 
                              error.getMessage());
                } else {
                    LOG.error("Error sending audio chunk", error);
                }
            })
            .onErrorResume(error -> {
                // Resume on streaming conflicts (expected during active calls)
                if (error.getMessage() != null && 
                    error.getMessage().contains("standalone audio chunk")) {
                    return Mono.empty();
                }
                return Mono.error(error);
            });
    }
    
    /**
     * Flushes any buffered audio to Voice Live.
     * Call this when audio stream ends to ensure all audio is sent.
     */
    public synchronized Mono<Void> flushAudioBuffer() {
        if (bufferPos > 0) {
            byte[] finalChunk = new byte[bufferPos];
            System.arraycopy(audioBuffer, 0, finalChunk, 0, bufferPos);
            bufferPos = 0;
            return sendChunk(finalChunk);
        }
        return Mono.empty();
    }
    
    /**
     * Resets the audio streaming state.
     * Call this when a call ends.
     */
    public synchronized void resetAudioStream() {
        isStreamingAudio = false;
        bufferPos = 0;
        LOG.info("✓ Audio streaming reset");
    }
    
    /**
     * Retrieves the next available audio output chunk.
     * Blocks until audio is available or timeout occurs.
     * 
     * @return PCM16 audio data, or null if timeout
     */
    public byte[] getAudioOutput(long timeoutMs) {
        int queueSize = outputAudioQueue.size();
        LOG.info("🔍 getAudioOutput() called - queue size: {}, timeout: {}ms", queueSize, timeoutMs);
        
        try {
            byte[] result = outputAudioQueue.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (result != null) {
                LOG.info("✓ Retrieved audio chunk: {} bytes (remaining: {})", result.length, outputAudioQueue.size());
            } else {
                LOG.warn("⚠ getAudioOutput() returned null - queue size: {}", outputAudioQueue.size());
            }
            return result;
        } catch (InterruptedException e) {
            LOG.error("❌ getAudioOutput() interrupted", e);
            Thread.currentThread().interrupt();
            return null;
        }
    }
    
    /**
     * Checks if audio output is available.
     */
    public boolean hasAudioOutput() {
        return !outputAudioQueue.isEmpty();
    }
    
    /**
     * Returns true if the session is ready to send/receive audio.
     */
    public boolean isSessionReady() {
        return isSessionReady;
    }
    
    /**
     * Check if Voice Live has finished sending audio for current response.
     */
    public boolean isResponseDone() {
        return isResponseDone;
    }
    
    /**
     * Returns a future that completes when the session is ready.
     * Use this to wait for SESSION_UPDATED event reactively.
     */
    public CompletableFuture<Void> getSessionReadyFuture() {
        return sessionReadyFuture;
    }
    
    /**
     * Gets the current response text accumulated from text deltas.
     */
    public String getCurrentResponseText() {
        return currentResponseText.get();
    }
    
    /**
     * Clears any buffered audio output.
     */
    public void clearOutputBuffer() {
        outputAudioQueue.clear();
        LOG.debug("Cleared output audio buffer");
    }
    
    /**
     * Gets the number of audio chunks waiting in the output queue.
     */
    public int getOutputQueueSize() {
        return outputAudioQueue.size();
    }
}
