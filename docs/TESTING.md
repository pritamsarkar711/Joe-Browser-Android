# Testing report

This is what was actually run, what it produced, and where the platform puts a ceiling on
what can be done.

## The device

| | |
|---|---|
| Emulator | Android 16 (API 37), x86_64, 16 KB page size system image |
| Screen | 1080 × 2400, density 420 |
| Engine | Chromium WebView 149.0.7827.5 |
| Builds tested | `app-debug.apk` and the signed `app-release.apk` |

The WebView version matters: it is what the profile reports, and the version a page can
find out about by asking whether the engine understands a feature from a particular
release.

## What was done

| Area | Result |
|---|---|
| Install and start, debug and release | Both install and start. |
| Profile creation, editing, saving, deleting, duplicating | Working. |
| Profiles survive a restart | Working — two bugs found and fixed here, see below. |
| The four tabs of the editor | Working. |
| The five screens | Working, light and dark. |
| The browser: tabs, address bar, find, reader mode, desktop site, bookmarks, downloads, share | Working. |
| The runtime on a live page | Applied; verified property by property. |
| Paste as human | Types into a real field, character by character. |
| First launch opens browserscan.net | Working. |
| The identity check | Working: 100% agreement on the profile that was tested. |
| Fingerprint tests | See below. |
| Proxy parsing | See below. |
| Proxy check | Working against the live services. |
| Rotation and offline alerts | Working; the events fire and notify. |
| Extensions | The store opens in the app; the package format is handled. |
| Engine detection | Working; the WebView and the installed browsers are reported with their versions. |
| Rotation, background and foreground | Working. |

## Fingerprint results

```
IPFighter            100%    Fingerprint masking: Not detected
                            Automation / bot:    No automation
BrowserScan          100%    Chrome 149.0.7827.5, Android 12.0.0
                            Bot detection: No
```

The browser scan was 90% in 1.16.0 and is 100% in 1.17.0. Two things changed it:

* the platform a phone reports is `Linux armv81`, which is what a 64 bit Android browser
  actually says and what `armv8l` never did;
* the language the engine defaults to now follows the profile, so `Intl` and
  `navigator.language` no longer disagree.

### The release build

The signed release APK was installed and put through the same flow: a fresh install, a
profile created from the empty state, and the profile opened for the first time on
`browserscan.net`.

```
IPFighter score          100%
Fingerprint masking      Not detected
Automation / bot         No automation
User-Agent               Mozilla/5.0 (Linux; Android 16; Pixel 9 Pro XL) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36
Platform                 Linux armv81
Time zone                Asia/Dhaka
Accept-Language          bn-BD,bn;q=0.9,en;q=0.8
Sec-Ch-Ua                "Not_A Brand";v="8", "Chromium";v="149", "Google Chrome";v="149"
X-Requested-With         (absent)
```

The release build has no inspector attached by default, which is the right default: a page
can find an open inspector. Turning on **Settings → Developer → Web inspector** makes the
engine inspectable, and the numbers above were read that way.

### The identity check

The app can check its own work. The identity check reads everything the browser will tell
a page, compares every value with what the profile says, and marks each one **Agree**,
**Disagree** or **Not checked**. On the profile that was tested it reported **100% —
nothing disagrees with the profile** across the user agent, the platform, the language,
the languages, the time zone, the clock offset, the screen, the available screen, the
scale, the colour depth, the cores, the memory, the touch points, the WebGL adapter, the
WebGL vendor, the WebGL version, the automation flag, the canvas stability and the
automation leftovers.

### The headers the server received

```
Accept-Language:    bn-BD,bn;q=0.9,en;q=0.8
Sec-Ch-Ua:          "Not_A Brand";v="8", "Chromium";v="149", "Google Chrome";v="149"
Sec-Ch-Ua-Mobile:   ?1
Sec-Ch-Ua-Platform: "Android"
User-Agent:         Mozilla/5.0 (Linux; Android 15; SM-A155F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36
X-Requested-With:   (absent)
```

### What IPHey reports, and why it is not a hundred

IPHey's score was 40 out of 100, with four signals reading *"Detected an inconsistent
browser fingerprint"*. It named the browser **Mobile Chrome** correctly, the country
**Bangladesh** correctly, and hardware and software **Everything is fine**.

Two things are going on, and neither is a contradiction inside the profile:

* IPHey compares the browser against a table of fingerprints collected from real people.
  A profile that describes a handset nobody in that table has is "unreliable" however well
  it is put together. The fix was to weight the device table towards the handsets that
  are actually met on the street, which is what 1.17.0 does.
* Its audio test timed out. The emulator has no audio device, so the test that measures
  the audio buffer never finished. That is an emulator, not a phone.

The score is therefore a statement about how common the identity is, not about whether it
is self-consistent. The identity check is the honest measure of the second.

### The sites that were run

| Site | Result |
|---|---|
| `ipfighter.com/browser-fingerprint` | **100%**, masking not detected |
| `browserscan.net` | **100%**, no bot detection |
| `pixelscan.net` | loads, no verdict shown without a sign-in |
| `iphey.com` | 40/100, see above |
| `amiunique.org` | loads, the entropy table is behind an interaction |
| `browserleaks.com` | the leak checks are per check, not a single score |

**No score is claimed for a site that did not show one.**

## The proxy subsystem

* Every accepted form of the address is parsed: `host:port`, `host:port:user:pass`,
  `http://`, `https://`, `socks5://`, `socks5h://`, `user:pass@host:port`, a bracketed
  IPv6 address, a full-width colon, and a list of all of them.
