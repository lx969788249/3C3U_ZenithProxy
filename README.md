# ZenithProxy

<p align="center">
  <a href="https://discord.gg/nJZrSaRKtb">
  <img alt="Discord" src="https://dcbadge.limes.pink/api/server/nJZrSaRKtb">
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/MC-1.21.4-brightgreen.svg" alt="Minecraft"/>
  <img src="https://img.shields.io/endpoint?url=https%3A%2F%2Ftokei.kojix2.net%2Fbadge%2Fgithub%2Frfresh2%2FZenithProxy%2Flines" alt="Lines of Code"/>
</p>

ZenithProxy is a Minecraft bot that players can also log into and control ingame

You can have accounts always online and securely shared with friends

The bot can be controlled remotely through a Discord bot, a terminal, or ingame

It's designed for use on [2b2t.org](https://www.2b2t.org/) but works on any MC server

This project is also used by the [2b2t.vc API](https://api.2b2t.vc) and [Discord Bot](https://bot.2b2t.vc).

<details>
    <summary>What is a proxy?</summary>

    There are four main components:
    1. A Player's Minecraft Client ("Player Client")
    2. The Proxy's Minecraft Server ("Proxy Server")
    3. The Proxy's Minecraft Client ("Proxy Client")
    4. The destination Minecraft Server ("MC Server")

    Player MC Client <-> Proxy Server <-> Proxy Client <-> MC Server

    Players use a Minecraft client to connect to the Proxy Server just like a normal MC server.
    The Proxy Client connects to a destination MC server (i.e. 2b2t.org).
    The Player's packets to the Proxy Server get forwarded to the Proxy Client which
    forwards them to the destination MC server.

    When no Player Client is connected the Proxy Client will act
    as a bot: moving around, chatting, etc.
</details>

<details>
    <summary>How does it work?</summary>

    ZenithProxy does not use, depend on, or interact with Mojang's Minecraft client or server code.

    Meaning it implements only the needed parts of the Minecraft network protocol and player logic

    So no rendering, lighting engine, or any similar components

    But this means existing MC mods or plugins cannot be used and must be
    reimplemented specifically for ZenithProxy.

    ZenithProxy acts primarily at the network packet layer. It can read/modify/cancel/send
    packets in either direction at any time.

    the client's session and world state is cached and
    sent to players when they connect.

    The cached world state is also used to simulate player movements,
    inventory actions, discord chat relay, and many more features.
</details>

# Setup and Download

https://wiki.2b2t.vc/Setup/

# Features

* High performance and efficiency on minimal hardware, <300MB RAM per java instance or <200MB on linux.
* Integrated ViaVersion
  * Can connect to (almost) any MC server and players can connect with (almost) any MC client
* Secure Whitelist system - share MC accounts without sharing passwords
* Discord Bot for management and notifications
    * Chat relay/bridge
    * Customizable pings and alerts. e.g. Player in visual range alerts
* Spectator mode
  * Multiple players can connect and spectate the player
* Coordinate obfuscation - let players you don't trust visit your base safely
* Baritone pathfinding and movement
* ReplayMod recordings
* 25+ modules including AutoEat, AutoDisconnect, AutoReconnect, AutoRespawn, AutoTotem, KillAura, Spammer, AutoReply
* Java plugins that add more modules created by the community
* Many, many, more features

## 3c3u.org queue support

When the destination is `3c3u.org`, ZenithProxy tracks queue state from server packets only. The personal queue position is read from structured queue HUD packets such as the observed subtitle `555 Playing | Position in queue: 165` (with legacy actionbar text such as `正在排队  位置：172` also supported); the queue total is read only from a tab-list footer such as `142 in queue 3c3u.org`. These are separate values, and malformed or stale values are shown as unknown rather than reused indefinitely.

While 3c3u state is connecting or queued, visual-range enter/leave/logout alerts (including their replay and whisper effects) and Discord relay player connection messages are suppressed. They resume once a non-empty, non-queue footer confirms the main server.

Use `queueStatus` (aliases: `queue`, `q`) to view the `3C3U Queue Status` phase, personal position, queue total, current wait duration, and last packet update. The injected proxy tab-list footer includes the same queue summary while connecting or queued. 3c3u status never uses 2b2t queue APIs, pinging, or ETA estimates.

# Wiki and Documentation

https://wiki.2b2t.vc/

# Development

I highly recommend using [Intellij](https://www.jetbrains.com/idea/) for building and running local development instances.

Gradle will automatically install the required Java version for compiling (currently Java 25)

Most useful gradle tasks:
* `run` - Builds and runs a local dev instance
* `build` - Builds an executable jar to `build/libs/ZenithProxy.jar`
* `nativeCompile` - Builds a GraalVM native image to `build/native/nativeCompile/ZenithProxy` (requires GraalVM JDK)

# Special Thanks

* [odpay](https://github.com/odpay/)
* [DaPorkchop_'s Pork2b2tBot](https://github.com/PorkStudios/Pork2b2tBot)
* [MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib)
* [Baritone](https://github.com/cabaletta/Baritone)
* [Netty](https://github.com/netty/netty/graphs/contributors)
* [GraalVM](https://graalvm.org/)
* [ViaVersion](https://github.com/ViaVersion/ViaVersion)
* [RaphiMC's MinecraftAuth](https://github.com/RaphiMC/MinecraftAuth)
* [JDA](https://github.com/DV8FromTheWorld/JDA)
* [JLine](https://github.com/jline/jline3)
* [Adventure](https://github.com/PaperMC/adventure)
* And many more awesome open source libraries
