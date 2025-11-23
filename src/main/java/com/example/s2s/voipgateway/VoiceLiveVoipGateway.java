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

package com.example.s2s.voipgateway;

import com.example.s2s.voipgateway.voicelive.VoiceLiveClient;
import com.example.s2s.voipgateway.voicelive.VoiceLiveConfig;
import com.example.s2s.voipgateway.voicelive.VoiceLiveStreamHandler;
import com.example.s2s.voipgateway.voicelive.VoiceLiveStreamerFactory;
import org.mjsip.config.OptionParser;
import org.mjsip.media.MediaDesc;
import org.mjsip.media.MediaSpec;
import org.mjsip.pool.PortConfig;
import org.mjsip.pool.PortPool;
import org.mjsip.sip.address.NameAddress;
import org.mjsip.sip.address.SipURI;
import org.mjsip.sip.message.SipMessage;
import org.mjsip.sip.provider.SipConfig;
import org.mjsip.sip.provider.SipKeepAlive;
import org.mjsip.sip.provider.SipProvider;
import org.mjsip.sip.provider.SipStack;
import org.mjsip.time.ConfiguredScheduler;
import org.mjsip.time.SchedulerConfig;
import org.mjsip.ua.*;
import org.mjsip.ua.registration.RegistrationClient;
import org.mjsip.ua.streamer.StreamerFactory;
import org.slf4j.LoggerFactory;
import org.zoolu.net.SocketAddress;
import reactor.core.publisher.Mono;

import java.util.Map;


/**
 * VoIP Gateway/User Agent for Azure Speech Voice Live API.
 */
public class VoiceLiveVoipGateway extends RegisteringMultipleUAS {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(VoiceLiveVoipGateway.class);
    
    // Instance variables
    protected final MediaConfig mediaConfig;
    protected final UAConfig uaConfig;
    private StreamerFactory streamerFactory;
    private RegistrationClient _rc;
    private SipKeepAlive keep_alive;

    // *************************** Public methods **************************

    /**
     * Creates a new UA with Voice Live streamer factory.
     */
    public VoiceLiveVoipGateway(SipProvider sipProvider, PortPool portPool, ServiceOptions serviceConfig,
                                UAConfig uaConfig, MediaConfig mediaConfig, StreamerFactory voiceLiveFactory) {
        super(sipProvider, portPool, uaConfig, serviceConfig);
        this.mediaConfig = mediaConfig;
        this.uaConfig = uaConfig;
        this.streamerFactory = voiceLiveFactory;
        
        LOG.info("✓ Using Voice Live streamer factory");
        registerWithKeepAlive();
    }

    /**
     * Disable RegisteringMultipleUAS.register(), which gets called from the constructor.
     * We need _rc to schedule keep-alives, but it's private in the parent class.
     */
    @Override
    public void register() { }

    /**
     * SIP REGISTER with keep-alive packets sent on a schedule.
     */
    public void registerWithKeepAlive() {
        if (this.uaConfig.isRegister()) {
            LOG.info("Registering with {}...", this.uaConfig.getRegistrar());
            this._rc = new RegistrationClient(this.sip_provider, this.uaConfig, this);
            this._rc.loopRegister(this.uaConfig);
            scheduleKeepAlive(uaConfig.getKeepAliveTime());
        } else {
            LOG.info("Registration disabled (REGISTER_WITH_SIP_SERVER=false), listening for incoming calls only");
        }
    }

    private void scheduleKeepAlive(long keepAliveTime) {
        if (keepAliveTime > 0L && _rc != null) {
            SipURI targetUri = this.sip_provider.hasOutboundProxy() ? this.sip_provider.getOutboundProxy() : _rc.getTargetAOR().getAddress().toSipURI();
            String targetHost = targetUri.getHost();
            int targetPort = targetUri.getPort();
            if (targetPort < 0) {
                targetPort = this.sip_provider.sipConfig().getDefaultPort();
            }

            SocketAddress targetSoAddr = new SocketAddress(targetHost, targetPort);
            if (this.keep_alive != null && this.keep_alive.isRunning()) {
                this.keep_alive.halt();
            }

            this.keep_alive = new SipKeepAlive(this.sip_provider, targetSoAddr, (SipMessage)null, keepAliveTime);
            LOG.info("Keep-alive started");
        }
    }

    @Override
    public void unregister() {
        LOG.info("Unregistering with {}...", this.uaConfig.getRegistrar());
        if (this._rc != null) {
            this._rc.unregister();
            this._rc.halt();
            this._rc = null;
        }
    }

