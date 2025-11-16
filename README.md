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

## Environment Variables

This project can be configured to run via the `.mjsip-ua` configuration file OR by setting environment variables.  Below is a list of the environment variables in use:

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

The entrypoint for the application is NovaSonicVoipGateway.java.  This class contains a main method and configures the user agent based on what it finds in environment variables.

The main entry point for the Nova integration is in NovaStreamerFactory.java, where the Bedrock client is instantiated and the audio streams are established.

By default, the gateway includes a toolset that gives Nova Sonic the ability to retrieve the date and time, but this can be extended to do much more.  The example tools can be found in com.example.s2s.voipgateway.nova.tools.

New tools can be developed by extending the AbstractNovaS2SEventHandler class and implementing the functionality you desire.  See the javadoc in AbstractNovaS2SEventHandler for more information.  An easy starting point for new tools would be to copy the DateTimeNovaS2SEventHandler to a new file, replacing the tools with something relevant to your use case.

The tool set is instantiated in NovaStreamerFactory.createMediaStreamer().  If you create new tools you'll need to update the NovaS2SEventHandler to instantiate your new class.



## License

MIT-0 License.  See the LICENSE file for more details.
