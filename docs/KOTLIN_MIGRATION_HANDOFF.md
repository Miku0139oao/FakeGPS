# FakeGPS — Kotlin 重構 Handoff

> **狀態（2026-11-13）**：Kotlin-first 遷移已完成。專案目前 0 個 Java 檔、49 個 Kotlin 檔，資料格式與 `fake_gps.db` 未變。以下內容保留為遷移前基準與驗收清單；實際結果見 `docs/KOTLIN_MIGRATION_REPORT.md` 與 git log。

> 給接手規劃／實作 Kotlin 重構的 agent。這份文件描述 **2026-11-13 的實際狀態**，不是原始 2016 年版本。
> 建議先讀完「不可破壞的行為」與「風險」再開始規劃。

## 1. 目前狀態

| 項目 | 值 |
|---|---|
| Repo | `D:/new/FakeGPS`（fork: `github.com/Miku0139oao/FakeGPS`） |
| 分支 | `feature/map-picker`，`master` 同 commit |
| HEAD | `180aa9f` — *Polish the map picker controls and search experience* |
| 版本 | `versionCode 4` / `versionName 2.1.0` |
| Release | `v2.1.0`（tag 指向 `180aa9f`） |
| APK SHA-256 | `99b88418c30cae74485f433e9f53ce569f11f56bcb3ee1bba36dd785bf6d1313` |
| CI | GitHub Actions 綠燈（`android.yml`: lintDebug/lintRelease + 30 unit tests + debug/release build） |
| 語言 | 目前 100% Java；Kotlin 重構尚未開始 |
| 程式量 | app + tests + loggerlibrary 約 4,285 行 Java |

- **不需要 root。** 使用 Android 標準 `addTestProvider` / `setTestProviderLocation`。
- 目標 Android 5.0–16（minSdk 21 / compileSdk 36 / targetSdk 36）。
- 已在 MuMu（Android 15）與實機邏輯上驗證；沒有 emulator 自動化測試，只有 JVM unit tests。

## 2. 不可破壞的行為（重構時逐項驗收）

1. **Mock location 必須在未 root 裝置可用**
   - `ACCESS_MOCK_LOCATION` 必須在所有 API 等級宣告（不可加 `maxSdkVersion="22"`）。
     Android 15/16 的 Developer options picker 會用 `PackageInfo.requestedPermissions` 過濾；加了 maxSdkVersion 會只剩 “Nothing”。Lint 需用 `tools:ignore="MockLocation,ProtectedPermissions"`。
   - 以 `AppOpsManager.checkOpNoThrow(OPSTR_MOCK_LOCATION, Process.myUid(), packageName)` 預檢。
   - Provider 設定：API 31+ 用 `ProviderProperties`，舊版用 `Criteria`。
   - `addTestProvider` / `setTestProviderEnable` / `setTestProviderLocation` / `removeTestProvider` 的時序與 teardown 不可退化。
2. **Linearity**：Fly mode 必須用 `elapsedRealtime()` 對快照 origin 做線性插值；不可每 tick 以目前點遞迴。
3. **Move Step 以公尺為單位**，經度依 `cos(latitude)` 換算；lat clamp、lon wrap。
4. **Location metadata**：由連續 fix 計算真實 speed/bearing；不要固定 `speed=1` / `bearing=0`；沒有移動時不要宣告 speed/bearing。
5. **權限**：只請求 `ACCESS_COARSE_LOCATION`；通知（API 33+）與 overlay 各自獨立；不要假設全部 granted。
6. **Foreground service**：`START_NOT_STICKY`、`FOREGROUND_SERVICE_TYPE_LOCATION`、通知 `VISIBILITY_SECRET`、wake lock 可依設定關閉。
7. **設定與語言**：主題（system/light/dark）、9 語系、Android 13+ per-app language；設定要持久化。
8. **書籤資料不可遺失**：`fake_gps.db`（lite-orm）內有使用者書籤；`FakeGPS` SharedPreferences 有 `last_loc` 與 settings。
9. **地圖**：osmdroid 6.1.20、OSM 圖磚、tile cache 50 MB、attribution、搜尋（Geocoder → Nominatim fallback）、夜間濾色、縮放/recenter、Copy/Open in Maps。
10. **R8 / release**：release minify + shrink，lite-orm/model keep rules；release key 在 repo 外。

## 3. Build / 環境

```bash
# 本機（Windows）
export JAVA_HOME="C:/Program Files/Microsoft/jdk-21.0.4.7-hotspot"
export ANDROID_HOME="/d/vless/.tools/android-sdk"   # platforms 35/36, build-tools 36.0.0
./gradlew lintDebug lintRelease testDebugUnitTest assembleDebug assembleRelease
```