* A check reads the exit address, the country, the city and the time zone, and writes them
  onto the proxy so the profile's clock can be matched to the exit node.
* A check that cannot reach the service fails. It never falls back to the device's own
  connection, because a test that silently succeeds over the device's internet reports the
  device's address and calls a dead proxy working.
* The relay speaks HTTP `CONNECT` with credentials and SOCKS5 with credentials to the
  upstream, and presents the browser with a plain local endpoint that needs neither.
* The rotation guard fires when the address changes, and the offline guard fires after two
  failures in a row.

The proxy could not be exercised against a live authenticated proxy during this run, so
the SOCKS5 and credential paths were verified against the local relay and its handshake,
not against a third-party server.

## Bugs that were found and fixed

These were all found by using the app, not by reading it.

| | |
|---|---|
| **A new profile was never saved.** It had no identifier yet, so it was written to a file called `.json` and then skipped on the next read. The identifier is now given the moment the editor opens. |
| **Saving a profile that already existed deleted it.** The write deleted the old file before moving the new one into place, and a reader arriving in that gap found nothing. The rename now replaces the file in one step, and the browser waits and reads again if it misses. |
| **The fingerprint runtime never ran.** A typo in the time-zone wrapper made the whole script a syntax error, and a script that will not parse fails silently: the page kept the device's identity and nothing said so. The runtime now reports its own errors, and the error is written to the document where it can be read. |
| **`navigator.platform` was not spoofed.** The same typo stopped the script before the platform was patched. |
| **The engine's own headers were leaking.** Every request carried `X-Requested-With: com.joebrowser.app`, and the `Accept-Language` was the device's, not the profile's. The engine offers no way to turn the first one off, so the app now makes the requests itself and sends the profile's headers. |
| **A request with a body still carried the engine's language.** The app cannot read the body of a request, so it cannot make one itself. The page now sets the language on every request it makes, whichever kind it is. |
| **A response arrived with its media type twice.** The engine adds the media type of the response object and passes the header map through as well, so the page saw `application/json, application/json; charset=utf-8` and could not read the body. The framing headers are now carried once. |
| **The proxy was reported as offline when it was working.** The same duplicate-header problem made the checker's answer unreadable. |
| **The fingerprint score was 70 out of 100.** The profile claimed Chrome 131 while the engine was Chromium 149, and a page can find that out by probing for a feature from a given release. The profile now reports the build that is really running, and the score is 100. |
| **The browser scan was 90.** The platform said `armv8l` and the engine's default language said `en-US` while the page said `bn-BD`. Both are now right. |
| **`window.chrome.runtime.sendMessage` was carrying a `prototype`.** A native function has none, and the difference is one property away from being found. Every function the runtime installs is now prototype-less and prints as native code. |
| **The WebGL version did not match the renderer.** The renderer said Mali-G76 and the version said "OpenGL ES 2.0 Chromium". Both are now derived from the same adapter. |
| **The time zone was the device's.** Only `Intl` was patched, so `new Date().getTimezoneOffset()` and `new Date().toString()` still gave the device's clock away. Both are patched now, and the offset is right to the minute. |
| **The engine's language and the page's language were two languages.** `Intl` answered with the device's language while `navigator.language` answered with the profile's. Both follow the profile now. |
| **There was no speech synthesis at all.** The engine on a phone has none, and a page that asked for it got an error instead of an answer. The engine is given the surface, so a page that asks does not fall over. |
| **The language did not match the time zone.** The device was in Dhaka and the profile said `en-US`. The time zone to language table now covers the zones a phone can actually be in. |
| **The theme did not change until it was saved.** The theme is the one setting a person expects to see change straight away. It applies now. |
| **The debug and release builds could not be installed together.** Both used the same file provider authority. The authority follows the application id now. |

## The limits of the platform

These are not bugs in the app. They are what the engine does, and they are stated rather
than hidden.

* **The engine is the system WebView.** A desktop browser can be launched as its own
  process with its own command line; a phone cannot. The engine that renders the pages is
  the WebView, and everything the profile claims is layered on top of it.
* **A request with a body keeps the engine's headers.** The app cannot read the body of a
  request, so it cannot make one itself, and a request the engine makes keeps the engine's
  own `X-Requested-With`. Every request the page makes carries the profile's language
  either way, because the page sets it.
* **A Chrome extension cannot be installed into the WebView.** There is no extension host
  to hand it to. What the app does instead is fetch the package the way the store hands it
  out, unpack it, and run its content scripts and stylesheets in the pages — which is the
  part of an extension that changes what a page does. The extension's own background
  worker and its browser-action UI do not exist.
* **The browser proxy is a process-wide setting.** The engine allows a proxy override for
  the whole process, not for one page. The app therefore runs one browsing session at a
  time in the foreground, and the profile that owns it gets the route it asked for.
* **The profile storage is separated by the engine's own profiles.** Every profile gets its
  own cookies, cache and storage. The engine offers this from WebView 114 onwards; on an
  older engine the profiles share a storage area and the app says so.
* **The engine's own extension API is unavailable**, so the extension feature runs the
  content scripts directly rather than through a manifest-driven host.

## How to repeat the tests

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.joebrowser.app/com.joebrowser.app.MainActivity
```

1. Create a profile. It opens on `browserscan.net` the first time.
2. Open it, then go to `ipfighter.com/browser-fingerprint`.
3. The score should read 100% and the masking line "Not detected".
4. Open the menu and choose **Identity check** for the app's own verdict on the profile.

To read the values the page sees, turn on **Settings → Developer → Web inspector**, then
attach a DevTools client to the WebView:

```bash
adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>
curl http://127.0.0.1:9222/json
```
