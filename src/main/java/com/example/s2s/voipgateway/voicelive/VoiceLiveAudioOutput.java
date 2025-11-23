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

import org.mjsip.media.RtpStreamSender;
import org.mjsip.media.RtpStreamSenderListener;
import org.mjsip.media.tx.AudioTXHandle;
import org.mjsip.media.tx.AudioTransmitter;
import org.mjsip.media.tx.RtpAudioTxHandle;
import org.mjsip.media.tx.RtpSenderOptions;
import org.mjsip.rtp.RtpControl;
import org.mjsip.rtp.RtpPayloadFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zoolu.net.UdpSocket;
import org.zoolu.sound.CodecType;
import org.zoolu.util.Encoder;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;

/**
 * mjSIP AudioTransmitter implementation for Azure Speech Voice Live API.
 * Reads audio FROM Voice Live and sends TO SIP caller via RTP.
 * 
 * Audio flow: Voice Live (PCM16 24kHz) → This transmitter → SIP RTP (ulaw 8kHz)
 */
public class VoiceLiveAudioOutput implements AudioTransmitter {
    private static final Logger LOG = LoggerFactory.getLogger(VoiceLiveAudioOutput.class);
    
    private final VoiceLiveStreamHandler handler;
    private VoiceLiveAudioOutputStream outputStream;
    
    /**
     * Creates a new Voice Live audio output receiver.
     * 
     * @param handler The Voice Live stream handler for receiving audio
     */
    public VoiceLiveAudioOutput(VoiceLiveStreamHandler handler) {
        this.handler = handler;
    }
    
    /**
     * Clear all buffered audio (for interrupts).
     */
    public void clearBuffer() {
        if (outputStream != null) {
            outputStream.clearBuffer();
        }
    }
    
    @Override
    public AudioTXHandle createSender(RtpSenderOptions options, UdpSocket udp_socket, AudioFormat audio_format,
                                      CodecType codec, int payload_type, RtpPayloadFormat payloadFormat,
                                      int sample_rate, int channels, Encoder additional_encoder, long packet_time,
                                      int packet_size, String remote_addr, int remote_port,
                                      RtpStreamSenderListener listener, RtpControl rtpControl) throws IOException {
        
        LOG.info("Creating Voice Live RTP stream sender:");
        LOG.info("  Payload: type={}, format={}", payload_type, payloadFormat);
        LOG.info("  Audio: sampleRate={}Hz, channels={}", sample_rate, channels);
        LOG.info("  Packet: time={}ms, size={} bytes", packet_time, packet_size);
        LOG.info("  Remote: {}:{}", remote_addr, remote_port);
        
        // Create input stream that reads FROM Voice Live and sends TO SIP
        this.outputStream = new VoiceLiveAudioOutputStream(handler);
        LOG.info("VoiceLiveAudioOutputStream initialized (PCM16 24kHz → µ-law 8kHz)");
        LOG.info("Voice Live audio sender thread started");
        
        // Create RTP sender that reads from Voice Live output stream
        RtpStreamSender sender = new RtpStreamSender(
            options,
            this.outputStream,       // Reads Voice Live audio from here
            true,              // Use source for audio input
            payload_type,
            payloadFormat,
            sample_rate,
            channels,
            packet_time,
            packet_size,
            additional_encoder,
            udp_socket,
            remote_addr,
            remote_port,
            rtpControl,
            listener
        );
        
        LOG.info("✓ Voice Live RTP sender created");
        return new RtpAudioTxHandle(sender);
    }
}
