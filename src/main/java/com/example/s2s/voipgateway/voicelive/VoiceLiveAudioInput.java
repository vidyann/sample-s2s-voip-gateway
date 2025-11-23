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

import org.mjsip.media.RtpStreamReceiver;
import org.mjsip.media.RtpStreamReceiverListener;
import org.mjsip.media.rx.AudioReceiver;
import org.mjsip.media.rx.AudioRxHandle;
import org.mjsip.media.rx.RtpAudioRxHandler;
import org.mjsip.media.rx.RtpReceiverOptions;
import org.mjsip.rtp.RtpPayloadFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zoolu.net.UdpSocket;
import org.zoolu.sound.CodecType;
import org.zoolu.util.Encoder;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;

/**
 * mjSIP AudioReceiver implementation for Azure Speech Voice Live API.
 * Receives RTP audio FROM SIP caller and sends to Voice Live.
 * 
 * Audio flow: SIP RTP (ulaw 8kHz) → This receiver → Voice Live (PCM16 24kHz)
 */
public class VoiceLiveAudioInput implements AudioReceiver {
    private static final Logger LOG = LoggerFactory.getLogger(VoiceLiveAudioInput.class);
    
    private final VoiceLiveStreamHandler handler;
    
    /**
     * Creates a new Voice Live audio input transmitter.
     * 
     * @param handler The Voice Live stream handler for sending audio
     */
    public VoiceLiveAudioInput(VoiceLiveStreamHandler handler) {
        this.handler = handler;
    }
    
    @Override
    public AudioRxHandle createReceiver(RtpReceiverOptions options, UdpSocket socket, AudioFormat audio_format,
                                        CodecType codec, int payload_type, RtpPayloadFormat payloadFormat,
                                        int sample_rate, int channels, Encoder additional_decoder,
                                        RtpStreamReceiverListener listener) throws IOException {
        
        LOG.info("Creating Voice Live RTP stream receiver:");
        LOG.info("  Payload: type={}, format={}", payload_type, payloadFormat);
        LOG.info("  Audio: sampleRate={}Hz, channels={}", sample_rate, channels);
        LOG.info("  Remote: receiving RTP packets FROM SIP caller");
        
        // Create output stream that receives RTP packets and sends to Voice Live
        VoiceLiveAudioInputStream outputStream = new VoiceLiveAudioInputStream(handler);
        
        // Create RTP receiver that writes received packets to our output stream
        RtpStreamReceiver receiver = new RtpStreamReceiver(
            options,
            outputStream,      // Writes received RTP audio here
            additional_decoder,
            payloadFormat,
            socket,
            listener
        ) {
            @Override
            protected void onRtpStreamReceiverTerminated(Exception error) {
                super.onRtpStreamReceiverTerminated(error);
                try {
                    outputStream.close();
                } catch (IOException ex) {
                    LOG.error("Error closing Voice Live input stream", ex);
                }
            }
        };
        
        LOG.info("✓ Voice Live RTP receiver created");
        return new RtpAudioRxHandler(receiver);
    }
}
