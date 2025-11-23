# Voice Live SIP Gateway - Class Diagram

This document provides a comprehensive class diagram showing the architecture and relationships between all components of the Voice Live SIP Gateway.

## High-Level Component Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         VoiceLiveVoipGateway                            │
│                      (Main Gateway Application)                          │
│  - Manages SIP registration and call handling                           │
│  - Uses mjSIP library for SIP/RTP protocol handling                     │
└──────────────────────┬──────────────────────────────────────────────────┘
                       │ uses
                       ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      VoiceLiveStreamerFactory                           │
│                   (Creates Audio Stream Bridges)                         │
│  - Implements mjSIP StreamerFactory interface                           │
│  - Creates AudioReceiver (SIP → Voice Live)                             │
│  - Creates AudioTransmitter (Voice Live → SIP)                          │
└──────────────────────┬──────────────────────────────────────────────────┘
                       │ creates
                       ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     Audio Processing Layer                              │
│  ┌──────────────────────────┐    ┌─────────────────────────────┐      │
│  │  VoiceLiveAudioInput     │    │  VoiceLiveAudioOutput       │      │
│  │  (AudioReceiver)         │    │  (AudioTransmitter)         │      │
│  │  SIP RTP → Voice Live    │    │  Voice Live → SIP RTP       │      │
│  └────────┬─────────────────┘    └────────┬────────────────────┘      │
│           │ uses                           │ uses                       │
│           ▼                                ▼                            │
│  ┌──────────────────────────┐    ┌─────────────────────────────┐      │
│  │ VoiceLiveAudioInputStream│    │ VoiceLiveAudioOutputStream  │      │
│  │ (Wraps OutputStream)     │    │ (Wraps InputStream)         │      │
│  └──────────────────────────┘    └─────────────────────────────┘      │
└────────────────────────────────────────────────────────────────────────┘
                       │
                       │ both use
                       ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      VoiceLiveStreamHandler                             │
│                  (Core Audio Streaming Logic)                            │
│  - Manages Voice Live WebSocket session                                 │
│  - Handles bidirectional audio streaming                                │
│  - Transcodes between G.711 μ-law (8kHz) and PCM16 (24kHz)             │
│  - Manages audio buffering and event handling                           │
└──────────────────────┬──────────────────────────────────────────────────┘
                       │ uses
                       ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         VoiceLiveClient                                 │
│                  (Azure SDK Client Wrapper)                              │
│  - Creates VoiceLiveAsyncClient using Azure SDK                         │
│  - Manages session lifecycle                                            │
│  - Handles authentication with API key                                  │
└──────────────────────┬──────────────────────────────────────────────────┘
                       │ configured by
                       ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         VoiceLiveConfig                                 │
│                    (Configuration Manager)                               │
│  - Loads configuration from environment variables                        │
│  - Validates endpoint, API key, model, voice settings                  │
│  - Manages transcription and greeting settings                          │
└─────────────────────────────────────────────────────────────────────────┘
```

## Detailed Class Diagram

```
┌──────────────────────────────────────────────────────────────────────────┐
│                        VoiceLiveVoipGateway                              │
├──────────────────────────────────────────────────────────────────────────┤
│ - mediaConfig: MediaConfig                                               │
│ - uaConfig: UAConfig                                                     │
│ - streamerFactory: StreamerFactory                                       │
│ - _rc: RegistrationClient                                                │
│ - keep_alive: SipKeepAlive                                               │
├──────────────────────────────────────────────────────────────────────────┤
│ + VoiceLiveVoipGateway(sipProvider, portPool, serviceConfig,            │
│                        uaConfig, mediaConfig, voiceLiveFactory)          │
│ + registerWithKeepAlive(): void                                          │
│ + onUaRegistrationSuccess(rc, contact): void                             │
│ + onUaRegistrationFailure(rc, contact, result): void                     │
│ + onUaIncomingCall(call, caller, callee, sdp, msg): void                │
│ + createStreamerFactory(mediaSpecs): StreamerFactory                     │
│ + main(args): void                                                       │
└──────────────────────────────────────────────────────────────────────────┘
                            │
                            │ creates
                            ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                      VoiceLiveStreamerFactory                            │
