# FakeGPS

[中文Readme](https://github.com/xiangtailiang/FakeGPS/blob/master/README_CN.md)

FakeGPS is a GPS device simulator. GPS location signal keep output according to the given coordinates. By the direction keys on the joystick, the user can simulate walking on the map.

## Features
- Simulate the real GPS devices, output GPS location per second.
- Have two modes to set the new location: **Jump Mode** and **Flight Mode**. **Jump Mode**: jump to the new location in second. **Flight Mode**: fly to the new location according to the given time by linear interpolation.
- With a global floating joystick, the direction button will do a certain offset at the current position (via Move Step, degrees unit). Click to move a step, long press will move continuously.
- Bookmarks support. In the Bookmarks List, tap to use it, long press to Delete. Long Press Bookmark button on the joystick to copy the current coordinates to the clipboard, make it easy to share it with other people.
- **Visual map picker** powered by OpenStreetMap (osmdroid): tap, long-press, or drag a marker to choose a location. No API key or Google Play Services required. Map tiles need a network connection; coordinate entry and mock location still work offline.

## Screenshots

![Screenshot_1](./screenshot/Screenshot_1.png)

![Screenshot_2](./screenshot/Screenshot_2.png)

![Screenshot_3](./screenshot/Screenshot_3.png)

## Download
[FakeGPS V2.0](https://github.com/xiangtailiang/FakeGPS/releases/tag/2.0)

## Requirements
- Android 5.0 (API 21) or above
- Tested on Android 16 (API 36)

## Installation

**No root required!** FakeGPS v2.0 uses the standard Android Mock Location API.

1. Install the APK on your device.
2. Enable **Developer Options** on your device (Settings > About Phone > Tap Build Number 7 times).
3. Go to **Settings > Developer Options > Select mock location app** and choose **FakeGPS**.
4. Launch FakeGPS, enter coordinates, and click **Start**.
5. The app will request Location, Notification, and Overlay permissions on first launch. Grant them all.
6. The floating joystick will appear. Open a Maps app (Google Maps, etc.) to verify the mock location.

## Changes in v2.1.0
- Added a visual location picker backed by OpenStreetMap tiles (osmdroid 6.1.20). Tap, long-press, or drag the marker; the result fills the location field in the main and flight screens.
- Map tiles are cached in app-private storage, so no storage permission is needed. `INTERNET` and `ACCESS_NETWORK_STATE` are used only for tile downloads and geolocation-free map rendering.
- Map attribution: © OpenStreetMap contributors.

## Changes in v2.0.1
- **Release signing fixed**: release builds are signed with a key supplied through a git-ignored `keystore.properties`; the old committed debug keystore and hard-coded password were removed.
- **Mock-app check**: if FakeGPS is not selected in Developer options, the app now explains how to enable it instead of showing a running notification while injecting nothing.
- **Flight mode is actually linear now**: interpolation is based on elapsed time from a snapshotted origin instead of compounding on the previous tick.
- Flight state is reset on start/stop; the Fly screen refreshes its button on resume.
- Inputs are validated: NaN and out-of-range coordinates, non-positive move steps, and invalid fly times are rejected.
- Deleting the last bookmark clears the list; a failed bookmark save no longer closes the dialog.
- Removed unused `INTERNET` / `RECEIVE_BOOT_COMPLETED` permissions and the logger library's storage permissions; auto backup is disabled.
- Release builds no longer write coordinate logs; lite-orm debug logging follows `BuildConfig.DEBUG`.
- The foreground service is `START_NOT_STICKY` and declares its foreground service type explicitly.

## Changes in v2.0
- **No root required** — uses standard `addTestProvider` / `setTestProviderLocation` API instead of hidden `ILocationManager`.
- **Supports Android 5.0 ~ 16** (API 21 ~ 36).
- Migrated from Support Library to **AndroidX**.
- Added **Foreground Service** with notification for reliable background operation on Android 8.0+.
- Floating joystick uses `TYPE_APPLICATION_OVERLAY` for Android 8.0+ compatibility.
- Runtime permission requests for Location, Notification (Android 13+), and Overlay.
- Scoped Storage compatible logging.
- Updated build toolchain: Gradle 8.11.1, AGP 8.7.3, Java 17.

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/FakeGPS_v2.1.0.apk`

### Release signing

Release builds read `keystore.properties` from the project root (git-ignored):

```properties
storeFile=/absolute/path/to/fakegps-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Keep the keystore and this file outside version control. Verify the result with
`apksigner verify --print-certs app/build/outputs/apk/release/FakeGPS_v2.1.0.apk`.

## Contribute
If you would like to contribute code to FakeGPS, you can do so through GitHub by forking the repository and sending a pull request.

## License
[The MIT License (MIT)](http://opensource.org/licenses/MIT)
