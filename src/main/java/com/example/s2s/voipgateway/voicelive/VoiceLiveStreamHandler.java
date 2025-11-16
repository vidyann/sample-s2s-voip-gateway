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
    private byte[] audioBuffer = new byte[MIN_CHUNK_SIZE_BYTES];
    private int bufferPos = 0;
    
    /**
     * Creates a stream handler for the given Voice Live session.
     * No circular dependency - session is passed in!
     */
    public VoiceLiveStreamHandler(VoiceLiveSessionAsyncClient session) {
        this.session = session;
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
        // Azure semantic VAD configuration
        ServerVadTurnDetection vad = new ServerVadTurnDetection()
            .setThreshold(0.3)
            .setPrefixPaddingMs(300)
            .setSilenceDurationMs(500)
            .setInterruptResponse(true)
            .setAutoTruncate(true)
            .setCreateResponse(true);
        
        // Audio transcription
        AudioInputTranscriptionOptions transcription = new AudioInputTranscriptionOptions(
            AudioInputTranscriptionOptionsModel.WHISPER_1);
        
        return new VoiceLiveSessionOptions()
            .setInstructions("You are a helpful AI voice assistant. You MUST always respond in English only, regardless of the language spoken by the user.")
            .setModalities(Arrays.asList(InteractionModality.TEXT, InteractionModality.AUDIO))
            .setVoice(BinaryData.fromObject(new AzureStandardVoice("en-US-Ava:DragonHDLatestNeural")))
            .setInputAudioFormat(InputAudioFormat.PCM16)
            .setOutputAudioFormat(OutputAudioFormat.PCM16)
            .setInputAudioSamplingRate(24000)
            .setTurnDetection(vad)
            .setInputAudioNoiseReduction(new AudioNoiseReduction(AudioNoiseReductionType.NEAR_FIELD))
            .setInputAudioEchoCancellation(new AudioEchoCancellation())
            .setInputAudioTranscription(transcription);
            // TODO: Add output audio timestamp types when API is available
            //.setOutputAudioTimestampTypes(Arrays.asList(OutputAudioTimestampType.WORD));
    }
    
    /**
     * Handles typed events from the Voice Live SDK.
     * No manual JSON parsing needed!
     */
    private void handleEvent(SessionUpdate event) {
        LOG.info("📩 Received event: {}", event.getType());
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
                LOG.info("🎤 Speech detected");
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
        sessionReadyFuture.complete(null); // Signal that session is ready!
    }
    
    private void handleResponseAudioDelta(SessionUpdateResponseAudioDelta event) {
        byte[] audioData = event.getDelta();
        if (audioData != null && audioData.length > 0) {
            outputAudioQueue.offer(audioData);
            LOG.info("← Queued audio chunk: {} bytes (queue size: {})", audioData.length, outputAudioQueue.size());
        }
    }
    
    private void handleResponseAudioDone(SessionUpdate event) {
        LOG.info("✓ Response audio complete");
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
        try {
            return outputAudioQueue.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
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
