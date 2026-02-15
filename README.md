# School Grades App (Hybrid Fetching)

這是一個用於查詢學校成績的 Web 應用程式 (Flask + Python)。

## 專案實現：混合式抓取 (Hybrid Fetching)

1.  **Playwright 登入 (Headless Browser)**
    *   僅在使用者登入時啟動 Chromium 瀏覽器。
    *   自動處理登入表單、驗證碼或彈跳視窗。
    *   登入成功後，擷取 `Cookies`、`__RequestVerificationToken` 和 `StudentNo`。

2.  **Requests 資料查詢 (Lightweight API)**
    *   後續所有的資料查詢 (學期結構、成績單) 使用 Python `requests` 套件。
    *   直接對學校後端 API 發送 HTTP POST 請求。

## 環境需求

*   Docker & Docker Compose (推薦)
*   Python 3.10+ (若不使用 Docker)

## 快速開始 (Docker 部署)

這是最推薦的部署方式，已包含所有依賴環境 (Playwright, Python, Flask)。

### 1. Clone 專案
```bash
git clone https://github.com/alvin000009238/grades.git
cd grades
```

### 2. 設定環境變數
請複製 `.env.example` (若有) 或直接建立 `.env` 檔案：
```bash
# .env
SECRET_KEY=your_random_secret_string_here
TZ=Asia/Taipei
```

### 3. 啟動服務
```bash
docker-compose up --build -d
```

### 4. 訪問應用程式
打開瀏覽器訪問 `http://localhost:5000` (或您的 VPS IP)。

## VPS 部署指南

### 前置準備
1.  準備一台 VPS (推薦 Ubuntu 22.04+)。
2.  安裝 Docker 和 Docker Compose。

### 部署步驟
1.  **Clone 程式碼**：
    在 VPS 上執行 `git clone` 下載此專案。

2.  **設定 `.env`**：
    確保 `.env` 檔案存在且包含 `SECRET_KEY` (參考範例)。

3.  **啟動容器**：
    進入專案目錄，執行 `docker-compose up --build -d`。

4.  **設定反向代理 (可選)**：
    建議使用 Nginx 或 Caddy 將 80/443 port 轉發到 `localhost:5000`，並設定 SSL 憑證。

## 開發與除錯

### 本地開發 (無 Docker)
1.  建立虛擬環境：
    ```bash
    python -m venv venv
    source venv/bin/activate  # Windows: venv\Scripts\activate
    ```
2.  安裝依賴：
    ```bash
    pip install -r requirements.txt
    playwright install chromium
    ```
3.  啟動伺服器：
    ```bash
    python server.py
    ```

