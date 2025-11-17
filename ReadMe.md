# Volume Mixer Panel

**Work in progress**
Control each app's volume independently.
Use a more modern and more useful Volume Panel than Android's native one.

This is a fork of [VolumeManager](https://github.com/yume-chan/VolumeManager/) I updated it myself to my liking.
It's my first time using Kotlin and forking a project.
Special thanks to Yume-Chan for identifying these APIs and how to use them via Shizuku.
I used AI Assistance for speeding up development and helping with Android development features.

## Pictures
![OverlaySystemVolScreenshot](OverlayScreenshot.png)
![OverlayAppVolScreenshot](OverlayScreenshot.png)
![VolumeTabScreenshot](VolumeTabScreenshot.png)
![OverlayTabScreenshot](OverlayTabScreenshot.png)
![AppFilterTabScreenshot](AppFilterTabScreenshot.png)

## Requirements
- Android 13+ (APIs are only available at these versions)
- Shizuku (is used to access privileged APIs.)

## Permissions
- Shizuku (essential)
- Accessibility (Only for volume trigger event. Not needed if Overlay is triggered differently)
- NotificationAccess (for changing DoNotDisturb mode. Also not needed if no Overlay is used)

## Conflicts
- Apps that bypass native volume control (e.g. Poweramp with the optional Direct Volume Control)
- This app uses the same API as MIUI's "Adjust media sound in multiple apps".
Because this API can only set the volume, but can not read the volume state results in a wrong reflection of the volume state across this app and MIUI's.

## Install
1. Install and enable [Shizuku](https://shizuku.rikka.app/)
2. Launch Volume Mixer Panel and request Shizuku permission.

## Tip
If you want to use many Audio Players at once you may need to disable Audio Focus for these apps.
You can do that with [AppOps](https://appops.rikka.app/guide/)

## Usage/Features

# Core Functionality:
- Control each app's volume independently.
- System-level audio stream management (Media, Ringtone, Notification, Alarm, DND)

# User Interface & Experience:
- Custom overlay panel replacing standard Android volume controls.
- Two tab overlay for more functionality while keeping a low profile

First tab:
- Mute media, alarm and call with pressing the corresponding icon
- Set Vibrate, Silent or Loud with pressing the corresponding icon
- Set DND with a dedicated button
- Change output with a dedicated button
- Adaptive media icon for displaying output device (Speaker, Headset, Bluetooth)

Second Tab:
- Change individual App volume in a scrollable list
- Mute app volume when pressing app icon

# Preferences:
- App filtering feature via Whitelist/Blacklist for Overlay
- Option to trigger the overlay via volume buttons.
- Ability to control overlay visibility on lock screen.
- Overlay panel configured to close with back gesture or tap on screen
- Adjustable display timeout for the overlay (timeout can be disabled too)

# To Do:
- Modern UI designed following Google's Material Design 3 guidelines.
- Integration with Android's Quick Settings for easy access.
- Advanced settings page (accessed via top bar) covering theme, backup, language, and color picker.
- Supports dynamic color themes ("Material You") adapting to the user's wallpaper.
- Option for users to customize primary colors when using static theme.
- Add functionality for the change output device button
- And probably more


## Download
Under Releases



Comparison from Yume-Chan's Readme:
## Compare to [SoundMaster from ShizuTools](https://github.com/legendsayantan/ShizuTools/wiki/SoundMaster)

This app uses hidden API to directly change each audio stream's volume.

SoundMaster uses MediaProjection API to record audio from each app and apply post-effects.

| Feature                        | Volume Manager  | SoundMaster     |
| ------------------------------ | --------------- | --------------- |
| Minimal Android version        | 13              | 10              |
| Control volume of each app     | ✅              | ✅              |
| Set output device for each app | ❌ <sup>1</sup> | ✅              |
| Change left-right balance      | ❌ <sup>2</sup> | ✅              |
| Equalizer (EQ)                 | ❌              | ✅              |
| Control protected apps         | ✅              | ❌ <sup>3</sup> |
| Zero latency added             | ✅              | ❌              |

<sup>1</sup>: There are APIs to do that, but not implemented in this app

<sup>2</sup>: There are other APIs to do that, but not implemented in this app

<sup>3</sup>: Can be worked around by patching the app

## How does it work

1. Use [`AudioManager#getActivePlaybackConfigurations()`](<https://developer.android.com/reference/android/media/AudioManager#getActivePlaybackConfigurations()>) to get list of [`AudioPlaybackConfiguration`](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/media/java/android/media/AudioPlaybackConfiguration.java;drc=e282cc572ef848b1cb8d622c2c4939aac37c3b27).

   Each `AudioPlaybackConfiguration` represents a audio player, like [`AudioTrack`](https://developer.android.com/reference/android/media/AudioTrack) and [`MediaPlayer`](https://developer.android.com/media/platform/mediaplayer)

2. Use [`ActivityManager#getRunningAppProcesses()`](<https://developer.android.com/reference/android/app/ActivityManager#getRunningAppProcesses()>) and [`PackageManager#getApplicationInfo()`](<https://developer.android.com/reference/android/content/pm/PackageManager#getApplicationInfo(java.lang.String,%20android.content.pm.PackageManager.ApplicationInfoFlags)>) to map and group `AudioPlaybackConfiguration#getClientPid()` to apps
3. Use `AudioPlaybackConfiguration#getPlayerProxy()` and [`IPlayer.setVolume()`](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/av/media/libaudioclient/aidl/android/media/IPlayer.aidl;l=29;drc=75e48fea431b1de2bf1715eb5c22ba4c794200bd) to update the internal volume multiplier
4. Use [`AudioManager#registerAudioPlaybackCallback()`](<https://developer.android.com/reference/android/media/AudioManager?hl=en#registerAudioPlaybackCallback(android.media.AudioManager.AudioPlaybackCallback,%20android.os.Handler)>) to listen for new `AudioPlaybackConfiguration`s and apply current volume to them.
