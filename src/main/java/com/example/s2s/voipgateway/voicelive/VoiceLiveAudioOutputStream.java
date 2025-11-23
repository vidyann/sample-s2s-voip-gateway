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

import com.example.s2s.voipgateway.voicelive.transcode.PcmToULawTranscoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * InputStream that provides audio FROM Voice Live API for SIP transmission.
 * Converts PCM16 24kHz audio from Voice Live to µ-law 8kHz for SIP RTP.
 * RtpStreamSender reads FROM this stream.
 * 
 * Audio flow: Voice Live (PCM16 24kHz) → resample → encode → read() → SIP RTP (µ-law 8kHz)
 */
public class VoiceLiveAudioOutputStream extends InputStream {
    private static final Logger LOG = LoggerFactory.getLogger(VoiceLiveAudioOutputStream.class);
    
    // RTP packet size for µ-law 8kHz: 160 bytes = 20ms of audio
    private static final int RTP_PACKET_SIZE = 160;
    
    private static final int QUEUE_CAPACITY_PACKETS = 1200; // hard cap ~24 seconds (enough for very long Voice Live responses)
    private static final int QUEUE_HIGH_WATER_PACKETS = 150; // (UNUSED)
    private static final int QUEUE_TARGET_PACKETS = 140; // (UNUSED)
    private static final int MAX_TRIM_PER_CALL = 6; // (UNUSED)
    
    // Dynamic buffering water marks to handle Voice Live's bursty delivery
    private static final int LOW_WATER_MARK = 100;  // ~2 seconds cushion - pause RTP when queue drops below this
    private static final int HIGH_WATER_MARK = 150; // ~3 seconds cushion - resume RTP when queue refills to this

    private final VoiceLiveStreamHandler handler;
    private final LinkedBlockingQueue<byte[]> outputQueue;
    private volatile boolean closed = false;
    private volatile boolean ready = false; // Don't start RTP until we have audio buffered
    private volatile boolean isPaused = false; // Dynamic buffering: pause RTP when queue is low
    
    // Packet buffer for breaking large chunks into RTP-sized packets
    private byte[] packetBuffer = new byte[8000]; // 1 second buffer
    private static final int MIN_PREBUFFER_PACKETS = 25; // Wait for ~500ms of audio before starting RTP (smooth bursty delivery)
    private int packetBufferPos = 0;
    
    public VoiceLiveAudioOutputStream(VoiceLiveStreamHandler handler) {
        this.handler = handler;
        // UNBOUNDED queue - no capacity limit, can grow as large as needed for long Voice Live responses
        this.outputQueue = new LinkedBlockingQueue<>();
        
        LOG.info("🔧 VoiceLiveAudioOutputStream created - handler: {}, handler queue size: {}, response done: {}", 
                 System.identityHashCode(handler), 
                 handler.hasAudioOutput() ? "has audio" : "empty",
                 handler.isResponseDone());
        
        // Start background thread to poll Voice Live audio
        startAudioReceiver();
        
        LOG.info("VoiceLiveAudioOutputStream initialized (PCM16 24kHz → µ-law 8kHz, UNBOUNDED buffer)");
    }
    

    
    /**
     * Start background thread to receive audio from Voice Live API
     */
    private void startAudioReceiver() {
        Thread receiverThread = new Thread(() -> {
            LOG.info("Voice Live audio receiver thread started");
            
            // Check if proactive greeting already completed before call connected
            if (handler.isResponseDone() && handler.hasAudioOutput()) {
                ready = true;
                LOG.info("✓ Response complete before call connected - ready for immediate playback");
            }
            
            while (!closed) {
                try {
                    // Poll handler for Voice Live audio chunks (response.audio.delta events)
                    byte[] pcm24k = handler.getAudioOutput(50); // 50ms timeout
                    
                    if (pcm24k != null && pcm24k.length > 0) {
                        LOG.debug("← Voice Live sent {} bytes PCM24k", pcm24k.length);
                        processAudioFromVoiceLive(pcm24k);
                    }
                    
                    // Set ready when we have enough packets buffered (for both pre-call and normal responses)
                    if (!ready && outputQueue.size() >= MIN_PREBUFFER_PACKETS) {
                        ready = true;
                        LOG.info("✓ Prebuffering complete - {} packets ready, starting RTP transmission", outputQueue.size());
                    }
                    
                } catch (Exception e) {
                    LOG.error("Error in audio receiver thread", e);
                }
            }
            
            LOG.info("Voice Live audio receiver thread stopped");
        }, "VoiceLive-Audio-Receiver");
        
        receiverThread.setDaemon(true);
        receiverThread.start();
    }
    
    /**
     * Process audio received from Voice Live API.
     * This should be called by the event handler when audio responses arrive.
     * 
     * @param pcm24k PCM16 24kHz audio data from Voice Live
     */
    public void processAudioFromVoiceLive(byte[] pcm24k) {
        try {
            if (pcm24k == null || pcm24k.length == 0) {
                return;
            }
            
            // Convert PCM16 24kHz → µ-law 8kHz
            processAudioChunk(pcm24k);
            
        } catch (Exception e) {
            LOG.error("Error processing audio from Voice Live", e);
        }
    }
    
