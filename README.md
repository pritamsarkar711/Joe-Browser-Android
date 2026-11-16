# Joe Browser — Android

The Android build of **Joe Browser**, the anti-detect browser. Every profile is an
independent identity: its own fingerprint, its own storage, its own cookies, its own route
to the internet and its own set of built-in tools.

Ported from the Windows desktop build (C# / WinForms) to native Android (Kotlin / Jetpack
Compose), keeping the same engine, the same data tables and the same design language.

## Download

**[⬇ Joe Browser 1.17.0 — GitHub Release](https://github.com/pritamsarkar711/Joe-Browser-Android/releases/tag/v1.17.0)**

| | |
|---|---|
| `JoeBrowser-1.17.0-release.apk` | the installable app |
| `JoeBrowser-1.17.0-release.aab` | the app bundle for Google Play |

```bash
adb install -r JoeBrowser-1.17.0-release.apk
```

---

## What it does

### Profiles

Each profile is one machine that never existed. It carries:

| | |
|---|---|
| **Operating system** | Windows 11, Windows 10, macOS, Linux, Android, iPhone, iPad |
| **Browser engine** | Chrome, Chromium, Brave, Edge, Firefox |
| **Screen** | a real viewport with a real pixel ratio, paired with the handset that ships it |
| **Hardware** | CPU cores, memory, a WebGL adapter from a real table of adapters |
| **Locale** | time zone, language and `Accept-Language`, matched to the exit address |
| **User agent** | generated, or written by hand |
| **Privacy** | canvas noise, audio noise, font noise, WebRTC, geolocation, battery, DNT |

New profiles are rolled until they share nothing meaningful with the ones already in the
workspace: the fingerprint is compared on twelve surfaces, each weighted by how much it
actually identifies a person.

### The browser

The engine is the system WebView, driven to look like the browser the profile names.

* Every profile gets its own WebView profile, so cookies, cache, local storage, IndexedDB
  and service workers belong to that profile alone.
* The page sees the profile's identity: `navigator`, `screen`, WebGL, fonts, WebRTC, the
  time zone and the language.
* The server sees the same identity: the user agent, the client hints and the
  `Accept-Language` header are the profile's, not the device's.
* The requests are made by the app, so the engine never adds the header that names the
  package it lives in.

It is a browser, not a page viewer:

| | |
|---|---|
| **Tabs** | several at once, each its own page with the profile's identity, switched without a reload |
| **Address bar** | tap to type, tap to go, search when it is not an address |
| **Reader mode** | the article on its own, with the interface taken away |
| **Desktop site** | the wide layout, for the pages that need it |
| **Find on page** | as you read, not in a menu |
| **Bookmarks** | saved from the toolbar, listed in a sheet |
| **Downloads** | listed, and kept out of the device's gallery |
| **Share** | the link, to anything on the phone |
| **Identity check** | what the page sees against what the profile says |

### The engines

Every engine is built into the app. Nothing is downloaded, nothing is installed and nothing
is handed over to another program.

| Engine | What it reports |
|---|---|
| **Joe Browser** | the app's own engine, on Chromium |
| **Chrome** | Chrome |
| **Chromium** | Chromium, with the two brand list a real Chromium sends |
| **Brave** | Brave |
| **Edge** | Edge |
| **Firefox** | Firefox, with no client hints, because Firefox has none |

That is the whole point of an anti-detect browser. A real Chrome on the device would be a
different program with its own idea of what the machine is, and the profile would be
describing a machine that browser had never seen.

### The identity check

The app can check its own work. The identity check reads everything the browser will tell
a page, compares every value with what the profile says, and shows the ones that
disagree — each one marked **Agree**, **Disagree** or **Not checked**.

Nothing is claimed that has not been compared, and the page links out to the checks
themselves so the answer can be seen from the outside as well.

### Proxies

* A proxy can be pasted in any shape: `host:port`, `host:port:user:pass`,
  `socks5://user:pass@host:1080`, bracketed IPv6, or a whole list, one per line.
* The proxy is checked through a local relay that keeps the credentials, so a proxy that
  needs a user name and a password works from the browser as well.
* A check never falls back to the device's own connection. A test that cannot fail is not
  a test.
* The exit address, the country, the city and the time zone are read back and used to line
  the profile's clock and language up with the exit node.

### The proxy guard

* Polls the public address of every profile that has a proxy.
* Reports the proxy going offline, the proxy coming back, and the address changing — which
  is a rotation.
* Two consecutive failures are needed before an outage is reported. One failed request is
  usually a hiccup.
* Runs as a foreground service, so it keeps watching while the app is in the background.

### Built-in tools

Both are part of the app, not extensions. Nothing has to be installed for them to work.

* **Paste as human** types the clipboard one character at a time, with the pauses a person
  actually takes, and dispatches the real keyboard events a form listens for. Four speeds.
* **Restore the right click** brings back the context menu, copying and selection on pages
  that block them.

### Extensions

The Chrome Web Store opens inside the app. The extension's package is fetched the way the
store itself hands it out, unpacked, and its content scripts and stylesheets are run in the
pages of every profile that asks for them.

### The rendering engine

The engine that renders the pages is the system WebView, and its version is the number a
profile has to report: a page can find out which Chromium it is talking to by asking
whether the engine understands a feature from a particular release.

---

## Design

The desktop build's rules were kept:

* Depth comes from a four step neutral ramp, not from shadows.
* One accent colour, a single blue that changes with the mode.
* The base text is 16 px and nothing anywhere is smaller than 14 px.
* Rounded, flat surfaces: radius 10 for controls, 12 for cards.
* Light and dark are one step apart, never a dissolve.

The colour tokens are in `ui/theme/Theme.kt`, taken from the desktop palette.

---

## Building

### Requirements

* JDK 17
* Android SDK with platform 35 and build-tools 35.0.0
* Gradle 8.11 (the wrapper is included)

### From the command line

```bash
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # signed release APK
./gradlew bundleRelease          # signed app bundle
```

`local.properties` must point at the SDK:

```properties
sdk.dir=/path/to/Android/Sdk
```

### Outputs

| File | |
|---|---|
| `app/build/outputs/apk/release/app-release.apk` | release APK, signed |
| `app/build/outputs/bundle/release/app-release.aab` | app bundle, signed |
| `release/` | copies of both, ready to upload |

### Signing

The release build is signed with the keystore in `keystore/joe-release.jks`.

```
alias     joebrowser
password joebrowser2026
```

**Replace it before you publish anything.** A keystore that is in a public repository is a
keystore that anyone can sign with. Generate your own and put it in the same place:

```bash
keytool -genkeypair -v -keystore keystore/joe-release.jks \
  -storepass <password> -keypass <password> -alias joebrowser \
  -keyalg RSA -keysize 2048 -validity 10950
```

The passwords live in `app/build.gradle.kts`. For a real release, move them to
`local.properties` or to the environment and read them from there.

### Application id and version

```
applicationId com.joebrowser.app
versionCode   11600
versionName   1.16.0
minSdk        24
targetSdk     35
```

---

## Installing

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

or copy the APK to the phone and open it. The app asks for the notification permission so
that the proxy guard can warn you; everything else works without it.

---

## Publishing to Google Play

1. Create the app in the Play Console with the id `com.joebrowser.app`.
2. Upload `app/build/outputs/bundle/release/app-release.aab` to the internal testing track.
3. Fill in the store listing, the privacy policy and the data safety form.

**The data safety form.** The app declares internet access and nothing else. It does not
collect personal data, it does not share anything with the developer, and the data it
keeps — profiles, proxies, history — never leaves the device. The proxy passwords are
encrypted with a key in the Android keystore and are never exported.

**Permissions, and why each one is there.**

| Permission | Why |
|---|---|
| `INTERNET` | the pages |
| `ACCESS_NETWORK_STATE` | telling an offline proxy from a broken one |
| `POST_NOTIFICATIONS` | the proxy guard's warnings |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | keeping the proxy guard alive |
| `REQUEST_INSTALL_PACKAGES` | handing a downloaded browser package to the system installer |
| `VIBRATE` | the notification |
| `USE_BIOMETRIC` | reserved for a future profile lock |

**Things to know before you publish.**

* `REQUEST_INSTALL_PACKAGES` needs an explanation in the console. It is used only for the
  browser packages the user explicitly asks to download, and the system installer asks for
  confirmation every time.
* Cleartext traffic is refused everywhere. A profile that reports an encrypted connection
  must not open a plain one.
* The release keystore in this repository is a placeholder. Replace it.

---

## Testing

See [docs/TESTING.md](docs/TESTING.md) for the full report: what was tested, on what, what
the results were, and what the limits of the platform are.

---

## Layout

```
app/src/main/
  assets/runtime/        the scripts that are injected into every page
    page.js              the fingerprint runtime
    worker.js            the same, for workers
    tools.js             Paste as human, the proxy guard, the right-click guard
  java/com/joebrowser/app/
    browser/             the engine: the WebView, the runtime builder, the relay, the engines
    core/                paths, the keystore-backed secret box
    data/                the models and the stores
    extensions/          the Chrome Web Store, the package format, the runtime
    fingerprint/         the tables and the generation engine
    proxy/               the parser, the relay, the tester, the monitor
    ui/                  the Compose screens and the theme
```

---

## Licence

See [LICENSE](LICENSE).
