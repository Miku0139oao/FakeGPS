# FakeGPS — Kotlin-first 遷移結果報告

> 日期：2026-11-13 · 分支：`refactor/kotlin-first` · 基準：`f552ca6`

## 1. 結果摘要

- Java 檔案：**39 → 0**
- Kotlin 檔案：**0 → 49**（35 main + 14 test）
- JVM 測試：**31 → 61**（app 53 + loggerlibrary 8），全部通過
- `lintDebug` / `lintRelease`：**0 errors / 35 warnings**（皆為既有類別）
- `assembleDebug` / `assembleRelease`（含 R8、shrinkResources）：成功
- `fake_gps.db`、SharedPreferences、Manifest 元件名稱、minSdk 21–36 全部不變
- 已在 MuMu（Android 15）完成 debug APK 的升級與功能 smoke test

## 2. 語言與程式量

| 項目 | 遷移前 | 遷移後 |
|---|---:|---:|
| Java 檔案 | 39 | 0 |
| Kotlin 檔案 | 0 | 49 |
| main 原始碼 | 32 Java | 35 Kotlin |
| 測試檔 | 7 Java | 14 Kotlin |
| main 行數 | 3,952 | 3,822 |
| test 行數 | 347 | 847 |
| JVM 測試 | 31 | 61 |

新增 main 類別：`BookmarkRepository`（IO dispatcher + Mutex）、`MovementState`（可注入時鐘的移動／飛行狀態）、`FixValues`（純 fix payload）。

## 3. 建置與測試

最終使用的工具鏈：

| 項目 | 版本 |
|---|---|
| Kotlin Gradle Plugin | 2.3.21 |
| Gradle | 8.14.7 |
| AGP | 8.13.2 |
| JDK | 21（source/target 17） |
| compileSdk / targetSdk / minSdk | 36 / 36 / 21 |

```bash
JAVA_HOME="C:/Program Files/Microsoft/jdk-21.0.4.7-hotspot" \
ANDROID_HOME="D:/vless/.tools/android-sdk" \
./gradlew :app:clean :loggerlibrary:clean clean \
  lintDebug lintRelease testDebugUnitTest assembleDebug assembleRelease
```

結果：

| 項目 | 結果 |
|---|---|
| Clean build | BUILD SUCCESSFUL |
| Kotlin stdlib | 2.3.21（1.8.22 BOM 已移除；runtime 無 1.8.22 jdk7/jdk8 artifact） |
| Lint | 0 errors / 35 warnings |
| Tests | 61 passed / 0 failed |
| Debug APK | 7,141,807 bytes（baseline 6,947,419，+2.80%） |
| Release APK | 2,280,466 bytes（baseline 2,216,743，+2.88%） |
| Release SHA-256 | `29d9024764a501949d07825fbd92c36f88d138bb098b86cabcab4e5d4b5fe4d3` |

## 4. 資料相容性

- `LocPoint` 以 Java 序列化存放在 lite-orm 的 `mLocPoint` BLOB；轉換後仍保留：
  - FQN `com.github.fakegps.model.LocPoint`
  - `serialVersionUID = -1770575152720897533`
  - 欄位 `mLatitude` / `mLongitude`（primitive double）
- 以 Java 時期實作產生的 golden fixture（`app/src/test/resources/locpoint_legacy.ser`）在 Kotlin 版本仍可反序列化。
- LiteOrm schema 由 reflection 測試鎖住：表 `LocBookmark`、主鍵 `_id`、欄位 `mName` / `mLocPoint`、`@NotNull`、`@PrimaryKey(AUTO_INCREMENT)`、無參數建構子。
- R8 release dex 直接檢查：`serialVersionUID` 值與 `mLatitude` / `mLongitude` 欄位均未被改名或移除。
- `JoyStickButton` 的 `(Context, AttributeSet)` 建構子在 release dex 中存在（XML inflation 契約）。
- 裝置升級測試：舊 debug APK → 新 debug APK，`fake_gps.db` 的 1 筆書籤與 95-byte BLOB、`last_loc`、`theme=dark` 全部保留。

## 5. 裝置 Smoke Test（MuMu，Android 15）

安裝方式：以 MuMuManager 原地安裝 debug APK（保留資料）。全程無 `AndroidRuntime` crash。

