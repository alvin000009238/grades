# 專案無停機部署 (Zero-Downtime Deployment) 與 CI/CD 指南

本專案採用 **GitHub Actions** 進行 CI/CD 流水線配置，並在 VPS 上使用 **Docker Swarm (單節點模式)** 達成無停機滾動更新（Rolling Update）。

---

## 🚀 階段一：VPS 環境初始化設定

為了讓 `start-first` 滾動更新生效，我們必須將 Docker 轉換為 Swarm 模式。您只需要在 VPS 上執行一次：

### 1. 安裝 Docker
```bash
# 更新系統
sudo apt update && sudo apt upgrade -y

# 安裝 Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# 將目前使用者加入 docker 群組
sudo usermod -aG docker $USER
newgrp docker

# 驗證安裝
docker --version
```

### 2. 啟用 Docker Swarm
登入您的 VPS 並執行：
```bash
docker swarm init
```
*(如果 VPS 有多張網卡，系統可能會提示您指定 `--advertise-addr`)*

### 3. 取得專案程式碼
```bash
git clone <您的儲存庫 URL> school_grades
cd school_grades/school_grades
```

### 4. 環境變數準備

> [!CAUTION]
> Docker Swarm 的 `docker stack deploy` **不會自動讀取 `.env` 檔案**。
> 您必須在執行部署指令前，手動 `export` 所有環境變數。

在專案目錄下建立 `.env` 檔案以便管理（確保它**不會被提交到 Git**）：
```env
GHCR_IMAGE=您的GitHub帳號/school_grades
SECRET_KEY=您的Flask_Secret_Key
TUNNEL_TOKEN=您的Cloudflare_Tunnel_Token
```

### 5. 首次部署

首次部署需使用 `docker stack deploy`，**之後的日常更新由 CI/CD 自動處理**：

```bash
# 先將 .env 的變數載入到 Shell 環境
export $(grep -v '^#' .env | xargs)

# 登入 GHCR
echo "<您的 GHCR_PAT>" | docker login ghcr.io -u <您的GitHub帳號> --password-stdin

# 部署
docker stack deploy --with-registry-auth -c docker-compose.yml school_grades
```

驗證服務狀態：
```bash
docker service ls
docker service ps school_grades_app
docker service ps school_grades_tunnel
```

---

## 🔐 階段二：GitHub Secrets 設定

要讓 GitHub Actions 能夠順利拉取並部署您的程式碼，請前往 GitHub 專案的 **Settings -> Secrets and variables -> Actions** 中新增以下 Secrets：

| Secret 變數名稱 | 說明 / 注意事項 |
| --- | --- |
| `VPS_HOST` | VPS 的 IP 位址。 |
| `VPS_PORT` | SSH 的 Port，預設通常是 `22`。 |
| `VPS_USERNAME` | 登入 VPS 的帳號名稱（如 `root` 或 `ubuntu`）。 |
| `VPS_SSH_KEY` | 用來登入 VPS 的 SSH 私鑰（Private Key，通常是 `~/.ssh/id_rsa` 的內容）。 |
| `VPS_APP_PATH` | 專案在 VPS 上的絕對路徑（例如 `/home/ubuntu/school_grades`），以便 Action 進入該目錄執行指令。 |
| `GHCR_PAT` | 個人存取權杖 (Personal Access Token, Classic 即可)，須勾選 `read:packages` 權限供 VPS 從 GHCR 拉取映像檔。 |

*(請確保 `VPS_APP_PATH` 目錄內已經有 `git clone` 過您的 repo 並且切換到 `main` 分支)*

---

## 🔄 日常部署流程

每次 Push 至 `main` 分支時，GitHub Actions 會自動：
1. 建置 Docker Image
2. 以 **Commit SHA** 為 tag 推送至 GHCR（同時也推送 `latest`）
3. SSH 至 VPS 執行 `docker service update --image ghcr.io/<repo>:sha-<commit>` 更新 app 服務

> [!IMPORTANT]
> CI/CD 使用明確的 **SHA tag** 而非 `latest` 來觸發更新。
> 這確保每次部署都會產生真正的 Rolling Update，避免 Swarm 因 image 字串未變而跳過更新。

---

## 🔧 服務監控與除錯

### 查看服務狀態
```bash
docker service ls                           # 所有服務概覽
docker service ps school_grades_app         # App 詳細狀態
docker service ps school_grades_tunnel      # Tunnel 詳細狀態
```

### 查看 Logs
```bash
docker service logs school_grades_app       # App 日誌
docker service logs school_grades_tunnel    # Tunnel 日誌
```

### Tunnel 顯示 0/1 的常見原因
- `TUNNEL_TOKEN` 環境變數未正確注入（首次部署前忘了 `export`）
- Token 值不正確或已過期
- 使用 `docker service logs school_grades_tunnel` 查看具體錯誤訊息

---

## ⏪ 回滾 (Rollback)

### 自動回滾（健康檢查失敗）
如果新版的 `/health` 端點無法通過檢查（回傳非 200），Docker Swarm 最多重試 3 次後會觸發 `failure_action: rollback`，**自動退回前一個穩定版本**，全程無停機。

### 手動回滾（業務邏輯瑕疵）
如果新版部署成功但發現 Bug，使用特定的 Commit SHA 進行無停機回滾：
```bash
docker service update \
  --image ghcr.io/<您的GitHub帳號>/school_grades:sha-<退回的Commit_SHA> \
  school_grades_app
```
*(請將 `<您的GitHub帳號>` 與 `<退回的Commit_SHA>` 替換為實際數值)*
