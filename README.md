# Snapwall

Android accessibility service that sends you to the home screen when Snapchat's Discover section or Spotlight tab is on screen. Chats, snaps, camera, and the map still work.

## Build

**GitHub (no local setup):** push this folder to a new repo. The `Build APK` action runs on every push. Open the run under the Actions tab and download `snapwall-apk`.

**Android Studio:** open the folder, let it sync, then Build > Build APK(s).

## Install

1. Copy the APK to your phone and open it. Allow installs from that source when asked.
2. Open Snapwall, tap **Open accessibility settings**, find Snapwall under installed apps, and turn it on.
3. Android 13+ may block step 2 for sideloaded apps ("Restricted setting"). Fix: Settings > Apps > Snapwall > three-dot menu > **Allow restricted settings**, then retry.
4. Some phones (Samsung, Xiaomi, OnePlus) kill background services. Set Snapwall's battery usage to **Unrestricted**.

## Rules

| Rule | Matches |
|---|---|
| `text:Discover` | any visible element whose text or label is exactly "Discover" |
| `selected:Spotlight` | an element labeled "Spotlight" that is selected (the active nav tab) |
| `id:com.snapchat.android:id/...` | any visible element with that view id |

Default rules block the Discover heading and the Spotlight tab. Snapchat's nav shows "Spotlight" on every tab, which is why that rule uses `selected:` and not `text:`.

## When Snapchat updates and something slips through

Tap **Capture next Snapchat screen**, open the leaking screen in Snapchat, return to Snapwall, tap **View capture**, and copy it. The capture lists every visible label and view id; add a rule from it.

## Limits

- The Discover section sits right below friends' stories, so the Stories tab may get blocked as soon as it opens.
- Disabling the service in accessibility settings bypasses it. Pausing inside the app requires typing a sentence, which is the friction point.
