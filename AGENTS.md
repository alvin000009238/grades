# AGENTS.md

本檔給 AI agent 在此 repo 工作時使用。請先讀實際程式碼再改動，避免依照通用模板猜測架構。

## 專案結構

- `app/` 與 `server.py`：Flask 後端。`app.create_app()` 是主要 app factory，`server.py` 只負責本機啟動。
- `frontend/`：Vite 前端原始碼，進入點是 `frontend/main.js`，樣式拆在 `frontend/styles/`。
- `public/`：Flask 服務的靜態入口與 Vite build 輸出位置。
- `android/`：獨立 Kotlin / Jetpack Compose / Material 3 Android app，package 為 `com.clhs.score`。
- `tests/`：後端 pytest 與前端 Node 測試。

## 工作規則

- 優先保留既有資料流程與檔案分層。UI 改版請先找現有 screen、theme、chart 元件，不要另建平行 app。
- 不要把帳密、token、cookie 或正式環境 secret 寫進程式碼或對話；使用 `.env`、local properties 或本機設定檔。
- 此 repo 的 Markdown 預設會被 `.gitignore` 忽略；新增或更新 agent 文件後要確認 `AGENTS.md` 沒有被 ignore。
- 文件預設使用繁體中文；程式碼註解只在能降低理解成本時加入。

## 常用驗證

- 後端：`pytest tests/backend/`
- Python 語法：`python -m compileall app fetcher.py server.py`
- 前端：`npm run test`、`npm run build`
- Android：在 `android/` 內執行 `.\gradlew.bat test`

在 Windows Codex 環境跑 Android Gradle 時，若 `java` 不在 PATH，使用 Android Studio 內建 JBR，並將 `GRADLE_USER_HOME`、`ANDROID_USER_HOME` 指到 workspace 內的暫存目錄。若測試一開始就出現 `could not open ...\jbr\lib\jvm.cfg`，通常是設定的 Android Studio JBR 路徑不存在或不完整；先用 `Test-Path` 或列出 `C:\Program Files\Android\Android Studio*` 確認實際 JBR 位置。本機曾遇到 `C:\Program Files\Android\Android Studio\jbr` 不可用，而 `C:\Program Files\Android\Android Studio1\jbr` 可用。

## Android release

- 推送 `v*` tag 會觸發 `.github/workflows/android-release.yml`，建立 signed `arm64-v8a` release APK 並發布 GitHub Release。
- Release notes 來自 `CHANGELOG.md` 內與 tag 對應的 `## [x.y.z]` 區塊；新增版本時要先補 changelog。
- GitHub Secrets 需設定 `ANDROID_RELEASE_KEYSTORE_BASE64`、`ANDROID_RELEASE_KEYSTORE_PASSWORD`、`ANDROID_RELEASE_KEY_ALIAS`、`ANDROID_RELEASE_KEY_PASSWORD`。不要提交 keystore 或密碼。
