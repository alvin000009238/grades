# 實作計畫：使用 Docker 與 Cloudflare Tunnel 發佈至公網

這個計畫旨在協助您將本機執行的成績查詢系統，安全地發佈到公網並綁定您的個人網域名稱。我們將使用 **Cloudflare Tunnel (Zero Trust)** 技術，這是一種無需在路由器上設定連接埠轉發（Port Forwarding）的安全方式。

## 為什麼選擇 Cloudflare Tunnel？
1.  **安全性高**：不需要在路由器打開任何連接埠（如 Port 80/443），避免家庭網路直接暴露。
2.  **自動 SSL**：Cloudflare 會自動處理 HTTPS 憑證，您的網站將會有安全鎖頭。
3.  **動態 IP 友善**：即使您的家用網路 IP 是浮動的，Tunnel 也會自動連線，不需要設定 DDNS。

## 前置準備
1.  **擁由一個網域名稱**（Domain Name）。
2.  **註冊 Cloudflare 帳號** 並將您的網域 Nameservers 指向 Cloudflare（託管在 Cloudflare）。

## 實作步驟

### 步驟 1: 取得 Cloudflare Tunnel Token
1.  登入 [Cloudflare Zero Trust Dashboard](https://one.dash.cloudflare.com/)。
2.  進入 **Networks** > **Tunnels**。
3.  點擊 **Create a tunnel**。
4.  選擇 **Cloudflared** 作為連接器類型。
5.  輸入 Tunnel 名稱（例如 `school-grades`）並儲存。
6.  在 "Install and run a connector" 頁面，找到 Docker 的指令區塊。
7.  **複製 Token**：您會看到一段指令 `docker run ... --tokenEYJhIjoi....`。請只複製 `--token` 後面那長串的字串（這是您的 `TUNNEL_TOKEN`）。

### 步驟 2: 設定 Public Hostname
1.  在同一個 Tunnel 設定頁面，點擊 **Next**。
2.  選擇您要綁定的網域（例如 `grades.yourdomain.com`）。
3.  在 **Service** 欄位設定：
    *   **Type**: `HTTP`
    *   **URL**: `app:5000` (注意：這裡是 Docker 內部的服務名稱 `app`，不是 `localhost`)
4.  儲存設定。

### 步驟 3: 修改 docker-compose.yml
我們將修改 `docker-compose.yml` 以加入 Cloudflare Tunnel 服務。

```yaml
services:
  app:
    # ... 原有的 app 設定 ...
    restart: unless-stopped

  tunnel:
    image: cloudflare/cloudflared:latest
    restart: unless-stopped
    command: tunnel run
    environment:
      - TUNNEL_TOKEN=${TUNNEL_TOKEN}
```

### 步驟 4: 啟動服務
1.  建立一個 `.env` 檔案，寫入您的 Token：
    ```
    TUNNEL_TOKEN=您的長串Token
    ```
2.  重新啟動 Docker Compose：
    ```bash
    docker-compose up -d
    ```

## 驗證
完成後，您就可以在瀏覽器輸入您設定的網址（如 `https://grades.yourdomain.com`）來存取您的成績查詢系統。
