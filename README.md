# Custom Totem Manager (Fabric 1.21.11)

[![Fabric Mod](https://img.shields.io/badge/Minecraft-1.21.11-brightgreen.svg)](https://fabricmc.net/)
[![Fabric Loader](https://img.shields.io/badge/Fabric%20Loader-%3E%3D0.16.9-blue.svg)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://adoptium.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![AI Assisted](https://img.shields.io/badge/AI--Assisted-Google%20DeepMind%20Antigravity-purple.svg)](#-ai-disclosure--development-transparency)

> **Custom Totem Manager** is a feature-rich client-side Minecraft Fabric mod that empowers players to customize, preview, and hot-swap Totem of Undying textures, animations, glowing emissive effects, inventory backgrounds, and sounds dynamically in-game without restarting Minecraft!

---

## ✨ Features

- 🔄 **Hot-Swap In-Game (No Game Restart Required)**: Switch between totem textures and configurations on the fly via an intuitive in-game GUI (Default hotkey: `K`).
- 🎞️ **Animated & Emissive Totems**:
  - Full support for multi-frame animated totems (strip format).
  - Unshaded / Emissive Fullbright mode: make your totem glow brightly even in pitch-black caves and the Nether.
  - Custom display transforms: adjust 2D/3D scale, position offsets, and rotation in GUI, First-Person, and Third-Person hands.
- 🌐 **In-Game URL Downloader**: Download community or online totems directly in-game by pasting any direct image link.
- 📱 **Built-in Mobile / Web Gallery Uploader**:
  - Features an embedded local HTTP server so you can upload totem images directly from your phone or browser onto your PC game.
- 📁 **In-Game Folder Browser**: Browse and pick image folders directly inside the Minecraft screen.
- 🎒 **Custom Inventory GUI Background**:
  - Personalize your Survival / Creative inventory GUI background with any image.
  - Realistic in-game preview, image cropping, and adjustable slot opacity slider.
- 🎆 **Totem Pop Visual & Audio Customization**:
  - Adjust totem pop brightness, particle effects, and screen overlay presentation.
- 🛠️ **Totem Converter Tool Included**:
  - Includes `TotemConverter.exe` (built in Rust) in the `tools/` folder to quickly convert GIFs or video files into animated totem image strips.

---

## 📥 Installation

1. Make sure you have **Minecraft 1.21.11** installed.
2. Install **Fabric Loader** (version `0.16.9` or higher).
3. Download and install **Fabric API** for Minecraft 1.21.11 into your `.minecraft/mods` directory.
4. Download the latest `customtotem-x.x.x.jar` from [Releases](https://github.com/LHB16/CustomTotem/releases) or [Modrinth](https://modrinth.com/) and place it in your `.minecraft/mods` folder.
5. Launch Minecraft!

---

## 🎮 How to Use

1. Press **`K`** (or open via Options > Controls to rebind) to open the **Custom Totem Manager** menu.
2. Choose from:
   - **Totem List**: Click to activate any totem instantly.
   - **Download via URL**: Paste a direct link to download an image directly into your totem library.
   - **Browse Folders**: Select custom directories on your PC.
   - **Inventory GUI Settings**: Change and preview custom inventory backgrounds and adjust slot opacity.
   - **Display & Emissive Settings**: Tune totem scale, rotation, emissive brightness, and rendering mode.

---

## 🤖 AI Disclosure & Development Transparency

In accordance with open-source transparency standards and community guidelines (including Modrinth's Generative AI transparency policies):

- **Development Process**: This mod was conceived, designed, coded, and iterated with AI pair-programming assistance utilizing **Google DeepMind Antigravity** (Gemini models) in collaboration with **BinhLH**.
- **Human Oversight**: Architecture, functional specifications, feature validation, Minecraft version compatibility tests, and project maintenance are actively directed and verified by the maintainer.

---

## 🛠️ Building From Source

To build the mod `.jar` file yourself:

```bash
git clone https://github.com/LHB16/CustomTotem.git
cd CustomTotem
./gradlew build
```

The compiled mod file will be located at `build/libs/customtotem-<version>.jar`.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE). Feel free to use, modify, and distribute it in your modpacks!
