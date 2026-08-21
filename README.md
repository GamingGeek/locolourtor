# Locolourtor

**Ugly locator bar colour? Not anymore!**

Locolourtor is a client-side mod that lets you customise the colour of your marker on Minecraft's locator bar.
Your chosen colour syncs in real-time to any other players with Locolourtor installed, no need for any server-side mods.

---

## ✨ Features

- **Custom Colours:** Set any colour using Hex (`#0074d5`), RGB (`0–255`), or CMYK (`0–100%`).
- **Realtime Sync:** Instant syncing using a simple websocket connection to receive updates
- **100% Client-Side:** Works wherever you play whether it be LAN/Essential, Realms or a server, no need for extra
configuration.
- **Secure & Private:** Authentication using your profile keys, minimal data storage (only your UUID & colour)
- **Multi-Loader Support:** Available on both **Fabric** and **NeoForge** from 1.21.11 up to 26.2

---

## 🎮 Commands

Aliases: `/locolour`, `/locolor`

| Command | Description                                                                                  |
|---|----------------------------------------------------------------------------------------------|
| `/locolourtor set <#hex>` | Sets your locator bar colour using a 6-character hex code (e.g. `/locolourtor set #0074d5`). |
| `/locolourtor set rgb <r> <g> <b>` | Sets your colour using RGB values between 0 and 255 (e.g. `/locolourtor set rgb 0 116 213`). |
| `/locolourtor set cmyk <c> <m> <y> <k>` | Sets your colour using CMYK percentages (e.g. `/locolourtor set cmyk 100 45 0 16`).          |
| `/locolourtor reset` | Resets your locator bar colour back to Minecraft's vanilla default (UUID-derived).           |
| `/locolourtor refresh [player]` | Refreshes locator colours for all players or a specific player in your world.                |
| `/locolourtor status` | Shows your current locator bar colour in chat.                                               |

---

## 📦 Supported Versions

Below are the currently supported versions for the mod. As new versions release, it should be updated within a day

| Minecraft Version | Fabric | NeoForge |
|---|---|---|
| **1.21.11** | ✅ | ✅ |
| **26.1** | ✅ | ✅ |
| **26.1.1** | ✅ | ✅ |
| **26.1.2** | ✅ | ✅ |
| **26.2** | ✅ | ✅ |

---

## 🛠️ Building from Source

Prerequisites: JDK 21+

Clone the repository and build all version jars:
```bash
./gradlew build
```
Production remapped jars will be created in each `versions/<version>-<loader>/build/libs/` directory.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
