# DiscordSRV ReForged

A port of [DiscordSRV](https://github.com/DiscordSRV/DiscordSRV), the Discord ↔ Minecraft chat bridge plugin for
Spigot/Paper, to **NeoForge 1.21.1**.

DiscordSRV ReForged is a **server-side** mod: install it on a dedicated server only. Clients don't need it.

## Features

Most of DiscordSRV's features work the same as on Spigot and use the same configuration files:

- Minecraft chat ↔ Discord channel bridging, either as bot messages or through webhooks (with player avatars)
- Join, leave, first join, death and advancement messages (embeds configurable in `messages.yml`)
- Server start/stop messages, channel topic updater and channel name updater (`ChannelUpdater`)
- Console channel: the server log streamed to a Discord channel, and commands sent there run as the console
- `!c <command>` console commands and the `playerlist` command from the chat channel, canned responses
- Account linking (`/discord link`, linking codes sent to the bot in a DM or the link channel), stored in a file or MySQL
- Group ↔ role synchronization via **LuckPerms** (also LuckPerms contexts such as `discordsrv:linked`)
- Nickname synchronization, ban synchronization (both directions)
- "Require linked account to play" (also with required Discord server membership / subscriber roles)
- Bot presence/status cycling, server watchdog messages
- Alerts (`alerts.yml`) triggered by game events, DiscordSRV API events and Discord (JDA) events
- Proximity voice module (experimental, as on Spigot)
- The DiscordSRV API (`github.scarsz.discordsrv.api`) for other mods: `DiscordSRV.api.subscribe(listener)`

## Installation

1. Install [NeoForge](https://neoforged.net) 21.1.x for Minecraft 1.21.1 on your server.
2. Put `DiscordSRV-ReForged-<version>.jar` into the server's `mods` folder (use the jar **without** the `-slim`
   suffix; it contains the required libraries).
3. Start the server once. The configuration is created in `config/discordsrv/`.
4. Create a Discord bot (https://discord.com/developers/applications), enable the **Server Members Intent** and the
   **Message Content Intent**, invite it to your Discord server and put its token into `config/discordsrv/config.yml`
   (`BotToken`). Alternatively the token can be provided with the `DISCORDSRV_TOKEN` environment variable/JVM property
   or a `config/discordsrv/.token` file.
5. Set the channel ids (`Channels`, `DiscordConsoleChannelId`) and restart the server.

Optional: install [LuckPerms](https://luckperms.net) for permissions, primary groups in chat formats and group sync.

## Commands & permissions

`/discord` (alias `/discordsrv`): `help`, `link`, `unlink`, `linked`, `broadcast`, `reload`, `resync`, `language`,
`debug`, `debugger`.

Permissions are registered with NeoForge's permission API (so LuckPerms and other permission mods can manage them).
Without a permission mod, the defaults of the Spigot version apply: player permissions (`discordsrv.chat`,
`discordsrv.link`, `discordsrv.linked`, `discordsrv.discord`, `discordsrv.help`, `discordsrv.nicknamesync`) are granted
to everyone, admin permissions (`discordsrv.reload`, `discordsrv.bcast`, `discordsrv.unlink`, `discordsrv.*.others`,
`discordsrv.resync`, `discordsrv.debug`, ...) to server operators. `discordsrv.silentjoin` / `discordsrv.silentquit`
and the group sync permissions `discordsrv.sync.<group>` / `discordsrv.sync.deny.<group>` are off by default.

## Differences from the Spigot version

- **Plugin hooks** don't exist on NeoForge: chat channel plugins (only the `global` channel is bridged in game),
  vanish plugins, Vault (replaced by LuckPerms), PlaceholderAPI, Multiverse (`%worldalias%` equals `%world%`, which is
  the dimension name like `overworld`), Dynmap, mcMMO, Skript and Essentials.
- **Placeholders**: instead of PlaceholderAPI, a few built-in placeholders can be used wherever the Spigot version
  accepted PlaceholderAPI placeholders: `%player_name%`, `%player_displayname%`, `%player_uuid%`, `%player_world%`,
  `%player_ping%`, `%luckperms_primary_group_name%`, `%server_online%`, `%server_max_players%`, `%server_tps%`,
  `%server_version%`, `%server_motd%`, `%server_unique_joins%`.
- **Require linked account to play** runs right after the vanilla ban/whitelist checks; the `Listener priority` and
  `Listener event` options are no longer used.
- **Minecraft → Discord ban synchronization** detects bans by watching the server's ban list (every few seconds),
  so it also works for bans made by other mods.
- **`/discord debug`** writes the debug report to `config/discordsrv/debug/` instead of uploading it.
- The update checker, bStats metrics and the `ForceTLSv12`/fallback DNS options were removed.
- Alerts trigger on these game events (instead of Bukkit events): `PlayerJoinEvent`, `PlayerQuitEvent`,
  `PlayerChatEvent` (also matches `AsyncPlayerChatEvent`), `PlayerDeathEvent`, `PlayerAdvancementDoneEvent`,
  `PlayerCommandEvent`, `ServerCommandEvent`.
- Discord was updated from JDA 4 to JDA 6. Discord usernames no longer have discriminators.

## Building

```
./gradlew build
```

The mod jar is `build/libs/DiscordSRV-ReForged-<version>.jar`. Requires Java 21. `./gradlew runServer` starts a
development server.

### Project layout

- `github.scarsz.discordsrv` – DiscordSRV itself. This code is platform independent: it talks to the game only
  through the interfaces in `github.scarsz.discordsrv.platform` (`Platform`, `GamePlayer`, `CommandSender` and the game
  events in `platform.event`).
- `github.scarsz.discordsrv.neoforge` – the NeoForge implementation: mod entrypoint, event forwarding, the `/discord`
  Brigadier command, permission nodes, text component conversion and a mixin for the login check.

Libraries (JDA, Adventure, MCDiscordReserializer, SnakeYAML, ...) are shaded and relocated into the mod jar.

## License

DiscordSRV ReForged is licensed under the GNU General Public License v3.0 (or later), like DiscordSRV.
DiscordSRV is © Austin "Scarsz" Shapiro and contributors. This port is not affiliated with or endorsed by the
DiscordSRV team, please don't ask them for support with it.
