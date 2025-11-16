package com.example.s2s.voipgateway.voicelive;

import com.example.s2s.voipgateway.nova.transcode.PcmToULawTranscoder;
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
    
    private final VoiceLiveStreamHandler handler;
    private final LinkedBlockingQueue<byte[]> outputQueue;
    private volatile boolean closed = false;
    
    // Packet buffer for breaking large chunks into RTP-sized packets
    private byte[] packetBuffer = new byte[8000]; // 1 second buffer
    private int packetBufferPos = 0;
    
    public VoiceLiveAudioOutputStream(VoiceLiveStreamHandler handler) {
        this.handler = handler;
        this.outputQueue = new LinkedBlockingQueue<>(500); // 500 packets = 10 seconds buffer
        
        // Start background thread to poll Voice Live audio
        startAudioReceiver();
        
        LOG.info("VoiceLiveAudioOutputStream initialized (PCM16 24kHz → µ-law 8kHz)");
    }
    
    /**
     * Start background thread to receive audio from Voice Live API
     */
    private void startAudioReceiver() {
        Thread receiverThread = new Thread(() -> {
            LOG.info("Voice Live audio receiver thread started");
            
            while (!closed) {
                try {
                    // Poll handler for Voice Live audio chunks (response.audio.delta events)
                    byte[] pcm24k = handler.getAudioOutput(50); // 50ms timeout
                    
                    if (pcm24k != null && pcm24k.length > 0) {
                        LOG.info("← Processing {} bytes PCM24k from Voice Live", pcm24k.length);
                        processAudioFromVoiceLive(pcm24k);
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
     * @param pcm24k PCM16 audio at 24kHz
     */
    private void processAudioChunk(byte[] pcm24k) {
        try {
            // Step 1: Downsample PCM16 24kHz → PCM16 8kHz
            byte[] pcm8k = AudioResampler.downsample24to8(pcm24k);
            LOG.info("  → Downsampled to {} bytes PCM8k", pcm8k.length);
            
            // Step 2: Encode PCM16 → µ-law
            byte[] ulawData = PcmToULawTranscoder.transcodeBytes(pcm8k);
            LOG.info("  → Encoded to {} bytes µ-law", ulawData.length);
            
            // Step 3: Break into RTP-sized packets (160 bytes each)
            packetizeAndQueue(ulawData);
            LOG.info("  → Packetized and queued {} bytes", ulawData.length);
            
        } catch (Exception e) {
            LOG.error("Error processing audio chunk", e);
        }
    }
    
    /**
     * Break audio data into RTP-sized packets (160 bytes = 20ms)
     * This ensures smooth playback by providing consistent packet sizes
     */
    private synchronized void packetizeAndQueue(byte[] ulawData) throws InterruptedException {
        int sourcePos = 0;
        
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
                
                if (!outputQueue.offer(packet, 100, TimeUnit.MILLISECONDS)) {
                    LOG.warn("Output queue full, dropping packet");
                } else {
                    LOG.info("  → Queued RTP packet: {} bytes (queue size: {})", packet.length, outputQueue.size());
                }
                
                // Move any remainder to start of buffer
                int remainder = packetBufferPos - RTP_PACKET_SIZE;
                if (remainder > 0) {
                    System.arraycopy(packetBuffer, RTP_PACKET_SIZE, packetBuffer, 0, remainder);
                }
                packetBufferPos = remainder;
            }
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
        
        try {
            // Wait up to 20ms for audio data (matches RTP packet duration)
            // This provides smooth audio playback by blocking until data is available
            byte[] packet = outputQueue.poll(20, TimeUnit.MILLISECONDS);
            if (packet == null) {
                // No data available - return 0 to signal no data
                return 0;
            }
            
            // All packets should be exactly 160 bytes now
            int bytesToCopy = Math.min(packet.length, length);
            System.arraycopy(packet, 0, buffer, offset, bytesToCopy);
            LOG.info("→ RTP sender reading {} bytes (queue remaining: {})", bytesToCopy, outputQueue.size());
            
            return bytesToCopy;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
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
