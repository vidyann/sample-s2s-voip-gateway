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

/**
 * Audio resampling utility for converting between 8kHz and 24kHz sample rates.
 * Handles PCM16 (16-bit little-endian) audio data.
 * 
 * Voice Live requires 24kHz audio, while SIP typically uses 8kHz (G.711).
 * This resampler uses linear interpolation for upsampling and decimation with
 * simple averaging for downsampling.
 */
public class AudioResampler {
    
    /**
     * Upsample PCM16 audio from 8kHz to 24kHz (3x interpolation)
     * Uses linear interpolation between samples.
     * 
     * @param pcm8k 8kHz PCM16 data (little-endian, 2 bytes per sample)
     * @return 24kHz PCM16 data (little-endian, 2 bytes per sample, 3x length)
     */
    public static byte[] upsample8to24(byte[] pcm8k) {
        if (pcm8k == null || pcm8k.length == 0) {
            return new byte[0];
        }
        
        // Ensure we have complete samples (2 bytes per sample)
        int validBytes = pcm8k.length - (pcm8k.length % 2);
        int samples8k = validBytes / 2;
        int samples24k = samples8k * 3;
        byte[] pcm24k = new byte[samples24k * 2];
        
        for (int i = 0; i < samples8k - 1; i++) {
            // Read current and next sample (little-endian)
            short current = (short)((pcm8k[i * 2] & 0xFF) | ((pcm8k[i * 2 + 1] & 0xFF) << 8));
            short next = (short)((pcm8k[(i + 1) * 2] & 0xFF) | ((pcm8k[(i + 1) * 2 + 1] & 0xFF) << 8));
            
            // Generate 3 output samples through linear interpolation
            int outIdx = i * 3;
            
            // Sample 0: original
            writeSample(pcm24k, outIdx, current);
            
            // Sample 1: 1/3 between current and next
            short interpolated1 = (short)((current * 2 + next) / 3);
            writeSample(pcm24k, outIdx + 1, interpolated1);
            
            // Sample 2: 2/3 between current and next
            short interpolated2 = (short)((current + next * 2) / 3);
            writeSample(pcm24k, outIdx + 2, interpolated2);
        }
        
        // Handle last sample (repeat it 3 times)
        if (samples8k > 0) {
            short last = (short)((pcm8k[(samples8k - 1) * 2] & 0xFF) | 
                               ((pcm8k[(samples8k - 1) * 2 + 1] & 0xFF) << 8));
            int outIdx = (samples8k - 1) * 3;
            writeSample(pcm24k, outIdx, last);
            writeSample(pcm24k, outIdx + 1, last);
            writeSample(pcm24k, outIdx + 2, last);
        }
        
        return pcm24k;
    }
    
    /**
     * Downsample PCM16 audio from 24kHz to 8kHz (3x decimation)
     * Uses simple averaging of 3 consecutive samples.
     * 
     * @param pcm24k 24kHz PCM16 data (little-endian, 2 bytes per sample)
     * @return 8kHz PCM16 data (little-endian, 2 bytes per sample, 1/3 length)
     */
    public static byte[] downsample24to8(byte[] pcm24k) {
        if (pcm24k == null || pcm24k.length == 0) {
            return new byte[0];
        }
        
        // Ensure we have complete samples
        int validBytes = pcm24k.length - (pcm24k.length % 2);
        int samples24k = validBytes / 2;
        int samples8k = samples24k / 3;
        byte[] pcm8k = new byte[samples8k * 2];
        
        for (int i = 0; i < samples8k; i++) {
            // Read 3 consecutive samples
            int inIdx = i * 3;
            short sample1 = readSample(pcm24k, inIdx);
            short sample2 = readSample(pcm24k, inIdx + 1);
            short sample3 = readSample(pcm24k, inIdx + 2);
            
            // Average the samples
            short averaged = (short)((sample1 + sample2 + sample3) / 3);
            
            // Write to output (little-endian)
            pcm8k[i * 2] = (byte)(averaged & 0xFF);
            pcm8k[i * 2 + 1] = (byte)((averaged >> 8) & 0xFF);
        }
        
        return pcm8k;
    }
    
    /**
     * Read a 16-bit sample from byte array (little-endian)
     */
    private static short readSample(byte[] data, int sampleIndex) {
        int byteIndex = sampleIndex * 2;
        if (byteIndex + 1 >= data.length) {
            return 0; // Return silence if out of bounds
        }
        return (short)((data[byteIndex] & 0xFF) | ((data[byteIndex + 1] & 0xFF) << 8));
    }
    
    /**
     * Write a 16-bit sample to byte array (little-endian)
     */
    private static void writeSample(byte[] data, int sampleIndex, short sample) {
        int byteIndex = sampleIndex * 2;
        if (byteIndex + 1 < data.length) {
            data[byteIndex] = (byte)(sample & 0xFF);
            data[byteIndex + 1] = (byte)((sample >> 8) & 0xFF);
        }
    }
}