    @Override
    protected UserAgentListener createCallHandler(SipMessage msg) {
        LOG.info("createCallHandler called for incoming INVITE");
        register();
        return new UserAgentListenerAdapter() {
            @Override
            public void onUaIncomingCall(UserAgent ua, NameAddress callee, NameAddress caller,
                                         MediaDesc[] media_descs) {
                LOG.info("Incoming call from: {}", callee.getAddress());
                LOG.info("Accepting call with media descriptors: {}", mediaConfig.getMediaDescs());
                ua.accept(new MediaAgent(mediaConfig.getMediaDescs(), streamerFactory));
            }
        };
    }
    
    /**
     * The main method.
     */
    public static void main(String[] args) {
        println("Azure Speech Voice Live Gateway " + SipStack.version);
        
        Map<String, String> environ = System.getenv();
        LOG.info("Starting Azure Speech Voice Live API mode");
        
        try {
            VoiceLiveConfig voiceLiveConfig = new VoiceLiveConfig();
            
            LOG.info("Voice Live configuration:");
            LOG.info("  Endpoint: {}", voiceLiveConfig.getEndpoint());
            LOG.info("  Model: {}", voiceLiveConfig.getModel());
            
            // Configure SIP
            SipConfig sipConfig = new SipConfig();
            UAConfig uaConfig = new UAConfig();
            SchedulerConfig schedulerConfig = new SchedulerConfig();
            PortConfig portConfig = new PortConfig();
            ServiceConfig serviceConfig = new ServiceConfig();
            MediaConfig mediaConfig = new MediaConfig();
            
            if (isConfigured(environ.get("SIP_SERVER"))) {
                // Production mode: configure from environment variables
                configureFromEnvironment(environ, uaConfig, mediaConfig, portConfig, sipConfig);
            } else {
                // Local testing mode: read config file first, then override with env vars
                OptionParser.parseOptions(args, ".mjsip-ua", sipConfig, uaConfig, schedulerConfig, mediaConfig, portConfig, serviceConfig);
                // Override registration setting from environment (defaults to false for local mode)
                uaConfig.setRegister(Boolean.parseBoolean(environ.getOrDefault("REGISTER_WITH_SIP_SERVER", "false")));
                // Set default media descriptors for localhost mode
                mediaConfig.setMediaDescs(createDefaultMediaDescs());
                // Set via address to avoid network unreachable errors during normalize
                if (isConfigured(environ.get("SIP_VIA_ADDR"))) {
                    sipConfig.setViaAddrIPv4(environ.get("SIP_VIA_ADDR"));
                }
            }

            sipConfig.normalize();
            uaConfig.normalize(sipConfig);
            
            // Create Voice Live client using official SDK
            VoiceLiveClient voiceLiveClient = new VoiceLiveClient(voiceLiveConfig);
            
            LOG.info("Starting Voice Live session...");
            
            // Start session and get session client - NO circular dependency!
            voiceLiveClient.startSession(voiceLiveConfig.getModel())
                .flatMap(session -> {
                    LOG.info("✓ Voice Live session started successfully");
                    
                    // Create handler with session - clean dependency flow!
                    VoiceLiveStreamHandler streamHandler = new VoiceLiveStreamHandler(
                        session, 
                        voiceLiveConfig.getVoice(), 
                        voiceLiveConfig.getInstructions(), 
                        voiceLiveConfig.getTranscriptionModel(), 
                        voiceLiveConfig.getTranscriptionLanguage(), 
                        voiceLiveConfig.getMaxResponseOutputTokens(),
                        voiceLiveConfig.isProactiveGreetingEnabled(),
                        voiceLiveConfig.getProactiveGreeting()
                    );
                    
                    // Wait for session initialization to complete (SESSION_UPDATED event)
                    return streamHandler.initialize()
                        .doOnSuccess(v -> {
                            LOG.info("✓ Voice Live session configured with:");
                            LOG.info("  - Azure Semantic VAD with filler word removal");
                            LOG.info("  - Deep noise suppression");
                            LOG.info("  - Server-side echo cancellation");
                            LOG.info("  - End-of-utterance detection");
                            LOG.info("  - Azure HD voice ({})", voiceLiveConfig.getVoice());
                            LOG.info("  - Whisper transcription");
                            LOG.info("  - Word timestamps");
                            LOG.info("  - Proactive greeting: {}", 
                                voiceLiveConfig.isProactiveGreetingEnabled() ? "enabled" : "disabled");
                        })
                        .thenReturn(streamHandler);
                })
                .flatMap(streamHandler -> {
                    // Create Voice Live streamer factory for SIP audio bridging
                    VoiceLiveStreamerFactory voiceFactory = new VoiceLiveStreamerFactory(streamHandler);
                    LOG.info("✓ Voice Live audio bridge initialized");
                    LOG.info("  - Audio flow: SIP (µ-law 8kHz) ↔ Voice Live (PCM16 24kHz)");
                    LOG.info("  - Automatic codec conversion and resampling");
                    
                    LOG.info("Waiting for incoming SIP calls...");
                    
                    SipProvider sipProvider = new SipProvider(sipConfig, new ConfiguredScheduler(schedulerConfig));
                    VoiceLiveVoipGateway gateway = new VoiceLiveVoipGateway(sipProvider, portConfig.createPool(), 
                                                                             serviceConfig, uaConfig, mediaConfig, voiceFactory);
                    
                    return Mono.never(); // Keep alive
                })
                .doOnError(error -> {
                    LOG.error("❌ Failed to start Voice Live session", error);
                    System.exit(1);
                })
                .block();
            
        } catch (IllegalArgumentException e) {
            LOG.error("Voice Live configuration error: {}", e.getMessage());
            LOG.error("Required environment variables: VOICE_LIVE_ENDPOINT, VOICE_LIVE_API_KEY, VOICE_LIVE_MODEL");
            System.exit(1);
        } catch (Exception e) {
            LOG.error("Failed to initialize Voice Live mode", e);
            System.exit(1);
        }
    }

