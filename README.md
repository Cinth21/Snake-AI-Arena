# Snake AI Arena 🐍

**A simple Android game for an Intelligent Systems assignment.**

**Modes**

1. **Play vs AI (offline):** Easy, Medium, Hard. AI uses breadth-first search (BFS), safe-move rules, and (on Hard) reachable-area / opponent proximity evaluation.
2. **Hotspot LAN Multiplayer (offline):** 2–4 Android players. One **Android phone is the host**, generates a six-digit **room code**, and uses the phone's hotspot or the same Wi-Fi. Other phones join with the room code. **No MacBook or internet host is needed to play.**

## Start here (Bisaya, easy steps)

**Important:** This ZIP contains actual Android **source code**, not a finished APK yet. Dili kinahanglan mag-install og Android Studio sa MacBook. Ang **GitHub Actions** maoy mo-build sa APK online. Kinahanglan lang og browser, GitHub account, ug internet **during build/upload**. Human ma-install ang APK, offline na ang AI ug hotspot LAN gameplay.

### A. Build APK using GitHub (no Android Studio on MacBook)

1. Download and **extract** `Snake_AI_Arena_Android_Source.zip`.
2. Open [GitHub](https://github.com/new) sa Chrome/Safari, log in, and make a **Public** repository named `Snake-AI-Arena` (default branch `main`). Check **Add a README file** so the repository has an **Add file** button.
3. On the repository page choose **Add file → Upload files**. **Drag the contents of the extracted `Snake_AI_Arena_Android` folder** into the upload panel, including `app/`, `.github/`, `build.gradle`, `settings.gradle` and `gradle.properties`. In Finder press **Command + Shift + .** if `.github` is hidden. Commit files to `main`. GitHub supports folder drag-and-drop uploads.
4. Go to your repository **Actions** tab. Open **Build Android APK - no Android Studio on Mac**, then choose the latest run. The workflow runs automatically on a push to `main`, or select **Run workflow** if needed. Allow GitHub Actions for the repo if prompted.
5. When the workflow succeeds, open the **Artifacts** section, download `Snake-AI-Arena-Android`, and unzip it to get `Snake-AI-Arena.apk`. GitHub Actions artifact downloads require signing into GitHub, so **do not share the artifact URL with classmates as a public APK link**.
6. To create a **shareable APK link for Messenger**, open GitHub **Releases → Draft a new release**, create a tag such as `v1.0.0` based on `main`, and publish. The **tag push** causes the included workflow to build again and attach `Snake-AI-Arena.apk` to that GitHub Release. Check that the APK is attached after the new build finishes. If there is a workflow error, you can manually download the APK from Actions and upload it to the Release's assets.
7. Copy the **link to the APK asset** on the public GitHub Release and send that link in Messenger. Classmates open the link on their Android phones, download the APK, and install it (Android may ask permission to install apps from that browser).

**Example URL format (NOT a live link):**
`https://github.com/YOUR_USERNAME/Snake-AI-Arena/releases/download/v1.0.0/Snake-AI-Arena.apk`

### B. Edit code with VS Code on MacBook

Open the extracted folder in VS Code. Main files:

- `app/src/main/java/com/student/snakearena/GameEngine.java` — Snake movement, collision, food, BFS AI.
- `app/src/main/java/com/student/snakearena/MainActivity.java` — Android screen, touch/swipe controls, main menu, lobby.
- `app/src/main/java/com/student/snakearena/LanNetwork.java` — Local TCP gameplay server + UDP room-code discovery.
- `app/src/main/java/com/student/snakearena/GameCodec.java` — Game state serialization.
- `.github/workflows/build-apk.yml` — Automated GitHub build and public GitHub Release APK.

When you've edited a file, upload/commit the changes to GitHub; GitHub Actions will build an updated APK. Running a Java source file directly in VS Code does **not** produce an Android APK or public download link; the GitHub cloud build handles those steps.

### C. Play against AI

1. Install and open the APK on an Android phone.
2. Select **Easy, Medium or Hard**.
3. Tap **PLAY VS AI**.
4. **Swipe on the arena** or tap UP / DOWN / LEFT / RIGHT.
5. Eat red fruit; avoid hitting a wall or another snake's body.

### D. Play on hotspot LAN (no internet)

1. On **Phone A**, turn ON its regular Wi-Fi hotspot in Android Settings.
2. Connect Phones B, C, and D to Phone A's hotspot. No mobile data required after installation.
3. On Phone A, open the APK and tap **CREATE LAN ROOM**. A **six-digit room code** appears; note the **Host IP** also displayed.
4. On each joining phone, open the same APK, tap **JOIN LAN ROOM**, and enter the room code. Optional: enter the host's IP address if automatic discovery fails.
5. The host sees all joined players and taps **START MATCH** when at least two players are present.
6. Every phone controls its own snake; the **host phone** is the authoritative game server. On the host, **RESTART** makes a new round with the same players.

**LAN constraints / troubleshooting**

- This uses **standard local TCP/UDP sockets** over Wi-Fi and hotspot, *not* public cloud hosting. Works only where phones can reach each other on the same network. Certain Android hotspot models, routers with client isolation, carrier restrictions, or VPN/firewall configurations can block discovery or peer connections.
- If the room code cannot find Phone A automatically, enter the **Host IP** shown on Phone A's lobby. This avoids the need for UDP broadcast discovery but still requires local connectivity.
- Keep the hosting phone unlocked and keep the app on screen during multiplayer matches; Android may pause a background app.
- This prototype has no automatic hotspot creation. **Enable the hotspot manually** in Android Settings, then create the room in the game.
- Android local-network permission rules vary with target SDK and OS version. This prototype targets SDK 35 and includes `INTERNET` permission for LAN sockets; future Android targets may require additional runtime permissions.
- Only Android APKs are supported; iPhones cannot install this APK. Debug APK signing is intended for **classroom testing**, not production distribution or Play Store publication.
- Only install APKs you built or trust; a sideload prompt is normal for an APK shared outside Play Store.

## Development/testing status

- **Completed source implementation:** arena graphics, touchscreen controls, two modes, room code and lobby, host-authoritative LAN updates, food/collision rules, three AI difficulty levels, 2–4 player slot handling, GitHub Actions build/release workflow.
- **Verified in this preparation:** the pure Java `GameEngine` compiles and completes **100 simulated match smoke tests**; Java source passed a syntax/type-check with temporary Android API stubs.
- **Not yet verified:** a genuine Android SDK/Gradle APK build, actual installation on phones, and actual hotspot multiplayer on 2–4 phones. The environment that created this project has no Android SDK or internet access to build. The cloud workflow and device tests are still required.

## Built with

- Native Android **Java** (`minSdk 24`, `targetSdk 35`)
- Android Gradle Plugin **8.7.3**, Gradle **8.9**, JDK **17**, Android SDK **35** (downloaded in GitHub Actions)
- Java sockets (`ServerSocket`, `Socket`, `DatagramSocket`) for LAN communication
- Android Canvas drawing — **no third-party app dependency** or external backend

See `ASSIGNMENT_NOTES.md` for the Intelligent Systems explanation and evaluation checklist.
