
-----

# 🎬 YPV (Yasso Video Player)

**YPV** is a minimalist, high-performance Android media player built with **Jetpack Compose** and **Media3 (ExoPlayer)**. Designed with **FOSS** (Free and Open Source Software) principles at its core, YPV aims to provide a clean, ad-free, and privacy-focused alternative for video playback on Android.

-----

## ✨ Features (Beta 1.1)

  * **Modern and Simple Compose UI:** Built entirely with a declarative UI and Material Design 3.
  * **Local & Remote Playback:** Supports opening local files via a system picker or streaming directly from a URL.
  * **Advanced Track Management:** \* **Multi-Audio Support:** Easily switch between different language tracks in MKV/MP4 containers.
      * **Subtitle Selection:** Toggle and choose embedded subtitles on the fly.
  * **Performance Toggling:** Integrated settings for Hardware Acceleration and High-Performance Buffer modes.
  * **Adaptive Orientation:** Automatic landscape mode for an immersive cinematic experience.

-----

## 🛠 Current Challenges (Help Wanted\! 🆘)

YPV is currently in active development. While it performs perfectly on modern devices, it struggles with specific formats on mid-range devices.

**We are looking for contributors to help with:**

### 1\. Implementing FFmpeg Decoders 🧩

  * **Current State:** The app currently relies on **Standard Google Media3 Decoders**.
  * **Goal:** Integrate `media3-decoder-ffmpeg` with pre-compiled native binaries (`.so` files) to support advanced codecs and fix the MKV stuttering issue.

### 2\. UI/Controller Syncing 🎮

  * **Problem:** On some videos like MKV, the Seek Bar and playback controls occasionally appear disabled (greyed out) despite the video playing correctly.
  * **Goal:** Refine the `AndroidView` update cycle and state-to-UI binding in Compose.

### 3\. Optimization for Low-End Hardware 📱

  * **Goal:** Implement smarter "Fallback" mechanisms and more efficient memory/buffer management for devices with limited CPU/RAM.

-----

## 🚀 Getting Started

1.  **Clone the Repo:**
    ```bash
    git clone https://github.com/yassocoderstrikesback/YPVMediaPlayer.git
    ```
2.  **Open in Android Studio:** Use the latest version (Ladybug or newer).
3.  **Build & Run:** Deploy to a physical device to test real-world performance.

-----

## 🤝 Contributing

I am a secondary school student and a self-taught developer. I built YPV to learn and to provide a useful tool to the community.

**How you can help:**

  * Submit a **Pull Request** with FFmpeg implementation or UI fixes.
  * Open an **Issue** to report bugs or suggest new features.
  * Share your knowledge on optimizing Media3 for older Android kernels.

-----

## 📜 Vision

> "To create a media player that belongs to the user—no ads, no tracking, just pure performance."

**Developed with ❤️ by Yassien Elhariry (Yassogame67)**

-----