    /**
     * Checks if a string is configured.
     * @param str The string
     * @return true if the string is not null and not empty, otherwise false
     */
    private static boolean isConfigured(String str) {
        return str != null && !str.isEmpty();
    }

    private static void configureFromEnvironment(Map<String, String> environ, UAConfig uaConfig,
                                                 MediaConfig mediaConfig, PortConfig portConfig,
                                                 SipConfig sipConfig) {
        uaConfig.setRegistrar(new SipURI(environ.get("SIP_SERVER")));
        uaConfig.setSipUser(environ.get("SIP_USER"));
        uaConfig.setAuthUser(environ.get("AUTH_USER"));
        uaConfig.setAuthPasswd(environ.get("AUTH_PASSWORD"));
        uaConfig.setAuthRealm(environ.get("AUTH_REALM"));
        uaConfig.setDisplayName(environ.get("DISPLAY_NAME"));
        if (isConfigured(environ.get("MEDIA_ADDRESS"))) {
            uaConfig.setMediaAddr(environ.get("MEDIA_ADDRESS"));
        }
        uaConfig.setKeepAliveTime(Long.parseLong(environ.getOrDefault("SIP_KEEPALIVE_TIME","60000")));
        uaConfig.setRegister(Boolean.parseBoolean(environ.getOrDefault("REGISTER_WITH_SIP_SERVER","true")));
        uaConfig.setNoPrompt(true);
        mediaConfig.setMediaDescs(createDefaultMediaDescs());
        if (isConfigured(environ.get("MEDIA_PORT_BASE"))) {
            portConfig.setMediaPort(Integer.parseInt(environ.get("MEDIA_PORT_BASE")));
        }
        if (isConfigured(environ.get("MEDIA_PORT_COUNT"))) {
            portConfig.setPortCount(Integer.parseInt(environ.get("MEDIA_PORT_COUNT")));
        }
        sipConfig.setLogAllPackets(environ.getOrDefault("DEBUG_SIP","true").equalsIgnoreCase("true"));
        if (isConfigured(environ.get("SIP_VIA_ADDR"))) {
            sipConfig.setViaAddrIPv4(environ.get("SIP_VIA_ADDR"));
        }
    }

    /**
     * Prints a message to standard output.
     */
    protected static void println(String str) {
        System.out.println(str);
    }

    /**
     * Creates the default media descriptions.
     * @return Media descriptors for PCMU audio
     */
    private static MediaDesc[] createDefaultMediaDescs() {
        return new MediaDesc[]{new MediaDesc("audio",
                0, // Use 0 to let the system assign available ports dynamically
                "RTP/AVP",
                new MediaSpec[]{
                        new MediaSpec(0,
                                "PCMU",
                                8000,
                                1,
                                160)})};
    }
}