├──────────────────────────────────────────────────────────────────────────┤
│ - handler: VoiceLiveStreamHandler                                        │
├──────────────────────────────────────────────────────────────────────────┤
│ + VoiceLiveStreamerFactory(handler)                                      │
│ + createMediaStreamer(executor, flowSpec): MediaStreamer                 │
└──────────────────────────────────────────────────────────────────────────┘
                            │
                            │ creates
                            ▼
┌────────────────────────────────────┬─────────────────────────────────────┐
│     VoiceLiveAudioInput            │      VoiceLiveAudioOutput           │
│     (implements AudioReceiver)     │      (implements AudioTransmitter)  │
├────────────────────────────────────┼─────────────────────────────────────┤
│ - handler: VoiceLiveStreamHandler  │ - handler: VoiceLiveStreamHandler   │
│                                    │ - outputStream: VoiceLive...Stream  │
├────────────────────────────────────┼─────────────────────────────────────┤
│ + VoiceLiveAudioInput(handler)     │ + VoiceLiveAudioOutput(handler)     │
│ + createReceiver(...): AudioRxHandle│ + clearBuffer(): void               │
│                                    │ + createSender(...): AudioTXHandle   │
└────────────────────────────────────┴─────────────────────────────────────┘
                            │                           │
                            │ uses                      │ uses
                            ▼                           ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                   VoiceLiveAudioInputStream                              │
