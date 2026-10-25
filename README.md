<<<<<<< HEAD
# SentinelAC

SentinelAC is an early-stage security plugin foundation for Bukkit-compatible Minecraft Java servers. It builds against the Spigot 1.8.8 API and emits Java 8 bytecode. This is not a claim of verified compatibility with every Paper/Purpur release, client protocol, proxy, or ViaVersion combination.

## Build and install

Build with Java 8+ and Maven:

```text
mvn clean package
```

Copy `target/sentinel-ac-1.0.0.jar` into the server's `plugins` directory and restart. The shaded JAR includes SQLite JDBC. The plugin creates `plugins/SentinelAC/evidence.db` by default.

Grant administrators `sentinel.admin`. Commands: `/sentinel status`, `/sentinel reload`, `/sentinel ban <player> <reason>`, and `/sentinel unban <player>`. The ban command is an explicit staff override; automated permanent bans are not implemented.

## Current scope

Implemented: plugin lifecycle, configurable connection admission limits, per-IP/global login-burst limits, bounded/expiring pending-login tracking, basic login-name validation, bounded asynchronous SQLite evidence/action persistence, status/reload, and logged staff ban/unban overrides.

Anti-cheat, proxy/VPN detection, packet-level handshake inspection, trusted-proxy parsing, and automated enforcement are not implemented. Their configuration entries are disabled placeholders. Do not enable a check expecting detection. A Bukkit plugin cannot mitigate volumetric DDoS traffic upstream of the Minecraft server; use network/provider-level protection as well.

For accurate per-player IP limits, configure server forwarding only behind trusted proxies and restrict direct backend access at the network layer. This release does not parse proxy forwarding protocols itself.
=======
.-.
>>>>>>> origin/main
