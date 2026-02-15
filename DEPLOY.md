# School Grades VPS 部署指南

這個指南將協助你將 School Grades 應用程式部署到 VPS (Virtual Private Server) 上。

## 1. 準備工作

### 必備條件
- 一台 VPS (例如: DigitalOcean, Linode, AWS EC2, Google Cloud Compute Engine 等)。
- VPS 作業系統建議使用 Ubuntu 22.04 LTS 或更高版本。
- 擁有該 VPS 的 SSH 存取權限。

### 在 VPS 上安裝 Docker

SSH 連線到你的 VPS 後，執行以下指令安裝 Docker 和 Docker Compose：

```bash
# 更新系統
sudo apt update
sudo apt upgrade -y

# 安裝 Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# 將目前使用者加入 docker 群組 (避免每次都要 sudo)
sudo usermod -aG docker $USER

# 讓群組變更生效 (或者重新登入 SSH)
newgrp docker

# 驗證安裝
docker --version
docker compose version
```

## 2. 上傳專案檔案

你需要將專案檔案上傳到 VPS。你可以使用 `scp` (Secure Copy) 或 `git`。

### 方法 A: 使用 Git (推薦)
如果你的專案在 GitHub/GitLab 上：

```bash
git clone <你的儲存庫 URL> school_grades
cd school_grades/school_grades
```
*注意：確保 [Dockerfile](file:///c:/Users/USER/Desktop/project/school_grades/school_grades/Dockerfile), [docker-compose.yml](file:///c:/Users/USER/Desktop/project/school_grades/school_grades/docker-compose.yml), [requirements.txt](file:///c:/Users/USER/Desktop/project/school_grades/school_grades/requirements.txt) 和程式碼都在目錄中。*

### 方法 B: 使用 SCP (從本機上傳)
在你的**本機電腦** (Windows PowerShell 或 CMD) 執行：

```powershell
# 假設你的專案路徑是 c:\Users\USER\Desktop\project\school_grades
scp -r c:\Users\USER\Desktop\project\school_grades\school_grades user@<VPS_IP>:/home/user/school_grades
```

## 3. 設定環境變數

在 VPS 的專案目錄中，建立 [.env](file:///c:/Users/USER/Desktop/project/school_grades/school_grades/.env) 檔案來設定環境變數：

```bash
# 進入專案目錄
cd school_grades

# 建立 .env 檔案
nano .env
```

貼上以下內容 (請自行修改 SECRET_KEY)：

```env
# Flask Secret Key (請使用亂數產生，越長越好)
SECRET_KEY=your-super-secret-key-change-this

# Cloudflare Tunnel Token (如果你要使用 Cloudflare Tunnel)
# TUNNEL_TOKEN=eyJhIjoi...
```

按 `Ctrl+O` 儲存，`Enter` 確認，然後 `Ctrl+X` 離開。

## 4. 啟動服務

使用 Docker Compose 啟動服務：

```bash
docker compose up -d --build
```

- `-d`: 在背景執行 (Detached mode)。
- `--build`: 強制重新建置映像檔 (確保使用最新的程式碼)。

查看服務狀態：
```bash
docker compose ps
```

查看日誌 (Logs)：
```bash
docker compose logs -f app
```

## 5. 公開存取

### 方法 A: 使用 Cloudflare Tunnel (最安全、免開 Port)
如果你的 [docker-compose.yml](file:///c:/Users/USER/Desktop/project/school_grades/school_grades/docker-compose.yml) 中已經有 `tunnel` 服務，且你在 [.env](file:///c:/Users/USER/Desktop/project/school_grades/school_grades/.env) 中設定了 `TUNNEL_TOKEN`，則應用程式應該已經可以透過 Cloudflare 網域存取。

### 方法 B: 直接使用 IP + Port (測試用)
預設情況下，[docker-compose.yml](file:///c:/Users/USER/Desktop/project/school_grades/school_grades/docker-compose.yml) 將 Port `5000` 對應到 Host 的 `5000`。
你可以在瀏覽器輸入 `http://<VPS_IP>:5000` 來存取。

> **注意**：如果無法存取，請檢查 VPS 的防火牆設定 (UFW 或雲端服務商的安全群組)，確保 Port 5000 是開放的。

```bash
# 允許 Port 5000 (如果有啟用 UFW)
sudo ufw allow 5000
```

### 方法 C: 使用 Nginx 反向代理 (標準做法)
安裝 Nginx 並設定反向代理，將 80/443 Port 的流量轉發到 5000 Port。

1. 安裝 Nginx: `sudo apt install nginx`
2. 設定 Nginx Config (略，可參考 Nginx 文件)。

## 常見問題

### Q: 為什麼抓取成績失敗？
檢查日誌 (`docker compose logs -f app`)。如果是 Playwright 相關錯誤，可能是記憶體不足。Playwright 瀏覽器比較吃記憶體，建議 VPS 至少有 1GB RAM (最好 2GB)。

### Q: 如何更新程式？
1. `git pull` (或重新上傳檔案)
2. `docker compose up -d --build` (Docker 會偵測變更並重建)