│                   (extends OutputStream)                                 │
├─────────────────────────────────────────────────────────────────────────┤
│ - handler: VoiceLiveStreamHandler                                        │
│ - buffer: ByteArrayOutputStream                                          │
│ - BUFFER_SIZE: int = 320 (20ms at 8kHz)                                 │
├─────────────────────────────────────────────────────────────────────────┤
│ + VoiceLiveAudioInputStream(handler)                                     │
│ + write(b: int): void                                                    │
│ + write(b: byte[], off: int, len: int): void                            │
│ + flush(): void                                                          │
│ + close(): void                                                          │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                   VoiceLiveAudioOutputStream                             │
│                   (extends InputStream)                                  │
├─────────────────────────────────────────────────────────────────────────┤
│ - handler: VoiceLiveStreamHandler                                        │
│ - audioQueue: BlockingQueue<byte[]>                                      │
│ - currentChunk: byte[]                                                   │
│ - chunkPosition: int                                                     │
│ - TIMEOUT_MS: long = 1000                                                │
├─────────────────────────────────────────────────────────────────────────┤
│ + VoiceLiveAudioOutputStream(handler)                                    │
│ + read(): int                                                            │
│ + read(b: byte[], off: int, len: int): int                              │
│ + available(): int                                                       │
│ + clearBuffer(): void                                                    │
│ + close(): void                                                          │
└─────────────────────────────────────────────────────────────────────────┘

                                    │
                                    │ both delegate to
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                       VoiceLiveStreamHandler                             │
├──────────────────────────────────────────────────────────────────────────┤
│ - session: VoiceLiveSessionAsyncClient                                   │
│ - voice: String                                                          │
│ - instructions: String                                                   │
│ - transcriptionModel: String                                             │
│ - transcriptionLanguage: String                                          │
│ - maxResponseOutputTokens: Integer                                       │
│ - proactiveGreetingEnabled: boolean                                      │
│ - proactiveGreeting: String                                              │
│ - isResponseDone: boolean (volatile)                                     │
│ - conversationStarted: boolean (volatile)                                │
│ - audioOutput: VoiceLiveAudioOutput                                      │
│ - outputAudioQueue: BlockingQueue<byte[]>                                │
│ - isSessionReady: boolean (volatile)                                     │
│ - isStreamingAudio: boolean (volatile)                                   │
│ - currentResponseText: AtomicReference<String>                           │
│ - sessionReadyFuture: CompletableFuture<Void>                            │
├──────────────────────────────────────────────────────────────────────────┤
│ + VoiceLiveStreamHandler(session, voice, instructions, ...)             │
│ + start(): void                                                          │
│ + sendAudio(ulawData: byte[]): void                                      │
│ + getOutputAudio(): byte[]                                               │
│ + setAudioOutput(output: VoiceLiveAudioOutput): void                     │
│ + clearOutputBuffer(): void                                              │
│ + close(): void                                                          │
│ - connectSession(): Mono<Void>                                           │
│ - handleAudioDelta(event: AudioDelta): void                              │
│ - handleResponseDone(event: ResponseDone): void                          │
│ - handleTranscriptionDone(event: TranscriptionDone): void                │
│ - handleSessionStarted(event: SessionStarted): void                      │
│ - handleError(event: Error): void                                        │
│ - handleResponseTextDelta(event: ResponseTextDelta): void                │
│ - handleConversationItemCreated(event: ConversationItemCreated): void    │
│ - startProactiveGreeting(): Mono<Void>                                   │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ uses
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                          VoiceLiveClient                                 │
├──────────────────────────────────────────────────────────────────────────┤
│ - client: VoiceLiveAsyncClient                                           │
│ - session: VoiceLiveSessionAsyncClient                                   │
│ - config: VoiceLiveConfig                                                │
├──────────────────────────────────────────────────────────────────────────┤
│ + VoiceLiveClient(config)                                                │
│ + startSession(model: String): Mono<VoiceLiveSessionAsyncClient>        │
│ + close(): void                                                          │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ configured by
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                          VoiceLiveConfig                                 │
├──────────────────────────────────────────────────────────────────────────┤
│ - endpoint: String                                                       │
│ - apiKey: String                                                         │
│ - model: String                                                          │
│ - voice: String                                                          │
│ - instructions: String                                                   │
│ - transcriptionModel: String                                             │
│ - transcriptionLanguage: String                                          │
│ - apiVersion: String                                                     │
│ - maxResponseOutputTokens: Integer                                       │
│ - proactiveGreeting: String                                              │
│ - proactiveGreetingEnabled: boolean                                      │
├──────────────────────────────────────────────────────────────────────────┤
│ + VoiceLiveConfig()                                                      │
│ + VoiceLiveConfig(endpoint, apiKey, model, voice, ...)                  │
│ + buildWebSocketUrl(): String                                            │
│ + getEndpoint(): String                                                  │
│ + getApiKey(): String                                                    │
│ + getModel(): String                                                     │
│ + getVoice(): String                                                     │
│ + getInstructions(): String                                              │
│ + getTranscriptionModel(): String                                        │
│ + getTranscriptionLanguage(): String                                     │
│ + getApiVersion(): String                                                │
│ + getMaxResponseOutputTokens(): Integer                                  │
│ + getProactiveGreeting(): String                                         │
│ + isProactiveGreetingEnabled(): boolean                                  │
└──────────────────────────────────────────────────────────────────────────┘
```

## Audio Transcoding Classes

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         AudioResampler                                   │
│                    (Static Utility Class)                                │
├──────────────────────────────────────────────────────────────────────────┤
│ + upsample8to24(pcm8k: byte[]): byte[]                                   │
│   • Converts 8kHz PCM16 to 24kHz PCM16 (3x interpolation)               │
│                                                                          │
│ + downsample24to8(pcm24k: byte[]): byte[]                                │
│   • Converts 24kHz PCM16 to 8kHz PCM16 (1/3 decimation)                 │
│                                                                          │
│ - writeSample(buffer: byte[], sampleIdx: int, value: short): void       │
│ - readSample(buffer: byte[], sampleIdx: int): short                     │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│                      UlawToPcmTranscoder                                 │
│                    (Static Utility Class)                                │
├──────────────────────────────────────────────────────────────────────────┤
│ - ULAW_TO_LINEAR_TABLE: short[256]                                       │
├──────────────────────────────────────────────────────────────────────────┤
│ + decode(ulawData: byte[]): byte[]                                       │
│   • Converts μ-law 8-bit (8kHz) to PCM16 (8kHz)                         │
│                                                                          │
│ - ulawToLinear(ulawByte: byte): short                                    │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│                      PcmToULawTranscoder                                 │
│                    (Static Utility Class)                                │
├──────────────────────────────────────────────────────────────────────────┤
│ - ULAW_COMPRESS_TABLE: byte[256]                                         │
│ - BIAS: int = 0x84                                                       │
├──────────────────────────────────────────────────────────────────────────┤
│ + encode(pcmData: byte[]): byte[]                                        │
│   • Converts PCM16 (8kHz) to μ-law 8-bit (8kHz)                         │
│                                                                          │
│ - linearToUlaw(pcmSample: short): byte                                   │
└──────────────────────────────────────────────────────────────────────────┘
```

