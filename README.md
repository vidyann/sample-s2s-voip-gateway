# Azure Voice Live SIP Gateway Sample

A SIP-to-Voice Live gateway that enables telephone conversations with Azure Voice Live API. Make a phone call and talk to an AI assistant powered by Azure's real-time voice conversation service.

> **Licensing note:** This sample is distributed under GPLv2 because it links against the GPLv2-licensed mjSIP SIP stack. Any redistribution or derivative work must therefore remain GPLv2-compatible. See the [License](#-license) section for details.

[![License: GPLv2](https://img.shields.io/badge/License-GPLv2-blue.svg)](LICENSE)

## 🎯 Overview

This gateway bridges traditional telephony (SIP/VoIP) with Azure Voice Live API, enabling:
- ☎️ **Real-time phone conversations** with AI assistants
- 🎤 **Speech-to-Text** transcription (Azure Speech or Whisper-1)
- 🤖 **LLM processing** with GPT-4.1 for intelligent responses
- 🔊 **Text-to-Speech** synthesis with Azure Neural Voices
- 👋 **Proactive greetings** - AI greets callers automatically
- 🌍 **Multilingual support** - Multiple languages including en-IN, hi-IN, etc.

## 📋 Prerequisites

- **Java 21+** (JDK)
- **Apache Maven** 3.6+
- **Azure Cognitive Services** account with Voice Live API access
- **SIP softphone** for testing (e.g., X-Lite, Zoiper, MicroSIP) OR **SIP server/PBX** for production

## 🚀 Quick Start

### 1. Clone and Build

```bash
git clone https://github.com/vidyann/sample-s2s-voip-gateway.git
cd voicelive-sip-gateway-sample
mvn clean package -DskipTests
```

### 2. Configure Azure Voice Live

Edit `start-gateway.ps1` and update with your Azure credentials:

```powershell
# Required Configuration
$env:VOICE_LIVE_ENDPOINT = "https://your-resource.cognitiveservices.azure.com/"
$env:VOICE_LIVE_API_KEY = "your-api-key-here"
$env:VOICE_LIVE_MODEL = "gpt-4.1"
$env:VOICE_LIVE_VOICE = "en-IN-AartiIndicNeural"
```

### 3. Run the Gateway

```powershell
.\start-gateway.ps1
```

### 4. Make a Test Call

Using your SIP softphone, call: `sip:test@127.0.0.1:5060`

The AI assistant will greet you and start the conversation!

## ⚙️ Configuration

This project supports three configuration methods:

### 1️⃣ PowerShell Script (Recommended for Development)

**File:** `start-gateway.ps1`

**Best for:** Quick development, local testing, Windows environments

```powershell
.\start-gateway.ps1
```

### 2️⃣ Environment Variables (Recommended for Production)

**File:** `environment.template` → copy to `.env`

**Best for:** Docker/containers, CI/CD, Linux/Unix deployments

```bash
cp environment.template .env
# Edit .env with your values
source .env
java -jar target/voicelive-sip-gateway-sample-1.0.0-SNAPSHOT.jar
```

### 3️⃣ mjSIP Configuration (Advanced SIP Tuning)

**File:** `.mjsip-ua.template` → copy to `.mjsip-ua`

**Best for:** Fine-tuning RTP ports, keep-alive timers, codec settings

```bash
cp .mjsip-ua.template .mjsip-ua
# Edit with your SIP settings
```

**Configuration Priority:**
- If `SIP_SERVER` is set → Uses environment variables
- If `SIP_SERVER` is not set → Loads `.mjsip-ua`, then overrides with env vars

## 🔧 Configuration Reference

### Voice Live Settings (Required)

| Variable | Description | Example |
|----------|-------------|---------|
| `VOICE_LIVE_ENDPOINT` | Azure Cognitive Services endpoint | `https://foundrycentin.cognitiveservices.azure.com/` |
| `VOICE_LIVE_API_KEY` | Your Azure API key | `your-api-key-here` |
| `VOICE_LIVE_MODEL` | Model to use | `gpt-4.1` |
| `VOICE_LIVE_VOICE` | Neural voice | `en-IN-AartiIndicNeural` |
| `VOICE_LIVE_INSTRUCTIONS` | System prompt for AI | `You are a helpful assistant...` |
| `VOICE_LIVE_MAX_RESPONSE_OUTPUT_TOKENS` | Response length limit | `200` |
| `VOICE_LIVE_TRANSCRIPTION_MODEL` | Transcription engine | `AZURE_SPEECH` or `WHISPER_1` |
| `VOICE_LIVE_TRANSCRIPTION_LANGUAGE` | Language code | `en-IN`, `hi-IN`, `en-US` |

### Proactive Greeting Settings

| Variable | Description | Default |
|----------|-------------|---------|
| `VOICE_LIVE_PROACTIVE_GREETING_ENABLED` | Enable bot greeting first | `true` |
| `VOICE_LIVE_PROACTIVE_GREETING` | Greeting message | `Hello! How can I help you today?` |

### SIP Settings (Local Testing)

| Variable | Description | Default |
|----------|-------------|---------|
| `SIP_LOCAL_ADDRESS` | SIP listen address | `127.0.0.1` |
| `SIP_VIA_ADDR` | Via header address | `127.0.0.1` |
| `MEDIA_ADDRESS` | RTP media address | `127.0.0.1` |
| `REGISTER_WITH_SIP_SERVER` | Register with SIP server | `false` |

### SIP Settings (Production)

| Variable | Description |
|----------|-------------|
| `SIP_SERVER` | SIP server hostname/IP |
| `SIP_PORT` | SIP server port (default: 5060) |
| `SIP_USER` | SIP username |
| `AUTH_USER` | Authentication username |
| `AUTH_PASSWORD` | Authentication password |
| `AUTH_REALM` | SIP realm |
| `DISPLAY_NAME` | Display name for SIP |

## 🏗️ Architecture

```
┌─────────────┐         ┌──────────────────┐         ┌────────────────┐
│ SIP Phone   │ ◄─RTP──►│ VoIP Gateway     │◄─HTTPS─►│ Azure Voice    │
│ (G.711)     │         │ (This Project)   │         │ Live API       │
│ 8kHz μ-law  │         │ - Transcoding    │         │ - STT          │
└─────────────┘         │ - Audio Bridge   │         │ - LLM (GPT-4.1)│
                        │ - SIP Stack      │         │ - TTS          │
                        └──────────────────┘         └────────────────┘
```

**Key Components:**
- **mjSIP**: SIP/VoIP stack for call handling
- **Audio Transcoders**: Convert between G.711 μ-law (8kHz) and PCM16 (24kHz)
- **Voice Live Client**: Azure SDK integration with SSE event handling
- **Audio Buffering**: 500ms prebuffer for smooth playback

📖 **Detailed Architecture:** See [VOICELIVE-ARCHITECTURE.md](VOICELIVE-ARCHITECTURE.md)

📖 **Deployment Architecture:** See [high-level-arch.md](high-level-arch.md)

## 🌐 Networking

### Firewall Requirements

**Inbound:**
- UDP port `5060` (SIP signaling)
- UDP ports `10000-20000` (RTP media)

**Outbound:**
- HTTPS port `443` (Azure Voice Live API)
- All UDP outbound

### NAT/Firewall Configuration

For deployments behind NAT or firewalls:

```powershell
$env:MEDIA_ADDRESS = "your-public-ip"
$env:SIP_VIA_ADDR = "your-public-ip"
$env:SIP_LOCAL_ADDRESS = "0.0.0.0"
```

## 🔨 Build

### Requirements

- Java 21+
- Maven 3.6+

### Build Commands

```bash
# Clean build
mvn clean package -DskipTests

# Build with tests
mvn clean package

# Build Docker image (optional)
docker build -t voicelive-sip-gateway .
```

### Output

Compiled JAR: `target/voicelive-sip-gateway-sample-1.0.0-SNAPSHOT.jar`

## 🧪 Testing

### Local Testing (No SIP Server)

Perfect for development and testing without any external SIP infrastructure.

**Setup:**
1. Edit `start-gateway.ps1` with your Azure credentials
2. Ensure local testing configuration is enabled (default):
   ```powershell
   $env:SIP_LOCAL_ADDRESS = "127.0.0.1"
   $env:SIP_VIA_ADDR = "127.0.0.1"
   $env:MEDIA_ADDRESS = "127.0.0.1"
   $env:REGISTER_WITH_SIP_SERVER = "false"
   ```

**Testing Steps:**
1. Start the gateway: `.\start-gateway.ps1`
2. Install a SIP softphone (e.g., [X-Lite](https://www.counterpath.com/x-lite/), [Zoiper](https://www.zoiper.com/), [MicroSIP](https://www.microsip.org/))
3. Configure your softphone:
   - **Domain/Server**: `127.0.0.1:5060`
   - **No registration required** (direct connection)
4. Make a call to: `sip:test@127.0.0.1:5060`
5. Listen for the AI greeting and start talking!

**What happens:**
- Softphone connects directly to your gateway (no SIP server needed)
- No authentication required
- All traffic stays on localhost
- Perfect for development and debugging

---

### Production Testing (With SIP Server)

For production deployments with a SIP server/PBX (Asterisk, FreeSWITCH, Microsoft Teams, etc.)

**Setup:**
1. Edit `start-gateway.ps1` and uncomment production SIP settings:
   ```powershell
   # SIP Server Configuration
   $env:SIP_SERVER = "sip.example.com"
   $env:SIP_PORT = "5060"
   $env:SIP_USER = "voicebot@sip.example.com"
   $env:AUTH_USER = "voicebot"
   $env:AUTH_REALM = "sip.example.com"
   $env:AUTH_PASSWORD = "your-password"
   $env:REGISTER_WITH_SIP_SERVER = "true"
   $env:DISPLAY_NAME = "Voice Live Bot"
   ```

2. If behind NAT or firewall, configure network settings:
   ```powershell
   $env:MEDIA_ADDRESS = "your-public-ip"
   $env:SIP_VIA_ADDR = "your-public-ip"
   $env:SIP_LOCAL_ADDRESS = "0.0.0.0"
   ```

**Testing Steps:**
1. Start the gateway: `.\start-gateway.ps1`
2. Verify gateway registers with SIP server (check logs):
   ```
   Registration successful for sip:voicebot@sip.example.com
   ```
3. Configure your SIP server to route calls to the gateway:
   - **Asterisk**: Add dial plan routing to gateway
   - **FreeSWITCH**: Configure dialplan XML
   - **Teams**: Configure Direct Routing
4. Make a test call through your SIP server to the gateway
5. The call should be routed to the Voice Live bot

**Production Architecture:**
```
┌──────────────┐
│ PSTN / Users │ (Phone network)
└──────┬───────┘
       │
┌──────▼───────────────────────┐
│  SBC (Session Border Ctrl)   │ (e.g., Audiocodes, Oracle, Ribbon)
│  - NAT Traversal             │
│  - Security (firewall)       │
│  - Protocol conversion       │
│  - Media anchoring           │
└──────┬───────────────────────┘
       │ SIP Trunk
┌──────▼───────────────────────┐
│  SIP Server / PBX            │ (e.g., Asterisk, FreeSWITCH, Teams)
│  - Call routing              │
│  - User directory            │
│  - Call features             │
└──────┬───────────────────────┘
       │ SIP INVITE: sip:bot@gateway.example.com
┌──────▼───────────────────────┐
│  Voice Live VoIP Gateway     │ (This Project)
│  gateway.example.com:5060    │
│  - Registers with SIP server │
│  - Receives INVITEs          │
└──────┬───────────────────────┘
       │ HTTPS
┌──────▼───────────────────────┐
│  Azure Voice Live API        │
└──────────────────────────────┘
```

**Common Production Scenarios:**

1. **IVR Integration**: Route specific menu options to the bot
   ```
   Press 1 for sales → Human agent
   Press 2 for support → Voice Live Bot
   ```

2. **After-hours Support**: Route calls to bot when agents unavailable

3. **Queue Overflow**: Route calls to bot when wait times exceed threshold

4. **Initial Screening**: Bot qualifies leads before human agent

**Troubleshooting Production:**
- ✅ Verify firewall rules (UDP 5060, 10000-20000)
- ✅ Check NAT configuration if gateway is behind firewall
- ✅ Verify SIP server can reach gateway's IP
- ✅ Test audio in both directions
- ✅ Monitor logs for registration and call flow issues

📖 **Detailed Production Setup:** See [high-level-arch.md](high-level-arch.md) for SBC and network architecture

## 📦 Dependencies

### Core Libraries

- **mjSIP** 2.0.5 - SIP/VoIP stack (GPLv2)
- **Azure AI Voice Live SDK** 1.0.0-beta.1 - Official Azure SDK
- **Reactor Core** 3.6.2 - Reactive programming
- **Gson** 2.10.1 - JSON processing
- **Logback** 1.5.11 - Logging

## 👨‍💻 Developer Guide

### Project Structure

```
src/main/java/com/example/s2s/voipgateway/
├── VoiceLiveVoipGateway.java          # Main entry point
├── voicelive/
│   ├── VoiceLiveClient.java           # Azure Voice Live SDK client
│   ├── VoiceLiveStreamHandler.java    # Session lifecycle management
│   ├── VoiceLiveEventHandler.java     # Event processing interface
│   ├── VoiceLiveConfig.java           # Configuration management
│   ├── VoiceLiveAudioInput.java       # RTP → Voice Live audio
│   ├── VoiceLiveAudioInputStream.java # Audio input stream wrapper
│   ├── VoiceLiveAudioOutput.java      # Voice Live → RTP audio
│   ├── VoiceLiveAudioOutputStream.java# Audio output stream wrapper
│   ├── VoiceLiveStreamerFactory.java  # Component initialization
│   └── transcode/
│       ├── UlawToPcmTranscoder.java   # μ-law → PCM16 (with resampling)
│       └── PcmToULawTranscoder.java   # PCM16 → μ-law (with resampling)
```

### Key Classes

**Entry Point:**
- `VoiceLiveVoipGateway.java` - Main class, SIP user agent configuration

**Voice Live Integration:**
- `VoiceLiveClient.java` - Azure SDK wrapper, session management
- `VoiceLiveStreamHandler.java` - Handles session lifecycle, proactive greetings
- `VoiceLiveStreamerFactory.java` - Creates and wires audio components

**Audio Processing:**
- `VoiceLiveAudioInput.java` - Captures RTP audio from SIP
- `VoiceLiveAudioInputStream.java` - Input stream wrapper for Voice Live SDK
- `VoiceLiveAudioOutput.java` - Delivers Voice Live audio to SIP/RTP
- `VoiceLiveAudioOutputStream.java` - Output stream wrapper for RTP transmission
- Transcoders - Handle 8kHz ↔ 24kHz conversion with μ-law ↔ PCM16

### Extending the Gateway

To customize the AI behavior:

1. **Modify system instructions:**
   ```powershell
   $env:VOICE_LIVE_INSTRUCTIONS = "Your custom prompt here"
   ```

2. **Change voice:**
   ```powershell
   $env:VOICE_LIVE_VOICE = "en-US-AvaMultilingualNeural"
   ```

3. **Add multilingual support:**
   ```powershell
   $env:VOICE_LIVE_TRANSCRIPTION_LANGUAGE = "en-IN,hi-IN"  # Comma-separated
   ```

## 🐛 Troubleshooting

### Common Issues

**Issue:** Cannot connect to Voice Live API
- ✅ Verify `VOICE_LIVE_ENDPOINT` and `VOICE_LIVE_API_KEY`
- ✅ Check firewall allows HTTPS to Azure

**Issue:** No audio from AI
- ✅ Check logs for "Prebuffering..." messages
- ✅ Verify RTP ports 10000-20000 are open
- ✅ Check `MEDIA_ADDRESS` is correct for your network

**Issue:** SIP registration fails
- ✅ Verify SIP server credentials
- ✅ Check `SIP_VIA_ADDR` matches your network
- ✅ Ensure port 5060 UDP is accessible

### Logging

Logs are written to: `logs/voicelive-gateway-YYYYMMDD-HHMMSS.log`

Enable debug logging:
```powershell
$env:DEBUG_SIP = "true"
```

## 📊 Performance

| Metric | Value |
|--------|-------|
| End-to-End Latency | 500-800ms |
| Audio Packet Size | 160 bytes (20ms) |
| Prebuffer Duration | 500ms |
| Proactive Greeting Delay | <100ms |

## 📄 License

This project is licensed under the [GNU General Public License v2.0](LICENSE).

Because the gateway links against the GPLv2-licensed **mjSIP** stack, any redistribution of this project (modified or unmodified) must remain under GPLv2-compatible terms. To comply:

- Provide the full, corresponding source code for every binary distribution.
- Include this repository's `LICENSE` and `NOTICE` files with your distribution.
- Document your modifications (if any) so downstream users understand what changed.
- When embedding the gateway inside a larger product, ensure the combined work is distributed under GPLv2-compatible terms.

The `NOTICE` file summarizes third-party attributions, including the original MIT-0 sample release from bundled dependencies (mjSIP, Azure SDK, Reactor, Gson, Logback, etc.).

## 🤝 Contributing

Contributions welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## 📚 Additional Resources

- [Azure Voice Live Documentation](https://learn.microsoft.com/azure/ai-services/openai/realtime-audio)
- [mjSIP GitHub Repository](https://github.com/haumacher/mjSIP)
- [Architecture Documentation](VOICELIVE-ARCHITECTURE.md)
- [Deployment Guide](high-level-arch.md)

## 🆘 Support

For issues and questions:
- Open an [issue on GitHub](https://github.com/vidyann/sample-s2s-voip-gateway/issues)
- Check existing [discussions](https://github.com/vidyann/sample-s2s-voip-gateway/discussions)

---

**Note:** This is a sample implementation for demonstration purposes. Review security, scalability, and compliance requirements before production deployment.
