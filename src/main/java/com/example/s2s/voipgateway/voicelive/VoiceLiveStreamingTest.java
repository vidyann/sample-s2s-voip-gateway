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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test program that simulates real-time SIP streaming by sending audio in small chunks.
 * Uses official Azure Voice Live SDK with session-based API.
 * This approach more closely matches how a VoIP gateway would stream audio in real-time,
 * sending 20ms or 100ms packets as they arrive from the phone.
 */
public class VoiceLiveStreamingTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(VoiceLiveStreamingTest.class);
    
    // Audio streaming configuration
    private static final int SAMPLE_RATE = 24000;  // 24kHz
    private static final int BYTES_PER_SAMPLE = 2; // 16-bit PCM = 2 bytes
    private static final int CHUNK_DURATION_MS = 100; // Send 100ms chunks (typical for VoIP)
    private static final int CHUNK_SIZE_BYTES = (SAMPLE_RATE * CHUNK_DURATION_MS / 1000) * BYTES_PER_SAMPLE; // 4800 bytes
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java VoiceLiveStreamingTest <audio-file.wav>");
            System.err.println("Example: java VoiceLiveStreamingTest hello-how.wav");
            System.err.println("\nThis test simulates real-time SIP streaming by sending audio in " + CHUNK_DURATION_MS + "ms chunks");
            System.exit(1);
        }
        
        String audioFile = args[0];
        
        // Load configuration from environment
        VoiceLiveConfig config = new VoiceLiveConfig();
        LOG.info("Voice Live Configuration:");
        LOG.info("  Endpoint: {}", config.getEndpoint());
        LOG.info("  Model: {}", config.getModel());
        LOG.info("\nStreaming Configuration:");
        LOG.info("  Chunk size: {}ms ({} bytes)", CHUNK_DURATION_MS, CHUNK_SIZE_BYTES);
        LOG.info("  Sample rate: {}Hz", SAMPLE_RATE);
        
        // Atomic counters for tracking
        AtomicInteger totalAudioReceived = new AtomicInteger(0);
        AtomicReference<String> fullTranscript = new AtomicReference<>("");
        AtomicReference<String> userTranscript = new AtomicReference<>("");
        CountDownLatch completionLatch = new CountDownLatch(1);
        
        // Create Voice Live client using official SDK
        VoiceLiveClient client = new VoiceLiveClient(config);
        
        LOG.info("\n→ Starting Voice Live session...");
        
        // Start session and handle events
        client.startSession(config.getModel())
            .flatMap(session -> {
                LOG.info("✓ Session started successfully");
                
                // Subscribe to events with typed handlers
                session.receiveEvents()
                    .subscribe(
                        event -> handleEvent(event, totalAudioReceived, fullTranscript, userTranscript, completionLatch),
                        error -> {
                            LOG.error("❌ Error receiving events", error);
                            completionLatch.countDown();
                        },
                        () -> {
                            LOG.info("Event stream completed");
                            completionLatch.countDown();
                        }
                );
                
                // Create stream handler with voice, instructions and transcription model from config
                VoiceLiveStreamHandler handler = new VoiceLiveStreamHandler(
                    session, 
                    config.getVoice(), 
                    config.getInstructions(), 
                    config.getTranscriptionModel(), 
                    config.getTranscriptionLanguage(), 
                    config.getMaxResponseOutputTokens(),
                    false,  // proactiveGreetingEnabled
                    ""      // proactiveGreeting
                );
                
                // Wait for session to be ready (SESSION_UPDATED event) - reactively!
                LOG.info("⏳ Initializing handler and waiting for session to be ready...");
                
                return handler.initialize()
                    .timeout(Duration.ofSeconds(10))
                    .doOnSuccess(v -> LOG.info("✓ Session is ready!"))
                    .doOnError(ex -> LOG.error("Failed to initialize: {}", ex.getMessage()))
                    .then(Mono.defer(() -> {
                        LOG.info("✓ Session configured with Voice Live features");
                        LOG.info("→ About to load audio file...");
                        
                        try {
                            // Read audio file
                            LOG.info("\n→ Reading audio file: {}", audioFile);
                            byte[] audioData = loadAudioFile(audioFile);
                            LOG.info("  Audio loaded: {} bytes (~{} seconds)", audioData.length,
                                     String.format("%.1f", (double) audioData.length / (SAMPLE_RATE * BYTES_PER_SAMPLE)));
                            
                            // Calculate number of chunks
                            int totalChunks = (int) Math.ceil((double) audioData.length / CHUNK_SIZE_BYTES);
                            LOG.info("  Will stream in {} chunks of {}ms each", totalChunks, CHUNK_DURATION_MS);
                            
                            // Stream audio in chunks
                            LOG.info("\n→ Streaming audio in real-time ({}ms chunks)...", CHUNK_DURATION_MS);
                            Mono<Void> streamMono = streamAudioInChunks(session, audioData, totalChunks);
                            LOG.info("→ Returning stream Mono to chain");
                            return streamMono;
                        } catch (Exception e) {
                            LOG.error("Error loading/streaming audio", e);
                            return Mono.error(e);
                        }
                    }))
                    .doOnSubscribe(s -> LOG.info("→ Audio streaming chain subscribed"))
                    .doOnSuccess(v -> LOG.info("→ Audio streaming completed successfully"));
            })
            .doOnSuccess(v -> LOG.info("✓ All audio chunks sent"))
            .doOnError(error -> LOG.error("❌ Error during streaming", error))
            .subscribe(
                v -> {}, // onNext
                error -> LOG.error("❌ Subscription error: {}", error.getMessage(), error) // onError
            );
        
        // Wait for response completion (timeout after 30 seconds)
        if (!completionLatch.await(30, TimeUnit.SECONDS)) {
            LOG.warn("⚠️  Test timed out waiting for response");
        }
        
        // Print summary
        LOG.info("\n" + "=".repeat(80));
        LOG.info("Test Summary:");
        LOG.info("  Audio received: {} bytes (~{} seconds)", 
                 totalAudioReceived.get(),
                 String.format("%.1f", (double) totalAudioReceived.get() / (SAMPLE_RATE * BYTES_PER_SAMPLE)));
        LOG.info("  Streaming approach: Real-time SIP simulation ({}ms chunks)", CHUNK_DURATION_MS);
        if (!userTranscript.get().isEmpty()) {
            LOG.info("  User transcript: \"{}\"", userTranscript.get());
        }
        if (!fullTranscript.get().isEmpty()) {
            LOG.info("  AI Response: \"{}\"", fullTranscript.get());
        }
        LOG.info("=".repeat(80));
        LOG.info("\n✓ Streaming test complete!");
        
        // Give some time for cleanup
        Thread.sleep(1000);
        System.exit(0);
    }
    
    /**
     * Handles typed events from the Voice Live SDK.
     */
    private static void handleEvent(SessionUpdate event, AtomicInteger totalAudioReceived,
                                   AtomicReference<String> fullTranscript, 
                                   AtomicReference<String> userTranscript,
                                   CountDownLatch completionLatch) {
        try {
            switch (event) {
                case SessionUpdateSessionCreated created ->
                    LOG.info("✓ Session created: {}", created.getSession().getId());
                    
                case SessionUpdateSessionUpdated updated ->
                    LOG.info("✓ Session updated");
                    
                case SessionUpdateResponseAudioDelta audioDelta -> {
                    byte[] audioChunk = audioDelta.getDelta();
                    int total = totalAudioReceived.addAndGet(audioChunk.length);
                    LOG.info("← Received audio chunk: {} bytes (total: {} bytes, ~{} seconds)",
                             audioChunk.length, total,
                             total / (SAMPLE_RATE * BYTES_PER_SAMPLE));
                }
                    
                case SessionUpdateResponseAudioDone audioDone -> {
                    int total = totalAudioReceived.get();
                    double durationSeconds = (double) total / (SAMPLE_RATE * BYTES_PER_SAMPLE);
                    LOG.info("✓ Audio response complete: {} bytes (~{} seconds)", total,
                             String.format("%.1f", durationSeconds));
                }
                    
                case SessionUpdateResponseAudioTimestampDelta timestamp ->
                    LOG.info("  Timestamp [{}ms]: \"{}\"", 
                             timestamp.getAudioOffsetMs(), timestamp.getText());
                    
                case SessionUpdateResponseTextDelta textDelta -> {
                    String delta = textDelta.getDelta();
                    fullTranscript.updateAndGet(current -> current + delta);
                    System.out.print(delta);
                }
                    
                case SessionUpdateResponseAudioTranscriptDelta audioTranscriptDelta -> {
                    String delta = audioTranscriptDelta.getDelta();
                    if (delta != null && !delta.isEmpty()) {
                        fullTranscript.updateAndGet(current -> current + delta);
                        System.out.print(delta); // Print in real-time
                    }
                }
                    
                case SessionUpdateConversationItemInputAudioTranscriptionCompleted transcription -> {
                    userTranscript.set(transcription.getTranscript());
                    LOG.info("\n✓ User said: \"{}\"", transcription.getTranscript());
                }
                    
                case SessionUpdateResponseDone done -> {
                    LOG.info("✓ Response generation complete");
                    completionLatch.countDown();
                }
                    
                case SessionUpdateInputAudioBufferSpeechStarted speechStart ->
                    LOG.info("🎤 Speech detected");
                    
                case SessionUpdateInputAudioBufferSpeechStopped speechStop ->
                    LOG.info("🤔 Speech ended - processing...");
                    
                case SessionUpdateError error -> {
                    LOG.error("❌ Voice Live error: {}", error.getError().getMessage());
                    completionLatch.countDown();
                }
                    
                default ->
                    LOG.debug("Unhandled event: {}", event.getType());
            }
        } catch (Exception e) {
            LOG.error("Error handling event", e);
        }
    }
    
    /**
     * Streams audio data in chunks with delays to simulate real-time streaming.
     * Uses fully reactive approach with Flux for proper chain integration.
     */
    private static Mono<Void> streamAudioInChunks(VoiceLiveSessionAsyncClient session, 
                                                  byte[] audioData, int totalChunks) {
        // Create chunks as a list
        List<byte[]> chunks = new ArrayList<>();
        int offset = 0;
        
        while (offset < audioData.length) {
            int bytesToSend = Math.min(CHUNK_SIZE_BYTES, audioData.length - offset);
            byte[] chunk = Arrays.copyOfRange(audioData, offset, offset + bytesToSend);
            chunks.add(chunk);
            offset += bytesToSend;
        }
        
        // Add final silence chunk for VAD end-of-utterance detection
        int silenceBytes = (SAMPLE_RATE * 1000 / 1000) * BYTES_PER_SAMPLE; // 1 second
        byte[] silence = new byte[silenceBytes];
        chunks.add(silence);
        
        LOG.info("  Will stream {} audio chunks + 1 silence chunk", chunks.size() - 1);
        
        // Stream chunks reactively with delay between each
        return Flux.fromIterable(chunks)
            .index() // Add index for logging
            .concatMap(tuple -> {
                long index = tuple.getT1();
                byte[] chunk = tuple.getT2();
                
                // Determine if this is the silence chunk
                boolean isSilence = (index == chunks.size() - 1);
                
                if (isSilence) {
                    LOG.info("\n→ Sending final silence for VAD end-of-utterance detection...");
                    LOG.info("  → Sent {} bytes of silence ({}ms)", chunk.length, 1000);
                } else {
                    LOG.info("  → Sent chunk {}/{}: {} bytes", index + 1, totalChunks, chunk.length);
                }
                
                // Send chunk and delay before next (except after last chunk)
                Mono<Void> sendMono = session.sendInputAudio(BinaryData.fromBytes(chunk));
                
                if (index < chunks.size() - 1) {
                    return sendMono.delayElement(Duration.ofMillis(CHUNK_DURATION_MS));
                } else {
                    LOG.info("\n⏳ Waiting for VAD to detect end of speech and generate response...");
                    return sendMono;
                }
            })
            .then(); // Convert Flux<Void> to Mono<Void>
    }
    
    /**
     * Loads audio file and converts to PCM16 24kHz format.
     */
    private static byte[] loadAudioFile(String filePath) throws IOException, UnsupportedAudioFileException {
        File file = new File(filePath);
        AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
        AudioFormat sourceFormat = audioStream.getFormat();
        
        LOG.debug("Source format: {} Hz, {} channels, {} bits",
                 sourceFormat.getSampleRate(),
                 sourceFormat.getChannels(),
                 sourceFormat.getSampleSizeInBits());
        
        // Voice Live expects PCM16 at 24kHz mono
        AudioFormat targetFormat = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            24000,  // 24kHz
            16,     // 16-bit
            1,      // Mono
            2,      // Frame size (2 bytes per sample)
            24000,  // Frame rate
            false   // Little-endian
        );
        
        // Convert if necessary
        AudioInputStream convertedStream = audioStream;
        if (!sourceFormat.matches(targetFormat)) {
            LOG.debug("Converting to: 24kHz PCM16 mono");
            convertedStream = AudioSystem.getAudioInputStream(targetFormat, audioStream);
        }
        
        // Read all bytes
        byte[] audioData = convertedStream.readAllBytes();
        convertedStream.close();
        
        return audioData;
    }
}
