# Snapwall

Android accessibility service that backs you out when you open a Discover story or Spotlight short in Snapchat. The Stories tab, friends' stories, chats, snaps, camera, and the map still work.

## Build

**GitHub (no local setup):** push this folder to a new repo. The `Build APK` action runs on every push. Open the run under the Actions tab and download `snapwall-apk`.

**Android Studio:** open the folder, let it sync, then Build > Build APK(s).

## Install

1. Copy the APK to your phone and open it. Allow installs from that source when asked.
2. Open Snapwall, tap **Open accessibility settings**, find Snapwall under installed apps, and turn it on.
3. Android 13+ may block step 2 for sideloaded apps ("Restricted setting"). Fix: Settings > Apps > Snapwall > three-dot menu > **Allow restricted settings**, then retry.
4. Some phones (Samsung, Xiaomi, OnePlus) kill background services. Set Snapwall's battery usage to **Unrestricted**.

## Signing

Without the `SIGNING_KEYSTORE_B64` and `SIGNING_PASSWORD` repo secrets, each build is signed with a throwaway debug key, so every new APK needs an uninstall first. Add the secrets to make builds install as updates.

## Rules

| Rule | Matches |
|---|---|
| `section:Discover` | opening anything listed under the "Discover" heading (learned from what sits above and below the heading in the Stories list) |
| `text:Some label` | any visible element whose text or label is exactly that |
| `selected:Spotlight` | an element labeled "Spotlight" that is selected (the active nav tab) |
| `id:com.snapchat.android:id/...` | any visible element with that view id |

Default rules are `section:Discover` and `selected:Spotlight`. Snapchat's nav shows "Spotlight" on every tab, which is why that rule uses `selected:` and not `text:`.

## When Snapchat updates and something slips through

Tap **Record Snapchat for 60 seconds**, then in Snapchat open the Stories tab, a friend's story, a Discover story, and Spotlight. Return to Snapwall, tap **View capture**, and copy it. Blocking is off while recording. The capture lists every visible label and view id per screen; add a rule from it.

## Limits

- Disabling the service in accessibility settings bypasses it. Pausing inside the app requires typing a sentence, which is the friction point.