    /**
     * Process PCM16 24kHz audio chunk: resample → encode → packetize → queue
     * 
     * RATE CONTROL: If queue is getting large, slow down chunk processing to prevent
     * massive bursts from causing voice speed-up. This allows RTP consumption
     * to catch up naturally.
     * 
     * @param pcm24k PCM16 audio at 24kHz
     */
    private void processAudioChunk(byte[] pcm24k) {
        try {
            // Step 1: Downsample PCM16 24kHz → PCM16 8kHz
            byte[] pcm8k = AudioResampler.downsample24to8(pcm24k);
            
            // Step 2: Encode PCM16 → µ-law
            byte[] ulawData = PcmToULawTranscoder.transcodeBytes(pcm8k);
            
            // Step 3: Break into RTP-sized packets (160 bytes each)
            packetizeAndQueue(ulawData);
            
        } catch (Exception e) {
            LOG.error("Error processing audio chunk", e);
        }
    }
    
    /**
     * Break audio data into RTP-sized packets (160 bytes = 20ms)
     * This ensures smooth playback by providing consistent packet sizes.
     * 
     * IMPORTANT: Implements adaptive rate control to maintain consistent playback speed.
     * When the queue is large, we add packets more slowly to prevent speed-up.
     */
    private synchronized void packetizeAndQueue(byte[] ulawData) {
        int sourcePos = 0;
        int droppedPackets = 0;
        
        while (sourcePos < ulawData.length) {
            // Calculate how much we can add to current packet
            int spaceInPacket = RTP_PACKET_SIZE - packetBufferPos;
            int bytesToCopy = Math.min(spaceInPacket, ulawData.length - sourcePos);
            
            // Copy data to packet buffer
            System.arraycopy(ulawData, sourcePos, packetBuffer, packetBufferPos, bytesToCopy);
            packetBufferPos += bytesToCopy;
            sourcePos += bytesToCopy;
            
            // If packet is full, queue it
            if (packetBufferPos >= RTP_PACKET_SIZE) {
                byte[] packet = new byte[RTP_PACKET_SIZE];
                System.arraycopy(packetBuffer, 0, packet, 0, RTP_PACKET_SIZE);
                
                // Calculate remainder BEFORE any potential interruption
                int remainder = packetBufferPos - RTP_PACKET_SIZE;
                
                // Try to add packet to queue (non-blocking)
                // Queue size check is just for logging prebuffer status
                int queueSize = outputQueue.size();
                
                if (!outputQueue.offer(packet)) {
                    // Queue full - just drop the packet (should never happen with 1200-packet capacity)
                    droppedPackets++;
                } else {
                    // Set ready flag immediately when prebuffer threshold reached (no polling delay)
                    if (!ready && queueSize >= MIN_PREBUFFER_PACKETS) {
                        ready = true;
                        LOG.info("✓ Prebuffering complete - {} packets ready, starting RTP transmission", queueSize);
                    }
                }
                
                // Move any remainder to start of buffer (use pre-calculated remainder to avoid race condition)
                if (remainder > 0 && remainder <= packetBuffer.length - RTP_PACKET_SIZE) {
                    System.arraycopy(packetBuffer, RTP_PACKET_SIZE, packetBuffer, 0, remainder);
                    packetBufferPos = remainder;
                } else {
                    // Safety: If interrupted or invalid state, reset buffer position
                    packetBufferPos = 0;
                }
            }
        }
        
        // Trim queue once after processing entire chunk (prevents multiple aggressive trims during bursts)
        // DISABLED: Trimming causes jitter. Accept higher latency for smooth playback.
        // trimQueueIfNeeded();
        
        // Log if old packets were replaced to maintain queue flow
        if (droppedPackets > 0) {
            LOG.info("ℹ️ Replaced {} old packets with new ones to maintain queue flow (keeping most recent 16s audio)", droppedPackets);
        }
    }

    /**
     * Keep queue latency bounded by trimming oldest packets when backlog is excessive.
     */
    private void trimQueueIfNeeded() {
        int queueSize = outputQueue.size();
        if (queueSize <= QUEUE_HIGH_WATER_PACKETS) {
            return;
        }

        int trimmed = 0;
        while (queueSize > QUEUE_TARGET_PACKETS && trimmed < MAX_TRIM_PER_CALL) {
            byte[] dropped = outputQueue.poll();
            if (dropped == null) {
                break;
            }
            trimmed++;
            queueSize--;
        }

        if (trimmed > 0) {
            LOG.warn("⚠ Queue trimmed {} packets to cap latency (size now {})", trimmed, queueSize);
        }
    }
    
