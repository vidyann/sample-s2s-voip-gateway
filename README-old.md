# Azure Voice Live S2S VoIP Gateway

This project contains an implementation of a SIP endpoint that acts as a gateway to Azure Voice Live API.
In other words, you can call a phone number and talk to Azure Voice Live API .

<!-- TOC -->
* [How does this work?](#how-does-this-work-)

* [Third Party Dependencies of Note](#third-party-dependencies-of-note)
* [Environment Variables](#environment-variables)
* [Networking](#networking)
* [Build](#build)
* [Maven settings.xml](#maven-settingsxml)
* [Developer Guide](#developer-guide)
* [License](#license)
<!-- TOC -->

Requirements:
* A SIP account on a SIP server.  There are a number of options for this, whether it be a public VoIP provider or an account on your own PBX.

You should have some knowledge of Voice over IP (VoIP) and SIP.  

Please be aware that this is just a proof of concept and shouldn't be considered production ready code.

## How does this work?  

This application acts as a SIP user agent.  When it starts it registers with a SIP server.  Upon receiving a call it will answer, establish the media session (over RTP), start a session with Voice live, and bridge audio between RTP and VoiceLive.  Audio received via RTP is sent to Voice live and audio received from Voice Live is sent to the caller via RTP.


Additional Requirements:
* Your workstation should have Docker installed and Docker should be running.  This is required to build the Docker image.  See https://docs.docker.com/get-started/get-docker/.


```

## Third Party Dependencies of Note

This project utilizes a fork of the mjSIP project, which can be found at https://github.com/haumacher/mjSIP, which is licensed under GPLv2.

## Configuration

This project supports three configuration methods, each serving different use cases:

### 1. **start-gateway1.ps1** (Recommended for Development)
PowerShell script that sets environment variables and launches the gateway. Best for:
- Quick development and testing
- Local testing with hardcoded values
- Windows environments

Usage:
```powershell
.\start-gateway1.ps1
```

### 2. **environment.template** (Recommended for Production)
Template for environment variables. Best for:
- Production deployments
- Docker/container environments
- CI/CD pipelines
- Linux/Unix environments

Usage:
```bash
# Copy and customize
cp environment.template .env
# Edit .env with your values
# Source it before running
source .env
java -jar target/voicelive-sip-gateway-sample-1.0.0-SNAPSHOT.jar
```

### 3. **.mjsip-ua.template** (Advanced SIP Configuration)
Configuration file for low-level SIP/RTP settings. Best for:
- Fine-tuning RTP port ranges
- Customizing keep-alive timers
- Configuring symmetric RTP
- Adjusting audio codecs/formats

Usage:
```bash
# Copy to .mjsip-ua in project root or home directory
cp .mjsip-ua.template .mjsip-ua
# Edit with your SIP settings
# The application will auto-load this file in local mode
```

**Configuration Priority**: 
- If `SIP_SERVER` environment variable is set → Uses environment variables (modes 1 or 2)
- If `SIP_SERVER` is not set → Loads `.mjsip-ua` file, then overrides with any environment variables

## Environment Variables

Below is a list of the environment variables in use:

### Voice Live Configuration (Required)
* VOICE_LIVE_ENDPOINT - Azure Cognitive Services endpoint (e.g., https://your-resource.cognitiveservices.azure.com/)
* VOICE_LIVE_API_KEY - Your Azure API key
* VOICE_LIVE_MODEL - Model to use (e.g., gpt-4.1)
* VOICE_LIVE_VOICE - Neural voice to use (e.g., en-IN-AartiIndicNeural)
* VOICE_LIVE_INSTRUCTIONS - System instructions for the AI assistant
* VOICE_LIVE_MAX_RESPONSE_OUTPUT_TOKENS - Maximum response length (default: 200)
* VOICE_LIVE_TRANSCRIPTION_MODEL - AZURE_SPEECH or WHISPER_1
* VOICE_LIVE_TRANSCRIPTION_LANGUAGE - Language code (e.g., en-IN, en-US, hi-IN)

### Proactive Greeting Configuration
* VOICE_LIVE_PROACTIVE_GREETING_ENABLED - true|false to enable bot greeting first
* VOICE_LIVE_PROACTIVE_GREETING - The greeting message (e.g., "Hello! How can I help you today?")

### SIP Configuration
* AUTH_USER - username for authentication with SIP server
* AUTH_PASSWORD - password for authentication with SIP server
* AUTH_REALM - the SIP realm to use for authentication
* DEBUG_SIP - true|false to enable/disable logging SIP packets
* DISPLAY_NAME - the display name to send for your SIP address
* GREETING_FILENAME - the name of the wav file to play as a greeting.  Can be an absolute path or in the classpath.
* MEDIA_ADDRESS - the IP address to use for RTP media traffic.  By default it will source the address from your network interfaces.
* MEDIA_PORT_BASE - the first RTP port to use for audio traffic
* MEDIA_PORT_COUNT - the size of the RTP port pool used for audio traffic
* SIP_KEEPALIVE_TIME - frequency in milliseconds to send keep-alive packets
* SIP_SERVER - the hostname or IP address of the SIP server to register with.  Required if running in environment variable mode.
* SIP_USER - equivalent of sip-user from `.mjsip-ua`, generally the same as AUTH_USER
* SIP_VIA_ADDR - the address to send in SIP packets for the Via field.  By default it will source the address from your network interfaces.

If SIP_SERVER is set the application will pull configuration from environment variables.  If it is not set it will use the `.mjsip-ua` file.

## Networking

mjSIP doesn't contain any uPNP, ICE, or STUN capabilities, so it's necessary that your instance be configured with the proper security groups to allow VoIP traffic.

Inbound rules:
* Permit inbound UDP traffic on port 5060 (SIP port)
* Permit inbound UDP traffic on port range 10000-20000 (RTP ephemeral ports).  The range can be set via the MEDIA_PORT_BASE and MEDIA_PORT_COUNT environment variables.  Set this to an appropriate value for your configuration.

Outbound rules:
* Permit all outbound.

If you want to override addresses that are used with SIP traffic you can do so by running in environment variable mode.  See Environment Variables for more information on how to do this and what can be configured.

## Build


Additionally, Apache Maven is required to do the build.  This can be downloaded from https://maven.apache.org/ or installed on Amazon Linux using the command `sudo yum install maven`.  Unzip Maven in a place where you'll be able to find it again. See "Maven settings.xml" below for details about configuring Maven.

To build the project, open a terminal and cd to the project directory.  Run "mvn package" (you may need to put the full /path/to/maven/bin/mvn if the bin directory is not in your system PATH).

Maven will build the project and create an s2s-voip-gateway*.jar file in the target/ directory.

## Maven settings.xml

mjSIP is distributed from a GitHub Maven repository.  Unfortunately, GitHub Maven repositories require credentials.  You will need to set up a classic API token with GitHub (https://github.com/settings/tokens), if you haven't already, and configure that in your ~/.m2/settings.xml file:

```
<?xml version="1.0" encoding="UTF-8"?>

<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>github</id>
            <username>YOUR_USERNAME</username>
            <password>YOUR_AUTH_TOKEN</password>
        </server>
    </servers>
</settings>
```

## Developer Guide

The entrypoint for the application is VoiceLiveVoipGateway.java. This class contains a main method and configures the user agent based on what it finds in environment variables.

The main entry point for the VoiceLive integration is in VoiceLiveStreamerFactory.java, where the client is instantiated and the audio streams are established.

The tool set is instantiated in NovaStreamerFactory.createMediaStreamer().  If you create new tools you'll need to update the NovaS2SEventHandler to instantiate your new class.



## License

MIT-0 License.  See the LICENSE file for more details.