| 項目 | 版本 |
|---|---|
| AGP | 8.13.2 |
| Gradle | 8.14.7（wrapper 有 `distributionSha256Sum`） |
| JDK | 21（build；source/target 17） |
| compileSdk / targetSdk / minSdk | 36 / 36 / **21** |
| AppCompat | 1.7.0（1.8 需要 minSdk 23） |
| Material Components | 1.12.0（1.14 需要 minSdk 23） |
| RecyclerView | 1.4.0 |
| osmdroid | 6.1.20（上游 2024-11 封存） |
| ORM | `app/libs/lite-orm-1.9.2.jar`（2016、Java 6 bytecode） |
| Tests | JUnit 4.13.2 |

- `applicationId = com.github.fakegps`，但 `namespace = com.tencent.fakegps`（歷史因素；Kotlin 重構請保留，否則 R class / release 相容性會變）。
- `buildConfig true`；`lint { abortOnError true }`。
- Kotlin stdlib 由 `kotlin-bom:1.8.22` 對齊（目前沒有 Kotlin plugin）。
- R8：`minifyEnabled true`、`shrinkResources true`、`proguard-android-optimize.txt`。
- CI 沒有 `keystore.properties`，release build 會是 unsigned；本機 release 會用 `D:/new/fakegps-release.jks`。

## 4. 檔案 / 職責對照（Java → Kotlin 目標）

### Core / domain（最適合先轉 Kotlin）

| 現有檔案 | 行數 | 職責 | Kotlin 建議 |
|---|---:|---|---|
| `model/LocPoint.java` | 45 | lat/lon value object、6 位小數 toString | `data class LocPoint` + `toInputString()` |
| `FlyPath.java` | 48 | 線性插值、完成判斷 | `class FlyPath(origin, target, durationMs)`，加 `Duration` |
| `GeoUtils.java` | 46 | 公尺位移、經度 cos 換算、wrap | `object GeoUtils` 或 `LatLng.offsetMeters()` |
| `LocationDelta.java` | 75 | haversine 距離、bearing、speed | `data class LocationDelta` + top-level `between()` |
| `CoordinateParser.java` | 107 | 純座標 / geo: / 地圖 URL / DMS | `object CoordinateParser`，保持現有 regex 與測試 |
| `FakeGpsUtils.java` | 123 | 解析、驗證、clipboard、AppOps 檢查 | `object`；`AppOps` 檢查可移到 `MockLocationChecker` |
| `SettingsStore.java` | 52 | theme / keep-running 偏好 | DataStore Preferences（見 §6） |
| `model/BookmarkList.java` | 30 | 書籤 list state（純 Java 測試用） | `data class` / immutable list，或直接給 ViewModel |
| `model/LocBookmark.java` | 66 | lite-orm entity | Room `@Entity`（見 §6） |

### Engine / service

| 現有檔案 | 行數 | 職責 | Kotlin 建議 |
|---|---:|---|---|
| `LocationThread.java` | 246 | HandlerThread、1 Hz 注入、provider 管理、teardown | `LocationEngine`（CoroutineScope + `delay(1000)` / `Flow`） |
| `JoyStickManager.java` | 318 | singleton、current point、fly state、viewport callbacks、service 啟動 | `MovementController` + `MockLocationSession`，以 StateFlow 暴露狀態 |
| `FakeLocationService.java` | 129 | FGS、notification、wake lock | Kotlin Service + `ServiceCompat.startForeground` |
| `FakeGpsApp.java` | 84 | Application、theme、osmdroid init、logger、LiteOrm | `Application` + DI / initializer |
| `DbUtils.java` | 79 | lite-orm CRUD、SharedPreferences last loc | `BookmarkRepository` + Room + DataStore |
| `BookmarkEvents.java` | 35 | in-process listener | `MutableSharedFlow` / `Flow<List<LocBookmark>>` |

### UI