    /**
     * Read single byte from stream (required by InputStream).
     * Reads FROM Voice Live audio queue for RTP transmission.
     */
    @Override
    public int read() throws IOException {
        if (closed) {
            return -1;
        }
        
        byte[] buffer = new byte[1];
        int bytesRead = read(buffer, 0, 1);
        return (bytesRead == 1) ? (buffer[0] & 0xFF) : -1;
    }
    
    /**
     * Read processed audio data from the output queue.
     * This is called by RTP sender to get µ-law 8kHz audio for transmission.
     * RtpStreamSender reads FROM this stream.
     * 
     * CRITICAL: mjSIP may request larger reads than 160 bytes. We need to return
     * as much data as available up to the requested length to avoid slow consumption.
     * 
     * @param buffer Buffer to read into
     * @param offset Offset in buffer
     * @param length Maximum bytes to read
     * @return Number of bytes read, or -1 if stream closed, or 0 if no data available
     */
    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (closed) {
            return -1;
        }
        
        // Wait for prebuffering to complete before allowing RTP to consume
        // ready flag is now set immediately by audio receiver thread when threshold reached
        if (!ready) {
            // Not ready yet - return silence instead of blocking
            // This allows RTP to keep calling without blocking the thread
            java.util.Arrays.fill(buffer, offset, offset + length, (byte)0xFF);
            return length;
        }
        
        int queueSize = outputQueue.size();
        boolean responseDone = handler.isResponseDone();
        LOG.info("🔊 RTP read() called - requested {} bytes, queue size: {}, paused: {}, response.done: {}", length, queueSize, isPaused, responseDone);
        
        // Dynamic buffering: Pause RTP when queue runs low (Voice Live delivery gaps)
        // BUT: Don't pause if Voice Live already finished sending (response.done) - just play remaining queue
        if (!isPaused && queueSize < LOW_WATER_MARK && !responseDone) {
            isPaused = true;
            LOG.warn("⏸️ Queue low ({} packets < {}), pausing RTP to wait for Voice Live", queueSize, LOW_WATER_MARK);
        }
        
        // If response is done and queue is low, don't pause - just play what's left
        if (isPaused && responseDone) {
            isPaused = false;
            LOG.info("▶️ Response complete (response.done), resuming RTP to finish playback");
        }
        
        // Resume RTP when buffer replenishes
        if (isPaused && queueSize >= HIGH_WATER_MARK) {
            isPaused = false;
            LOG.info("▶️ Buffer replenished ({} packets >= {}), resuming RTP", queueSize, HIGH_WATER_MARK);
        }
        
        // While paused, return silence (µ-law silence = 0xFF)
        if (isPaused) {
            java.util.Arrays.fill(buffer, offset, offset + length, (byte)0xFF);
            return length;
        }
        
        try {
            int totalRead = 0;
            
            // Read multiple packets if mjSIP requests more than 160 bytes
            // This dramatically improves consumption rate
            while (totalRead < length) {
                int remainingSpace = length - totalRead;
                if (remainingSpace < RTP_PACKET_SIZE) {
                    break; // Not enough space for another full packet
                }
                
                // Wait for first packet, then poll quickly for additional packets
                long timeout = (totalRead == 0) ? 40 : 5; // 40ms for first, 5ms for subsequent
                byte[] packet = outputQueue.poll(timeout, TimeUnit.MILLISECONDS);
                
                if (packet == null) {
                    if (totalRead == 0) {
                        // No data at all - queue empty
                        if (ready && outputQueue.isEmpty()) {
                            ready = false; // Reset for next response
                            LOG.debug("Queue drained - prebuffering reset for next response");
                            return 0; // Return silence without warning
                        }
                        // Log underrun only if we're not prebuffering
                        if (ready) {
                            LOG.warn("⚠ RTP underrun - no audio data available (queue empty)");
                        }
                        return 0;
                    }
                    // Return what we have so far
                    break;
                }
                
                // Copy packet data
                int bytesToCopy = Math.min(packet.length, remainingSpace);
                System.arraycopy(packet, 0, buffer, offset + totalRead, bytesToCopy);
                totalRead += bytesToCopy;
            }
            
            // Log queue health periodically
            int currentQueueSize = outputQueue.size();
            if (currentQueueSize < 20) {
                LOG.debug("Queue health: {} packets remaining (read {} bytes)", currentQueueSize, totalRead);
            } else if (currentQueueSize > 800) {
                LOG.warn("⚠ Queue high: {} packets queued", currentQueueSize);
            }
            
            return totalRead;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }
    
    /**
     * Clear all buffered audio immediately (for interrupts).
     */
    public void clearBuffer() {
        outputQueue.clear();
        packetBufferPos = 0; // Also clear partial packet buffer
        ready = false; // Reset ready flag so next response will prebuffer properly
        isPaused = false; // Reset pause flag for new response
        LOG.info("🔇 Audio buffers cleared (interrupt) - ready and pause flags reset for new response");
    }
    
    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            outputQueue.clear();
            LOG.info("VoiceLiveAudioOutputStream closed");
        }
        super.close();
    }
}
