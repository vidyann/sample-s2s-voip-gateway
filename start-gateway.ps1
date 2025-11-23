# Start Azure Speech Voice Live Gateway
# Set your configuration here or use environment variables

# Voice Live Configuration (Required)
$env:VOICE_LIVE_ENDPOINT = "https://<resource>.cognitiveservices.azure.com/"
$env:VOICE_LIVE_API_KEY = ""
$env:VOICE_LIVE_MODEL = "gpt-4.1"
$env:VOICE_LIVE_VOICE = "en-IN-AartiIndicNeural"
$env:VOICE_LIVE_INSTRUCTIONS = "You are a helpful AI voice assistant. Keep responses  brief and concise. Answer in 1-2 sentences maximum. Start the Proactive greeting in English langusge only. Dont repeat the greeting during the conversation"
$env:VOICE_LIVE_MAX_RESPONSE_OUTPUT_TOKENS = "200"  # Limits response length (default: 200, ~1-2 sentences)
$env:VOICE_LIVE_TRANSCRIPTION_MODEL = "AZURE_SPEECH"  # Options: AZURE_SPEECH, WHISPER_1
$env:VOICE_LIVE_TRANSCRIPTION_LANGUAGE = "en-IN"  # Language for Azure Speech (e.g., en-US, es-ES, hi-IN, zh-CN)

# Proactive Greeting Configuration
$env:VOICE_LIVE_PROACTIVE_GREETING_ENABLED = "true"  # Set to "false" to wait for user to speak first
$env:VOICE_LIVE_PROACTIVE_GREETING = "Hello! I am an AI  assistant from Mandayam Finance. How can I help you today?"  # Bot's first message when call connects

# SIP Configuration for Local Testing (default)
$env:SIP_LOCAL_ADDRESS = "127.0.0.1"
$env:SIP_VIA_ADDR = "127.0.0.1"
$env:MEDIA_ADDRESS = "127.0.0.1"
$env:REGISTER_WITH_SIP_SERVER = "false"

# SIP Server Configuration (uncomment for production SIP server)
# $env:SIP_SERVER = "your-sip-server.com"
# $env:SIP_PORT = "5060"
# $env:SIP_USER = "your-username@your-sip-server.com"
# $env:AUTH_USER = "your-auth-username"
# $env:AUTH_REALM = "your-sip-server.com"
# $env:AUTH_PASSWORD = "your-sip-password"
# $env:REGISTER_WITH_SIP_SERVER = "true"
# $env:DISPLAY_NAME = "Voice Live Gateway"

# Network Configuration (uncomment if gateway is behind NAT or on different network)
# $env:MEDIA_ADDRESS = "your-gateway-ip"
# $env:SIP_VIA_ADDR = "your-gateway-ip"
# $env:SIP_LOCAL_ADDRESS = "0.0.0.0"

# Configure per-run log file
$logDir = Join-Path $PSScriptRoot "logs"
if (-not (Test-Path $logDir)) {
	New-Item -ItemType Directory -Path $logDir | Out-Null
}
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logFile = Join-Path $logDir "voicelive-gateway-$timestamp.log"
$env:VOICE_LIVE_LOG_FILE = $logFile
Write-Host "Logging to $logFile" -ForegroundColor Yellow

$javaExe = 'C:\Program Files\Microsoft\jdk-21.0.3.9-hotspot\bin\java.exe'
$javaArgs = @("-DvoiceLiveLogFile=$logFile", '-jar', 'target\voicelive-voip-gateway-1.0.0-SNAPSHOT.jar')

Write-Host "Starting Voice Live Gateway..." -ForegroundColor Green
& $javaExe @javaArgs
