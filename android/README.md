# Android 原生成績查詢 App

這是成績查詢系統的 Android 原生版本，使用 Kotlin、Jetpack Compose 與 Material 3 實作。手機端直接連線學校系統，不依賴既有 Flask server。

## 技術棧

- Kotlin + Jetpack Compose + Material 3
- OkHttp 直連 `https://shcloud2.k12ea.gov.tw/CLHSTYC`
- Jsoup 解析登入頁 hidden token 與 captcha 資訊
- kotlinx.serialization-json 解析學校端 JSON
- DataStore Preferences 與 AndroidX Security Crypto 保存短期 session 資料
- MockWebServer、JUnit、Compose UI Test 做測試

## 專案設定

- `applicationId`: `com.clhs.score`
- namespace: `com.clhs.score`
- minSdk: 29
- targetSdk: 36
- compileSdk: 36
- Android Gradle Plugin: 9.0.0
- Gradle wrapper: 9.3.1
- Compose BOM: 2026.03.00


## 功能

- 登入頁：帳號、密碼、驗證碼圖片、刷新驗證碼、登入狀態與錯誤提示
- 成績查詢：年學期與考試選擇，登入後直連學校 API 取得成績
- 總覽：摘要卡、重點解讀、本地推估洞察、快速入口、強弱科摘要
- 科目：精簡科目卡，點擊後展開五標落點、分佈摘要與上一考比較
- 進階：雷達分析、成績比較、五標分析、分數分布
- 歷次趨勢：同學期目前考試往前抓兩考，背景載入，不阻塞本次成績顯示
- 登出：清除 encrypted session、cookie 與 token

## 資料與安全策略

- App 不保存密碼。
- 登入成功後只保存短期 cookies、studentNo 與 apiToken。
- 登出或 session 過期時清除本機 session。
- Manifest 僅需要 `INTERNET` 權限。
- 禁止 cleartext traffic，只使用 HTTPS。


## 架構

```text
Compose UI
  -> ScoreViewModel
  -> GradeRepository
  -> SchoolGradeClient
  -> school system
```

主要模組：

- `data/SchoolGradeClient.kt`: 集中處理學校登入、captcha、token、成績 API 流程
- `data/GradeRepository.kt`: session restore、login、logout、structure loading、grade fetching
- `data/GradeAnalysis.kt`: 成績分析、上一考比較、近三次趨勢、本地洞察與排名粗估
- `viewmodel/ScoreViewModel.kt`: UI state、背景比較與趨勢載入
- `ui/`: Login、結果頁、圖表與 Material 3 theme

## 建置與測試

在 Windows PowerShell：

```powershell
cd android
.\gradlew.bat test
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat assembleDebug
```

debug APK 產物：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

有 emulator 或實機連線時可再跑：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## 安裝到實機

確認手機開啟 USB debugging 並已授權：

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
```

安裝 debug APK：

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r .\app\build\outputs\apk\debug\app-debug.apk
```

