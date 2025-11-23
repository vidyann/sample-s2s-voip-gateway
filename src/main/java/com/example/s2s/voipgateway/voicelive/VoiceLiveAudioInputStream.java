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

import com.example.s2s.voipgateway.voicelive.transcode.UlawToPcmTranscoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * OutputStream that receives SIP audio (µ-law 8kHz) and sends to Voice Live API (PCM16 24kHz).
 * RtpStreamReceiver writes decoded RTP audio TO this stream.
 * 
 * Audio flow: SIP RTP → RtpStreamReceiver → This OutputStream → Voice Live
 */
public class VoiceLiveAudioInputStream extends OutputStream {
    private static final Logger LOG = LoggerFactory.getLogger(VoiceLiveAudioInputStream.class);
    
    private final VoiceLiveStreamHandler handler;
    private final UlawToPcmTranscoder ulawDecoder;
    
    public VoiceLiveAudioInputStream(VoiceLiveStreamHandler handler) {
        this.handler = handler;
        this.ulawDecoder = new UlawToPcmTranscoder();
        
        LOG.info("VoiceLiveAudioInputStream initialized (µ-law 8kHz → PCM16 24kHz)");
    }
    
    /**
     * Write a single byte. Required by OutputStream but not typically used.
     */
    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }
    
    /**
     * Write audio data to Voice Live. This is called by RtpStreamReceiver.
     * Receives µ-law 8kHz audio, converts to PCM16 24kHz, and sends to Voice Live.
     */
    @Override
    public void write(byte[] ulawData, int offset, int length) throws IOException {
        try {
            if (ulawData == null || length == 0) {
                return;
            }
            
            // If offset is not zero, create a new array with just the data we need
            byte[] dataToProcess;
            if (offset == 0 && length == ulawData.length) {
                dataToProcess = ulawData;
            } else {
                dataToProcess = new byte[length];
                System.arraycopy(ulawData, offset, dataToProcess, 0, length);
            }
            
            // Step 1: Decode µ-law → PCM16 (8kHz)
            byte[] pcm8k = UlawToPcmTranscoder.convertByteArray(dataToProcess);
            
            // Step 2: Resample PCM16 8kHz → PCM16 24kHz
            byte[] pcm24k = AudioResampler.upsample8to24(pcm8k);
            
            // Step 3: Send to Voice Live API
            handler.sendAudioInput(pcm24k)
                .doOnError(error -> LOG.error("Failed to send audio to Voice Live", error))
                .subscribe(); // Fire and forget
            
        } catch (Exception e) {
            LOG.error("Error processing audio data", e);
        }
    }
    
    @Override
    public void close() throws IOException {
        super.close();
        LOG.info("VoiceLiveAudioInputStream closed");
    }
}