| 現有檔案 | 行數 | 職責 | Kotlin 建議 |
|---|---:|---|---|
| `ui/MainActivity.java` | 406 | main screen、權限鏈、start/stop、RecyclerView、狀態卡 | ViewModel + StateFlow + ViewBinding（或 Compose） |
| `ui/FlyToActivity.java` | 204 | route、fly time、fly state listener、bookmarks | ViewModel + StateFlow |
| `ui/MapPickerActivity.java` | 414 | osmdroid、搜尋、縮放、面板、離線、夜間濾色 | ViewModel + `MapPickerState`；地圖邏輯可拆 `MapController` |
| `ui/BookmarkActivity.java` | 100 | 新增書籤 dialog | MaterialAlertDialog 或 Compose dialog |
| `ui/BaseBookmarkActivity.java` | 98 | RecyclerView + BookmarkEvents + delete | ViewModel / shared composable |
| `ui/BookmarkAdapter.java` | 85 | RecyclerView adapter | ListAdapter + DiffUtil |
| `ui/SettingsActivity.java` | 199 | language/theme/wake lock/about | Preference/Compose settings screen |
| `ui/JoyStickView.java` | 169 | overlay WindowManager view、drag | 保留 custom View；可加 ComposeView 但 overlay 生命週期要小心 |
| `ui/JoyStickButton.java` | 68 | long-press repeat button | 保留 custom View |
| `ui/UiUtils.java` | 55 | edge-to-edge、insets、system bar appearance | `WindowInsets` helper |
| `ui/ScreenUtils.java` | 51 | status bar / screen metrics | `WindowMetrics` helper |

### Other

| 檔案 | 職責 |
|---|---|
| `AddressSearch.java` | Geocoder → Nominatim fallback；需保留 User-Agent、rate limit、timeout。 |
| `loggerlibrary/` | 舊 file logger；debug only。Kotlin 重構可考慮 Timber/Logcat，但 release 不寫座標日誌的決策要保留。 |
| `res/values-*/strings.xml` | en, zh-rTW, zh-rCN, ja, ko, es, fr, de, pt-rBR；`locales_config.xml`。 |
| `res/layout/*` | Material 3 XML；Kotlin 若用 Compose，需逐屏替換，過渡期可共存。 |

## 5. 目前行為流程

### Main
1. `FakeGpsApp.onCreate` 套用 theme（`SettingsStore`）、初始化 osmdroid、`JoyStickManager`。
2. `MainActivity` 讀 current/last loc、Move Step、bookmarks。
3. Start → 權限鏈（COARSE → notifications → overlay → mock app AppOps）→ `doStart()`。
4. `JoyStickManager.start(point)` → `LocationThread` + FGS + overlay。
5. `MainActivity.finish()`；通知與浮動搖桿保留。
6. Stop → `JoyStickManager.stop()` → remove providers、stop FGS、hide overlay。

### Location loop
- 每 1000 ms 讀 `JoyStickManager.getUpdateLocPoint()`。
- 若 fly active，以 `FlyPath.pointAt(elapsed)` 產生點；完成時清 `mIsFlyMode` 並通知 UI。
- `LocationDelta` 計算 speed/bearing；寫入 GPS 與 NETWORK test provider。
- 若 provider 全部失敗 → stop loop + `onMockLocationUnavailable`（Main 顯示引導）。

### Map
- `MapPickerActivity` 以 `LocPoint` 為 input；MAPNIK tiles、`setTilesScaledToDpi(true)`、自訂 zoom/recenter FAB、夜間 ColorMatrix filter。
- 搜尋：`AddressSearch.search()` 在 executor；Geocoder 失敗才走 Nominatim；結果 dialog → `selectPoint()`。
- 選點：tap / long-press / drag marker；`tv_selected` 更新（drag 100ms throttle）；Copy / Open in Maps。
- Panel：Use location / Cancel；attribution 可點。

### Settings / i18n
- `SettingsStore`：theme、keep-running。
- `AppCompatDelegate.setApplicationLocales` + `localeConfig`。
- 主題：`AppCompatDelegate.setDefaultNightMode`（Application 啟動時）。
- 9 個 locale；所有字串已 externalize，`locales_config.xml` 同步。

## 6. Kotlin 重構建議架構

### 建議模組
```
:app            Android app / UI / DI
:core-model     LocPoint, FlyPath, GeoUtils, CoordinateParser（純 Kotlin）
:core-data      Room, DataStore, BookmarkRepository
:location       MockLocationEngine, FGS, provider management
:map            osmdroid picker + AddressSearch
```
若不想多模組，至少把 `core-model` 與 `core-data` 分開，方便 JVM tests 與 Room schema 管理。

