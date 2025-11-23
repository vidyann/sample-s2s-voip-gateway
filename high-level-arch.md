

## Current Setup (Local Testing):

```
┌─────────────────┐
│  SIP Softphone  │ (e.g., X-Lite, Zoiper, MicroSIP)
│  192.168.1.4    │
└────────┬────────┘
         │ Direct SIP connection (no server)
         │ sip:test@127.0.0.1:5060
         │
┌────────▼────────────────────┐
│  Voice Live VoIP Gateway    │ (Your Java app)
│  127.0.0.1:5060             │
│  - SIP Stack (mjSIP)        │
│  - RTP Media Handler        │
│  - Audio Transcoding        │
└────────┬────────────────────┘
         │ HTTPS
         │
┌────────▼────────────────────┐
│  Azure Voice Live API       │
│  foundrycentin.cognitive... │
└─────────────────────────────┘
```

**Current Configuration:**
```powershell
$env:REGISTER_WITH_SIP_SERVER = "false"  # No SIP server
$env:SIP_LOCAL_ADDRESS = "127.0.0.1"     # Direct connection
```

## Production Setup (With SIP Server & SBC):

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
│  - Topology hiding           │
└──────┬───────────────────────┘
       │ SIP Trunk
       │
┌──────▼───────────────────────┐
│  SIP Server / PBX            │ (e.g., Asterisk, FreeSWITCH, Teams)
│  - Call routing              │
│  - User directory            │
│  - Call features             │
│  - CDR logging               │
└──────┬───────────────────────┘
       │ SIP INVITE
       │ sip:bot@gateway.example.com
       │
┌──────▼───────────────────────┐
│  Voice Live VoIP Gateway     │ (Your Java app)
│  gateway.example.com:5060    │
│  - Registers with SIP server │
│  - Receives INVITEs          │
└──────┬───────────────────────┘
       │ HTTPS
       │
┌──────▼───────────────────────┐
│  Azure Voice Live API        │
└──────────────────────────────┘
```

**Production Configuration:**
```powershell
# SIP Server Configuration
$env:SIP_SERVER = "sip.example.com"
$env:SIP_PORT = "5060"
$env:SIP_USER = "voicelive-bot@sip.example.com"
$env:AUTH_USER = "voicelive-bot"
$env:AUTH_REALM = "sip.example.com"
$env:AUTH_PASSWORD = "your-password"
$env:REGISTER_WITH_SIP_SERVER = "true"  # Register with server
$env:DISPLAY_NAME = "Voice Live Bot"

# Network Configuration (for SBC/NAT)
$env:MEDIA_ADDRESS = "your-public-ip"    # External IP for RTP
$env:SIP_VIA_ADDR = "your-public-ip"     # External IP for SIP
$env:SIP_LOCAL_ADDRESS = "0.0.0.0"       # Listen on all interfaces
```

## Why You Need SBC + SIP Server:

### **SBC (Session Border Controller):**
- **NAT Traversal**: Handles RTP through firewalls
- **Security**: Protects internal network from SIP attacks
- **Protocol Translation**: Converts between SIP variants
- **Media Anchoring**: Can transcode codecs if needed
- **Topology Hiding**: Hides internal network structure

### **SIP Server/PBX:**
- **Call Routing**: Routes calls to your gateway
- **User Management**: Authenticates users
- **Call Features**: Hold, transfer, conference
- **IVR Integration**: Can route to bot based on menu selection
- **Analytics**: Call logs, metrics, monitoring

## Example Production Flow:

```
1. Customer calls: +1-800-123-4567
   ↓
2. PSTN → SBC (converts to SIP)
   ↓
3. SBC → SIP Server (routes call)
   ↓
4. SIP Server checks routing rules:
   - If IVR menu option 2 → Route to Voice Live bot
   - INVITE sent to sip:voicebot@gateway.example.com
   ↓
5. Voice Live Gateway receives INVITE
   ↓
6. Gateway connects to Azure Voice Live
   ↓
7. Bidirectional RTP audio flows:
   Customer ↔ SBC ↔ SIP Server ↔ Gateway ↔ Voice Live
```

## Your Current Testing:
You're doing **direct SIP connection** without a server:
- Softphone connects directly to your gateway
- No authentication required
- No call routing
- Perfect for development and testing

## To Connect to Production:
1. **Deploy gateway** to a server with public IP
2. **Configure firewall** to allow SIP (5060) and RTP ports
3. **Set SIP credentials** in environment variables
4. **Update SIP server** to route calls to your gateway
5. **Configure SBC** if needed for NAT/security

Would you like help setting up production configuration?

User: Can you save thsi production diagram to md file

GitHub Copilot: Summarized conversation history

User: create an md file with both non production and production diagrams as you have shown 

GitHub Copilot: Summarized conversation history