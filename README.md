# 成績分析平台 - 中大壢中


[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)


> [!IMPORTANT]
> 本專案為非官方開發之第三方服務，我們與壢中及欣河智慧校園平台無任何直接關聯。

本專案實作了自動化的成績查詢流程，全程透過原生的 HTTP 請求（使用 Python `requests` 模組）與學校系統的 API 介接，免去開啟實際瀏覽器的負擔。由於摒棄了複雜的網站前端渲染，它能提供非常快速且輕量化的連線體驗，並支援取得成績後建立分享連結的功能。

## Table of Contents

- [Background](#background)
- [Install](#install)
- [Usage](#usage)
- [Maintainers](#maintainers)
- [License](#license)

## Background

此專案開發初衷是為了解決複雜的成績查詢手續。應用程式負責打理自動登入驗證與建立 session，並藉由輕量型 API 接口處理後續的網路請求。這使得使用者能夠透過更為友善直觀的介面，快速查詢個人成績並與他人分享。

## Install

本專案**僅提供本地開發及部署環境教學**。你需要 Python 3.10+ 版本環境才能執行此專案。

### 1. 取得專案原始碼

```bash
git clone https://github.com/alvin000009238/grades.git
cd grades
```

### 2. 環境變數設定

此專案需要設定特定的環境變數，請參考 `.env.example` 建立你自己的 `.env` 檔案。

Linux / macOS:
```bash
cp .env.example .env
```

Windows (PowerShell):
```powershell
Copy-Item .env.example .env
```

編輯 `.env`，設定例如 `SECRET_KEY` 的必要變數。

### 3. 設定 Python 虛擬環境並安裝依賴

強烈建議使用 [venv](https://docs.python.org/3/library/venv.html) 將本專案的依賴隔離於你的全域 Python 環境中：

```bash
# 建立虛擬環境
python -m venv venv

# 啟動虛擬環境 (Linux/macOS)
source venv/bin/activate

# 啟動虛擬環境 (Windows PowerShell)
.\venv\Scripts\Activate.ps1
```

接著安裝必要的 Python 套件：

```bash
# 安裝所有相依套件
pip install -r requirements.txt
```

## Usage

確保已經處於虛擬環境中（終端機介面前有 `(venv)` 標示）並且所有依賴都已正確安裝。

啟動 Flask 開發伺服器：

```bash
python server.py
```

在你的瀏覽器中開啟 `http://127.0.0.1:5000` 來使用此應用程式。

> [!WARNING]
> 本應用預設以 `debug=True` 運行於 Flask 的內建伺服器（Development Server），這並不適合直接暴露於公共網路環境，也不適用於生產模式部署。


## Architecture

完整系統架構（C4）、前後端溝通流程與 API 規格請參考 [`ARCHITECTURE.md`](ARCHITECTURE.md)。

## Maintainers

[@alvin000009238](https://github.com/alvin000009238)

## License

[MIT](LICENSE) © 2026 alvin000009238