### 建議技術選擇
- **Kotlin**：與 AGP 8.13.2 相容的 Kotlin（planning 時查 compatibility matrix；不要直接假設最新版）。
- **Coroutines + Flow**：1 Hz loop 用 `CoroutineScope(Dispatchers.Default)` + `delay` 或 `flow { while(isActive) { emit(...); delay(1000) } }`。
- **StateFlow / ViewModel**：`MainUiState`、`FlyUiState`、`MapUiState`、`SettingsUiState`。
- **ViewBinding**：若保留 XML；若 Compose，先從 Settings/Map panel 開始，不要一次翻掉 overlay。
- **Room + KSP**：`LocBookmark` entity；保留 `fake_gps.db` 資料遷移（見 §7）。
- **DataStore Preferences**：theme、locale、keep-running、last loc。
- **Activity Result API**：取代 `startActivityForResult`（Main 的 overlay/mock/map、Fly 的 from/to）。
- **DI**：Hilt 或手動 `AppContainer`；此 app 規模小，手動 DI 已足夠。
- **測試**：JUnit4 + `kotlinx-coroutines-test` + Turbine；Room in-memory instrumentation；保留現有 pure logic tests。

### 建議轉換順序
1. **Build**：加 Kotlin plugin、KSP、version catalog；保留 Java 檔案可共存。
2. **Pure logic → Kotlin**：LocPoint、FlyPath、GeoUtils、LocationDelta、CoordinateParser、BookmarkList、FakeGpsUtils 純函式；把現有測試一起搬。
3. **Persistence**：Room entity/DAO/database + repository；先與 lite-orm 並存，完成一次性 migration 後移除 lite-orm。
4. **Engine**：`LocationEngine` + `MovementController`；以 Flow 暴露 `LocationFix` / `MovementState`；保留 1 Hz、wake lock、FGS。
5. **UI**：Main → Fly → Map → Bookmarks → Settings；每屏加 ViewModel/StateFlow；Activity Result API。
6. **Overlay/Service**：Kotlin service + overlay custom View；確認 Android 15/16 FGS。
7. **清理**：刪 Java、lite-orm jar、舊 logger（或保留 debug）、R8 keep rules 更新。
8. **回歸**：JVM tests + 真機/MuMu smoke（見 §8）。

## 7. 資料遷移（最容易出錯）

- `fake_gps.db` 由 lite-orm 建立；`LocBookmark` 有 `_id`、`mName`、`mLocPoint`。`mLocPoint` 的實際儲存格式需先以 `sqlite3`/`PRAGMA table_info` 檢查，不要假設。
- 建議策略：**保留舊 DB 檔名**，Room `@Database(version=2)` 用 `Migration` 或一次性 importer：
  - 先開舊 DB、讀 `LocBookmark`（lite-orm 或 raw SQLite），轉成 Room entity；
  - 寫入新 Room DB / 同一 DB 的新 table；
  - 保留 `last_loc` 在 DataStore/SharedPreferences；
  - 遷移後再移除 lite-orm。
- 若無法安全遷移，**不要**直接換 DB 檔名或清空；使用者書籤會遺失。
- settings 目前在 `SharedPreferences("settings")`；theme/keep-running 要一起搬。

## 8. 測試 / 驗證

### JVM（目前 30 tests）
```bash
./gradlew testDebugUnitTest
```
- `FlyPathTest`（5）、`GeoUtilsTest`（5）、`LocationDeltaTest`（4）、`CoordinateParserTest`（6）、`FakeGpsUtilsTest`（8）、`BookmarkListTest`（2）。
- 新增 Kotlin 時用 `kotlinx-coroutines-test` + Turbine；不要刪掉這些覆蓋率。

### MuMu / 真機 smoke（必做）
- 安裝：`MuMuManager.exe control -v 0 app install --apk <apk>`。
- shell：`MuMuManager.exe sh -v 0 -c "..."`。
- mock app：`appops set com.github.fakegps android:mock_location allow`；或在 Developer options 選取（picker 需列出 FakeGPS）。
- 權限：`pm grant com.github.fakegps android.permission.ACCESS_COARSE_LOCATION`、`POST_NOTIFICATIONS`；overlay `appops set ... SYSTEM_ALERT_WINDOW allow`。
- 驗證：`dumpsys location` 看 `mock`；`dumpsys activity services` 看 `isForeground=true`、`types=0x8`。
- 必測：Start/Stop、fly、joystick 公尺步長、bookmark CRUD、map search/zoom/recenter/copy/open、語言切換、theme、螢幕關閉 injection、Android 15/16 picker。
- MuMu 限制：Settings 的 picker 可能只列系統 App（此問題已由 manifest 修正，但仍需真機覆核）；硬體鍵盤可能不顯示 IME。

### CI
- `.github/workflows/android.yml`：`lintDebug lintRelease testDebugUnitTest assembleDebug assembleRelease`；JDK 21；actions 以 SHA 釘選。
- 新增 Kotlin/Room 後需更新 R8 keep rules 與 CI；不要讓 release build 在 CI 失效。

