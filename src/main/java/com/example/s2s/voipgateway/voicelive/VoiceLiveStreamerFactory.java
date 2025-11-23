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

import org.mjsip.media.AudioStreamer;
import org.mjsip.media.FlowSpec;
import org.mjsip.media.MediaStreamer;
import org.mjsip.media.StreamerOptions;
import org.mjsip.media.rx.AudioReceiver;
import org.mjsip.media.tx.AudioTransmitter;
import org.mjsip.ua.streamer.StreamerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * StreamerFactory implementation for Azure Speech Voice Live API.
 * Creates audio streamers that bridge SIP RTP (ulaw 8kHz) with Voice Live API (PCM16 24kHz).
 */
public class VoiceLiveStreamerFactory implements StreamerFactory {
    private static final Logger LOG = LoggerFactory.getLogger(VoiceLiveStreamerFactory.class);
    
    private final VoiceLiveStreamHandler handler;
    
    /**
     * Creates a new Voice Live streamer factory.
     * 
     * @param handler The Voice Live stream handler with active session
     */
    public VoiceLiveStreamerFactory(VoiceLiveStreamHandler handler) {
        this.handler = handler;
    }
    
    @Override
    public MediaStreamer createMediaStreamer(Executor executor, FlowSpec flowSpec) {
        LOG.info("Creating Voice Live audio streamer for session");
        
        // AudioReceiver: Receives RTP FROM SIP caller → Sends to Voice Live (INPUT)
        // AudioTransmitter: Reads FROM Voice Live response → Sends RTP TO SIP caller (OUTPUT)
        AudioReceiver rx = new VoiceLiveAudioInput(handler);  // SIP → Voice Live
        VoiceLiveAudioOutput tx = new VoiceLiveAudioOutput(handler);  // Voice Live → SIP
        
        // Set audio output reference for interrupt handling
        handler.setAudioOutput(tx);
        
        // Configure streamer options
        StreamerOptions options = StreamerOptions.builder()
                .setSymmetricRtp(true)  // Use symmetric RTP for NAT traversal
                .build();
        
        LOG.info("✓ Voice Live audio streamer created");
        return new AudioStreamer(executor, flowSpec, tx, rx, options);
    }
}