## Audio Processing Pipeline

### Input Direction (SIP → Voice Live)
```
SIP Caller
    │
    │ G.711 μ-law (8kHz, 8-bit)
    ▼
[mjSIP RTP Receiver]
    │
    ▼
VoiceLiveAudioInput (AudioReceiver)
    │
    ▼
VoiceLiveAudioInputStream (OutputStream wrapper)
    │
    ▼
VoiceLiveStreamHandler.sendAudio()
    │
    ├─► UlawToPcmTranscoder.decode() → PCM16 (8kHz, 16-bit)
    │
    ├─► AudioResampler.upsample8to24() → PCM16 (24kHz, 16-bit)
    │
    ▼
Azure Voice Live API (WebSocket)
```

### Output Direction (Voice Live → SIP)
```
Azure Voice Live API (WebSocket)
    │
    │ PCM16 (24kHz, 16-bit)
    ▼
VoiceLiveStreamHandler.handleAudioDelta()
    │
    ├─► AudioResampler.downsample24to8() → PCM16 (8kHz, 16-bit)
    │
    ├─► PcmToULawTranscoder.encode() → G.711 μ-law (8kHz, 8-bit)
    │
    ▼
outputAudioQueue (BlockingQueue)
    │
    ▼
VoiceLiveStreamHandler.getOutputAudio()
    │
    ▼
VoiceLiveAudioOutputStream (InputStream wrapper)
    │
    ▼
VoiceLiveAudioOutput (AudioTransmitter)
    │
    ▼
[mjSIP RTP Sender]
    │
    │ G.711 μ-law (8kHz, 8-bit)
    ▼
SIP Caller
```

## Key Design Patterns

### 1. Factory Pattern
- **VoiceLiveStreamerFactory**: Creates MediaStreamer instances that bridge SIP and Voice Live
- Implements mjSIP's `StreamerFactory` interface
- Encapsulates creation of AudioReceiver and AudioTransmitter

### 2. Adapter Pattern
- **VoiceLiveAudioInput**: Adapts Voice Live streaming to mjSIP's `AudioReceiver` interface
- **VoiceLiveAudioOutput**: Adapts Voice Live streaming to mjSIP's `AudioTransmitter` interface
- **VoiceLiveAudioInputStream**: Adapts mjSIP's OutputStream to Voice Live's streaming API
- **VoiceLiveAudioOutputStream**: Adapts Voice Live's audio queue to mjSIP's InputStream

### 3. Strategy Pattern
- **Transcoding**: Pluggable audio format conversion strategies
  - `UlawToPcmTranscoder`: μ-law → PCM16
  - `PcmToULawTranscoder`: PCM16 → μ-law
  - `AudioResampler`: 8kHz ↔ 24kHz conversion

### 4. Facade Pattern
- **VoiceLiveClient**: Simplifies interaction with Azure Voice Live SDK
- **VoiceLiveStreamHandler**: Provides a unified interface for bidirectional audio streaming

### 5. Configuration Pattern
- **VoiceLiveConfig**: Centralized configuration management with environment variable loading and validation

## Dependencies

### External Libraries
```
┌─────────────────────────────────────────────────────────────────────────┐
│                         External Dependencies                            │
├─────────────────────────────────────────────────────────────────────────┤
│ • mjSIP 2.0.5 (org.mjsip)                                               │
│   - SIP/RTP protocol handling                                           │
│   - Media streaming interfaces                                          │
│                                                                         │
│ • Azure Voice Live SDK 1.0.0-beta.1 (com.azure.ai)                     │
│   - VoiceLiveAsyncClient                                                │
│   - VoiceLiveSessionAsyncClient                                         │
│   - Event models (AudioDelta, ResponseDone, etc.)                       │
│                                                                         │
│ • Project Reactor 3.6.2 (reactor-core)                                  │
│   - Mono<T> for reactive operations                                     │
│   - Publisher/Subscriber patterns                                       │
│                                                                         │
│ • SLF4J 2.0.16 + Logback 1.5.11                                         │
│   - Logging abstraction and implementation                              │
│                                                                         │
│ • Gson 2.10.1 (com.google.code.gson)                                    │
│   - JSON processing                                                     │
└─────────────────────────────────────────────────────────────────────────┘
```

