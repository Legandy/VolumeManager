# Volume Manager

**Work in progress**
Control each app's volume independently. [Shizuku](https://shizuku.rikka.app/) is used to access privileged APIs.

Requires Android 13.

This is a fork of [VolumeManager](https://github.com/yume-chan/VolumeManager/) I updated it myself to my liking.
It's my first time using Kotlin and forking a project.
Thanks to yume-chan I got the baseline for an app that can change App Volumes that motivated me to start Android development.
I used AI Assistance for speeding up development and helping with Android development features.

## Pictures
![OverlaySystemVolScreenshot](OverlayScreenshot.png)
![OverlayAppVolScreenshot](OverlayScreenshot.png)
![VolumeTabScreenshot](VolumeTabScreenshot.png)
![OverlayTabScreenshot](OverlayTabScreenshot.png)
![AppFilterTabScreenshot](AppFilterTabScreenshot.png)



## Install
1. Install and enable [Shizuku](https://shizuku.rikka.app/)
2. Launch Volume Manager and request Shizuku permission
3. It should automatically enable its accessibility service

## Usage/Features

# Core Functionality:
- Enable accessibility button and click the button
- Independent volume control for individual apps.
- System-level audio stream management (Media, Ringtone, Notification, Alarm).

# User Interface & Experience:
- Custom overlay panel replacing standard Android volume controls.

# Accessibility & Customization:
- Volume profiles feature allowing saved configurations
- App filtering feature via Whitelist/Blacklist

# Interaction & Feedback:
- Option to trigger the overlay via volume buttons (Bluetooth/DND required).
- Ability to control overlay visibility on lock screen.
- Overlay panel configured to close with back gesture or tap on screen
- Adjustable display timeout for the overlay.

# To Do:
- Modern UI designed following Google's Material Design 3 guidelines.
- Integration with Android's Quick Settings for easy access.
- Customizable preferred starting tab/panel in the system UI.
- Advanced settings page (accessed via top bar) covering theme, backup, language, and color picker.
- Supports dynamic color themes ("Material You") adapting to the user's wallpaper.
- Option for users to customize primary colors when using static theme.
- Onboarding flow explaining required permissions (Shizuku, Accessibility Service, Bluetooth, DND).
- Adaptive Icon for displaying output device (device/bluetooth volume)
- Add functionality for the change output device button
- And probably more


## Permissions
- Shizuku (essential)
- Accessibility (only for overlay app can be used without)
- NotificationAccess (for changing DoNotDisturb mode. Also not needed if no Overlay is used)
- Bluetooth Permission (For knowing the media volume output state)

## Download
Under Releases

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

## Note

This app uses the same API as MIUI's "Adjust media sound in multiple apps". Because this API can only setting volume, not reading, the volume set by one app will not be reflected in the other one.