## 9. Release / Signing

- Keystore：`D:/new/fakegps-release.jks`（repo 外）。
- `keystore.properties`（repo root，gitignored）：storeFile/storePassword/keyAlias/keyPassword。
- `apksigner verify --print-certs app/build/outputs/apk/release/FakeGPS_v2.1.0.apk`。
- Release：`gh release create v2.1.0 ... --target feature/map-picker`；tag 必須指向 release commit。
- 目前 release notes 是繁中教學；更新版本時要同步圖片（`screenshot/guide-*`）與 README/README_CN。

## 10. 已知技術債 / 風險

1. **minSdk 21 vs 新函式庫**：Material 1.14、AppCompat 1.8 都要求 minSdk 23。若要升級這兩個，必須決定是否放棄 Android 5.0/5.1；目前刻意停在 Material 1.12 / AppCompat 1.7。
2. **lite-orm**：2016 jar、Java 6 bytecode、無上游升級路徑；Room 遷移是主要風險。
3. **osmdroid 6.1.20**：上游 2024-11 封存；若重構要換 MapLibre，需重新處理 tile source/style/attribution。
4. **Nominatim**：公共服務、需 User-Agent、避免高頻；AddressSearch 目前是使用者觸發、10s timeout。
5. **Android 15/16 mock picker**：`ACCESS_MOCK_LOCATION` 必須在 requestedPermissions；不要加 maxSdkVersion。
6. **FGS / wake lock**：Android 14+ location FGS 需要 location permission；wake lock 由設定控制；release 不寫座標日誌。
7. **R8**：Room/Kotlin serialization/反射需要 keep rules；目前只保留 lite-orm/model。
8. **Locale/theme recreate**：`setApplicationLocales` 與 `setDefaultNightMode` 會 recreate activities；ViewModel/StateFlow 要能承受。
9. **namespace/applicationId 不一致**：`com.tencent.fakegps` namespace vs `com.github.fakegps` applicationId；Kotlin 重構不要順手改，會影響 R class、資料路徑、release 相容性。
10. **Overlay context theme**：`JoyStickView` 由 application context inflate；不要用 `?attr/...`（曾造成 `InflateException`），目前用明確 light/night 顏色資源。

## 11. 交給 planning agent 的必答問題

1. UI 要 **Kotlin + Compose**，還是 **Kotlin + XML/ViewBinding**？若是 Compose，overlay/service 是否維持 View？
2. minSdk 維持 **21**，還是升到 **23** 以使用 Material 1.14/AppCompat 1.8？
3. 持久化：**Room**、SQLDelight，還是暫時保留 lite-orm？若 Room，舊 DB migration 的 schema 要先確認。
4. 地圖：保留 **osmdroid**，還是換 **MapLibre**？
5. DI：**Hilt** 還是手動 `AppContainer`？
6. 模組：單一 `:app`，還是 `:core-model`/`:core-data`/`:location`/`:map`？
7. 重構範圍：一次翻完，還是 **strangler pattern**（先 core/data，再 UI）？
8. 版本節奏：`2.2.0`（Kotlin 內部重構）還是 `3.0.0`（含 Compose/MapLibre）？
9. 測試門檻：保留全部 30 JVM tests，新增哪些 instrumentation/UI tests？
10. 是否要把 release 改成 **signed CI**（GitHub secrets）或維持本機 release？

## 12. 快速驗收清單

- [ ] 未 root 裝置可選 FakeGPS 為 mock app（picker 有列出）
- [ ] Start 後 `dumpsys location` 顯示 `mock`
- [ ] FGS `isForeground=true`、type location
- [ ] Fly 25/50/75/100% 等比例
- [ ] Move Step 2m 在赤道/高緯都正確
- [ ] speed/bearing 隨移動變化
- [ ] 書籤新增/使用/刪除、舊資料不遺失
- [ ] 地圖搜尋、縮放、recenter、copy、open external、夜間濾色
- [ ] 9 語系切換、theme 切換、設定持久化
- [ ] 螢幕關閉仍注入（若 wake lock 開啟）
- [ ] `lintDebug lintRelease testDebugUnitTest assembleDebug assembleRelease` 全綠
- [ ] release APK 已簽章且 SHA-256 與 release 一致

## 13. 參考連結

- Fork：https://github.com/Miku0139oao/FakeGPS
- Release：https://github.com/Miku0139oao/FakeGPS/releases/tag/v2.1.0
- 主 README：`README.md`
- 繁中 README：`README_CN.md`
- 截圖：`screenshot/guide-*.png`
- CI：`.github/workflows/android.yml`