| 項目 | 結果 |
|---|---|
| 原地升級保留 DB / prefs | **Pass** |
| 冷啟動 | **Pass**（約 0.9–1.3 秒，dark theme 與舊資料正常） |
| Start：FGS | **Pass**（`isForeground=true`、`types=0x8` location、通知 `VISIBILITY_SECRET`） |
| Mock 注入 | **Pass**（GPS/NETWORK 均為 mock，hAcc=10、alt=50、vAcc=1） |
| 右箭頭 | **Pass**（經度 +1.9e-5，約 2 m） |
| 上箭頭 | **Pass**（緯度 +1.8e-5，約 2 m） |
| 長按連發 | **Pass**（1.6 秒按住約移動 6 m） |
| Stop | **Pass**（service、mock provider、overlay 全部移除） |
| Stop→Start | **Pass**（FGS 與注入恢復） |
| Fly | **Pass**（10 秒線性插值，完成於精確終點；按鈕恢復「開始飛行」） |
| 書籤新增 | **Pass**（DB 由 1 筆變 2 筆） |
| 書籤跨 process 持久化 | **Pass**（force-stop 後重啟仍顯示 2 筆） |
| 書籤刪除 | **Pass**（回到 1 筆） |
| 地圖選點 | **Pass**（OSM 圖磚、marker、夜間 UI、縮放/recenter FAB 顯示） |
| 地址搜尋 | **Pass**（Geocoder 回「東京都, 日本」） |
| Activity Result 回傳 | **Pass**（選取東京 → 使用此地點 → 主畫面顯示 35.676860,139.763895） |
| 地圖結果 Start | **Pass**（mock 注入東京座標） |
| Settings 主題切換 | **Pass**（深色 → 淺色 → 深色，Activity 正常重建、prefs 更新） |
| 螢幕關閉注入 | **Pass**（關螢幕 6 秒後 `elapsedRealtime` 仍持續前進） |
| 測試後資料還原 | **Pass**（保留原有 1 筆 Bookmark、`last_loc=(22.373292 , 114.178859)`、theme=dark） |

### 5.1 懸浮窗選點與選點 UI（後續 UX 更新）

- 懸浮窗底部新增「地圖」按鈕，直接開啟 App 內建 OSM 選點地圖，不需要先進主畫面。
- 選點完成後**立即套用**到目前 mock 位置，並以獨立 task 關閉選點畫面，返回使用者原本的 App（已用 Google Maps 實測）。
- 只要任何 FakeGPS Activity 在前景，懸浮搖桿會自動隱藏；回到其他 App 才重新顯示。
- 懸浮搖桿仍可拖曳，但現在會限制在畫面內（x 0–736、y 0–1568 @ 1440×2560）。
- 選點底部卡片縮小：縮短提示、降低字級與間距、保留 Copy／Open in Maps／Cancel／Use；9 個語系同步更新。
- 技術限制：Android App 無法讀取 Google Maps 的攝影機中心／縮放，因此不能直接把 Google Maps 畫面中心轉成座標；本功能改用 App 自己的 OSM 地圖，選完立即套用並返回 Google Maps。
- 裝置驗證：debug 與 **R8 release（debug 簽章、保留資料）** 都完成「Google Maps → 懸浮窗地圖 → 選點 → 套用 → 返回 Google Maps」，無 crash。

## 6. 未驗證 / 已知限制

- **Release APK 裝置安裝**：shipped APK 以 release keystore 簽章並成功建置；R8/minify 行為已用「release 建置 + debug 簽章」在裝置完整驗證（非 DEBUGGABLE、Start/FGS/mock、懸浮窗選點、Google Maps 返回）。release-key 簽章的 APK 未再次安裝，以避免清除裝置上的既有資料；先前（功能加入前）已安裝並啟動過。
- **語言切換 UI 未實際切換**：Settings 的語言摘要與 per-app locale（繁體中文）已確認；本功能新增字串已同步到 9 個語系，但未逐一語言切換測試。
- **OEM / Android 5–14 實機**未驗證，MuMu 為 Android 15。
- 未量測 startup / memory / CPU / 1 Hz jitter 的遷移前後比較（僅量測 APK size）。
- 既有行為保留、未修正：
  - FlyPath / LocationDelta 的 antimeridian 行為。
  - API < 33 的 per-app language auto-store 未宣告。
  - wake lock 設定只在 service 啟動時讀取。
  - Service 被外部停止時引擎仍可能繼續。
  - 浮動搖桿可被拖出畫面外（本次測試即發生，需 force-stop 重置；既有行為）。
- `ACCESS_FINE_LOCATION` 未請求、osmdroid 已封存、lite-orm 老舊：維持現狀。

## 7. Commit

| Commit | 內容 |
|---|---|
| `629d71a` | Phase 0：baseline 與資料契約測試 |
| `832f7ae` | Phase 1：Kotlin 工具鏈 |
| `dbd82af` | Phase 2：純邏輯與工具 |
| `96d4a94` | Phase 3：模型與持久化 |
| `dd30149` | Phase 4：UI / ViewBinding / Activity Result |
| `baf7c67` | Phase 5：定位引擎與 FGS |
| `a3584f4` | Phase 6：loggerlibrary 與清理 |
| `c40fce2` | 快速 Stop→Start 排程修正 |
| `d7c5c0f` | 忽略 Kotlin 編譯器 session data |
| `82027a0` | 遷移結果與首次裝置 smoke test 文件 |
| `6693bbf` | 懸浮窗地圖選點、App 內隱藏 overlay、選點 UI 精簡、拖曳邊界 |
