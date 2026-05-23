# 🦝 OSC Raccoon

**VRChat chatbox music display for Quest standalone — no PC needed.**

Reads whatever is playing on your Quest via Android's media system (no Spotify login required) and sends it to your VRChat chatbox via OSC. Customize the message format and add cycling messages that rotate alongside your now-playing info.

Made by **Recho Raccoon** — [guns.lol/rechoraccoon](https://guns.lol/rechoraccoon)

---

## Features

- 🎵 Reads now-playing from **any music app** (Spotify, YouTube Music, etc.) — no login needed
- ✏️ **Custom message template** using placeholders: `{song}`, `{artist}`, `{duration}`, `{progress}`
- 🔄 **Cycling messages** — add as many as you like, they rotate on a timer you control
- 📤 Sends both in one combined chatbox message every second
- 🗑️ **Clear chatbox** button to instantly wipe your VRChat chatbox
- 🎨 Recho Raccoon branded UI — brown checkerboard, neon green `#00FF07`

---

## Building the APK via GitHub Actions

### Step 1 — Get the Gradle wrapper jar
The `gradle-wrapper.jar` file is a binary that must be present before GitHub Actions can build.

1. Install [Android Studio](https://developer.android.com/studio) or just the [Gradle wrapper](https://gradle.org/releases/)
2. From the project root, run: `gradle wrapper --gradle-version 8.4`
3. This generates `gradle/wrapper/gradle-wrapper.jar` — commit this file

**Or:** Copy `gradle-wrapper.jar` from any existing Android project — it's the same file regardless of project.

### Step 2 — Create a GitHub repo
1. Go to [github.com](https://github.com) → New repository → name it `OSCRaccoon`
2. Make it public or private (your choice)

### Step 3 — Push the code
```bash
cd OSCRaccoon
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/OSCRaccoon.git
git push -u origin main
```

### Step 4 — Download your APK
1. Go to your repo on GitHub → **Actions** tab
2. Click the latest workflow run → scroll to **Artifacts**
3. Download `OSCRaccoon-debug`
4. Inside the zip is `app-debug.apk`

---

## Installing on Quest

### Sideloading via SideQuest
1. Install [SideQuest](https://sidequestvr.com/setup-howto) on your PC
2. Enable Developer Mode on your Quest (Meta account → Devices → Developer Mode)
3. Connect Quest to PC via USB
4. Drag and drop the APK onto SideQuest, or use **Install APK from folder**
5. Find the app in your Quest library under **Unknown Sources**

---

## First-Time Setup in the App

### Grant Notification Access (required once)
When you open the app, a banner will appear if notification access hasn't been granted.
Tap **"Grant Access"** → find **OSC Raccoon** in the list → toggle it on.

This is what lets the app read what's currently playing without needing a Spotify account.

### VRChat OSC Setup (required once in VRChat)
1. In VRChat, open the main menu → **OSC** → enable OSC
2. Make sure OSC is set to receive on port `9000` (this is the default)

That's it — OSC Raccoon sends to `127.0.0.1:9000` (localhost on your Quest).

---

## Placeholders

| Code | Output |
|------|--------|
| `{song}` | Song title |
| `{artist}` | Artist name |
| `{duration}` | `2:14 / 7:47` style timestamp |
| `{progress}` | `▓▓▓▓░░░░░░░░` progress bar |

**Example template:**
```
🎵 {song}
by {artist} | {duration}
{progress}
```

---

## Project Structure

```
OSCRaccoon/
├── .github/workflows/build.yml       ← GitHub Actions auto-build
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── assets/recho_icon.png         ← About screen avatar
│   └── java/com/rechoraccoon/oscraccoon/
│       ├── MainActivity.kt           ← Full UI (Jetpack Compose)
│       ├── MediaListenerService.kt   ← Reads now-playing via Android media sessions
│       ├── OscForegroundService.kt   ← Keeps OSC running in background
│       └── OscSender.kt              ← Builds & sends UDP/OSC packets
└── ...
```
