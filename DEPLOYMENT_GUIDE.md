# 專案無停機部署 (Zero-Downtime Deployment) 與 CI/CD 指南

本專案採用 **GitHub Actions** 進行 CI/CD 流水線配置，並在 VPS 上使用 **Docker Swarm (單節點模式)** 達成無停機滾動更新（Rolling Update）。

---

## 🚀 階段一：VPS 環境初始化設定

為了讓 `start-first` 滾動更新生效，我們必須將 Docker 轉換為 Swarm 模式。您只需要在 VPS 上執行一次：

### 1. 啟用 Docker Swarm
登入您的 VPS 並執行：
```bash
docker swarm init
```
*(如果 VPS 有多張網卡，系統可能會提示您指定 `--advertise-addr`)*

### 2. 環境變數 (.env) 準備
在您的專案目錄（例如 `~/school_grades`）下建立 `.env` 檔案以存放敏感資訊，確保它們**沒有被提交到 Git 儲存庫**：
```env
GHCR_IMAGE=您的GitHub帳號/school_grades
SECRET_KEY=您的Flask_Secret_Key
TURNSTILE_SECRET_KEY=您的Cloudflare_Turnstile_Secret
TUNNEL_TOKEN=您的Cloudflare_Tunnel_Token
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

## 🔄 日常部署與回滾 (Rollback) 流程

每次 Push 至 `main` 分支時，GitHub Actions 會自動執行建置、推送 Image 至 GHCR 並觸發 VPS 更新服務。

### 服務監控與管理
您可以透過以下指令查看服務運行狀態：
- 查看運作中的 Services：`docker service ls`
- 查看 Web App 的詳細狀態與 Healthcheck：`docker service ps school_grades_app`
- 查看服務 Logs：`docker service logs school_grades_app`

### 如何進行回滾 (Rollback)

我們在推播 Image 到 GHCR 時，不僅加上了 `latest` 標籤，還會加上 GitHub 的 **Commit SHA** 標籤。

**1. 自動回滾（健康檢查失敗）**
如果在 CI/CD 更新後，新版的 API 或系統導致 `/health` 端點檢查無法通過 (回傳非 200)，Docker Swarm 最多重試 3 次，隨後會觸發 `failure_action: rollback`，**自動將系統退回前一個穩定的版本**，全程不會有任何停機斷線。

**2. 手動回滾（業務邏輯瑕疵）**
如果系統部署成功且 Healthcheck 也通過了，但您發現了業務邏輯上的 Bugs 而想要緊急降版，您只需使用特定的 Commit SHA (例如 `5a2b3c4`) 對服務進行強制更新。這同樣會**以無停機的方式滾動更新到舊版本**：
```bash
docker service update --image ghcr.io/<您的GitHub帳號>/school_grades:sha-<退回的Commit_SHA> school_grades_app
```
*(請將 `<您的GitHub帳號>` 與 `<退回的Commit_SHA>` 替換為實際數值)*
