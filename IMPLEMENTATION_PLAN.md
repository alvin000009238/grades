# 登出與重載功能實作計畫

本計畫旨在為成績查詢系統增加登出與重載考試結構的功能，並防止使用者頻繁重載。

## 已完成項目

### 1. 後端 API (`server.py`)
- **新增 `/api/logout` 端點**
  - 收到 POST 請求時，清除 session。
  - 回傳成功訊息。
- **更新 `/api/structure` 端點**
  - 支援 `reload=true` 查詢參數。
  - 若 `reload=true`，則忽略 session 中的快取，強制重新呼叫 `GradeFetcher` 抓取資料。

### 2. 前端介面 (`index.html`)
- **更新選擇考試視窗 (`selectExamModal`)**
  - 在 Modal Footer 加入左側按鈕群組。
  - 新增「登出」按鈕 (`#logoutBtn`)，樣式設為危險操作 (紅色)。
  - 新增「重載」按鈕 (`#reloadStructureBtn`)，樣式設為次要操作 (藍色)。

### 3. 前端樣式 (`grades_dashboard.css`)
- **新增按鈕樣式**
  - `.modal-btn.reload`: 藍色背景，支援 disabled 狀態與 hover 效果。
  - `.modal-btn.danger`: 紅色背景，用於登出等危險操作。

### 4. 前端邏輯 (`grades_dashboard.js`)
- **登出功能**
  - 點擊按鈕後彈出確認視窗。
  - 確認後呼叫 `/api/logout`。
  - 成功後重新整理頁面 (`location.reload()`) 以清除前端狀態。
- **重載功能**
  - 設定 60 秒 (1分鐘) 冷卻時間。
  - 使用 `localStorage` 記錄上次重載時間 (`lastReloadTime`)，確保重新整理後冷卻時間仍有效。
  - 點擊按鈕時：
    - 更新 `lastReloadTime`。
    - 呼叫 `openSelectExamModal(true)`，傳入 `true` 參數以強制重載。
  - **冷卻倒數機制**
    - 開啟 Modal 時檢查冷卻狀態。
    - 若在冷卻中，按鈕設為 disable 並顯示倒數秒數。
    - 使用 `setInterval` 每秒更新顯示，Modal 關閉時清除 interval。