## Class Relationships Summary

| Relationship | From | To | Type |
|-------------|------|-----|------|
| Uses | VoiceLiveVoipGateway | VoiceLiveStreamerFactory | Composition |
| Creates | VoiceLiveStreamerFactory | VoiceLiveAudioInput | Factory |
| Creates | VoiceLiveStreamerFactory | VoiceLiveAudioOutput | Factory |
| Uses | VoiceLiveAudioInput | VoiceLiveAudioInputStream | Composition |
| Uses | VoiceLiveAudioOutput | VoiceLiveAudioOutputStream | Composition |
| Uses | VoiceLiveAudioInputStream | VoiceLiveStreamHandler | Association |
| Uses | VoiceLiveAudioOutputStream | VoiceLiveStreamHandler | Association |
| Uses | VoiceLiveStreamHandler | VoiceLiveSessionAsyncClient | Composition |
| Uses | VoiceLiveStreamHandler | UlawToPcmTranscoder | Dependency |
| Uses | VoiceLiveStreamHandler | PcmToULawTranscoder | Dependency |
| Uses | VoiceLiveStreamHandler | AudioResampler | Dependency |
| Creates | VoiceLiveClient | VoiceLiveAsyncClient | Factory |
| Creates | VoiceLiveClient | VoiceLiveSessionAsyncClient | Factory |
| Configures | VoiceLiveConfig | VoiceLiveClient | Configuration |
| Implements | VoiceLiveAudioInput | AudioReceiver (mjSIP) | Interface |
| Implements | VoiceLiveAudioOutput | AudioTransmitter (mjSIP) | Interface |
| Extends | VoiceLiveAudioInputStream | OutputStream | Inheritance |
| Extends | VoiceLiveAudioOutputStream | InputStream | Inheritance |
| Extends | VoiceLiveVoipGateway | RegisteringMultipleUAS (mjSIP) | Inheritance |

## Thread Safety Notes

### Volatile Fields (VoiceLiveStreamHandler)
- `isResponseDone`: Thread-safe flag for response completion
- `conversationStarted`: Thread-safe flag for conversation state
- `isSessionReady`: Thread-safe flag for session readiness
- `isStreamingAudio`: Thread-safe flag for audio streaming state

### Thread-Safe Collections
- `outputAudioQueue`: BlockingQueue for producer-consumer audio buffering
- `currentResponseText`: AtomicReference for thread-safe text accumulation

### Concurrency Patterns
- **Reactive Streams**: Uses Project Reactor's Mono for asynchronous operations
- **CompletableFuture**: Used for session readiness signaling
- **BlockingQueue**: Implements producer-consumer pattern for audio buffering

## Audio Format Conversions

| Stage | Input Format | Output Format | Component |
|-------|-------------|---------------|-----------|
| SIP Reception | G.711 μ-law 8kHz 8-bit | PCM16 8kHz 16-bit | UlawToPcmTranscoder |
| Upsampling | PCM16 8kHz 16-bit | PCM16 24kHz 16-bit | AudioResampler |
| Voice Live Input | PCM16 24kHz 16-bit | - | Azure SDK |
| Voice Live Output | - | PCM16 24kHz 16-bit | Azure SDK |
| Downsampling | PCM16 24kHz 16-bit | PCM16 8kHz 16-bit | AudioResampler |
| SIP Transmission | PCM16 8kHz 16-bit | G.711 μ-law 8kHz 8-bit | PcmToULawTranscoder |

---

**Generated**: November 23, 2025  
**Project**: Voice Live SIP Gateway  
**Version**: 1.0.0-SNAPSHOT
