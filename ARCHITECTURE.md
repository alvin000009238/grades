# 成績分析平台架構與 API 規格

本文件整理專案的完整系統架構，包含：

- C4 Model（Context / Container / Component）
- 前端與後端溝通流程
- 後端與外部校務系統 API 溝通流程
- REST API 規格（請求/回應/錯誤碼）
- 資料與安全設計

---

## 1. C4 - Context（系統脈絡）

### 1.1 參與者與外部系統

- **使用者（學生）**
  - 透過瀏覽器操作前端頁面，登入、查詢成績、建立分享連結。
- **成績分析平台（本系統）**
  - 由 Flask 後端 + 靜態前端組成。
- **外部校務系統（欣河智慧校園）**
  - 本系統透過 Python `requests` 進行登入與資料查詢。
- **Cloudflare Turnstile**
  - 提供人機驗證，套用於登入與建立分享連結。

### 1.2 Context 流程

1. 使用者在瀏覽器載入網站。
2. 前端向本系統取得 Turnstile site key 並初始化驗證元件。
3. 使用者登入時，前端送帳密 + Turnstile token 給後端。
4. 後端代為登入外部校務系統，取得 cookies/token 並建立 session。
5. 前端呼叫 API 取學年/考試結構與實際成績。
6. 後端向外部校務系統查資料後回傳 JSON，前端渲染儀表板與圖表。
7. 使用者可建立分享連結，後端儲存成績快照 JSON，供他人唯讀查看。

---

## 2. C4 - Container（容器層）

### 2.1 前端容器（Browser + Static Files）

- 主要檔案：`public/index.html`, `public/app.js`, `public/style.css`
- 技術：原生 HTML/CSS/JavaScript + Chart.js
- 功能：
  - 儀表板渲染
  - 登入/查詢/登出
  - 分享建立與分享頁唯讀模式
  - 本地快取（`localStorage`）

### 2.2 後端容器（Flask API）

- 主要檔案：`server.py`
- 技術：Flask, flask-cors
- 功能：
  - 提供靜態資源
  - Session 管理（儲存 api cookies/token/student_no）
  - Turnstile 驗證
  - 成績查詢 API
  - 分享資料讀寫與清理

### 2.3 外部整合容器（Fetcher）

- 主要檔案：`fetcher.py`
- 技術：requests, BeautifulSoup, ThreadPoolExecutor
- 功能：
  - 模擬校務系統登入
  - 擷取 `__RequestVerificationToken`
  - 呼叫學年/考試/成績 API

### 2.4 部署容器

- `Dockerfile`：以 `python:3.11-slim` 建 image，使用 gunicorn 啟動。
- `docker-compose.yml`：
  - `app`：本系統
  - `tunnel`：cloudflared，做對外通道
  - healthcheck：`/health`

---

## 3. C4 - Component（元件層）

## 3.1 前端元件（`public/app.js`）

- **GlobalTurnstileManager**
  - 初始化 Turnstile（拿 site key、render invisible widget）
  - 提供 `getToken()` / `reset()`
- **Sync 模組（setupSyncFeature）**
  - 登入流程 `/api/login`
  - 取得結構 `/api/structure`
  - 查詢成績 `/api/fetch`
  - 登出 `/api/logout`
- **Share 模組（setupShareFeature / checkSharedLink）**
  - 建立分享 `/api/share`
  - 載入分享 `/api/share/<id>`
- **Dashboard Render 模組**
  - `initDashboard`, `updateStudentInfo`, `updateRankInfo`, `generateCharts` 等

## 3.2 後端元件（`server.py`）

- **HTTP 路由層**
  - `/`、`/<path:filename>` 靜態檔服務
  - `/api/*` REST API
- **Turnstile 驗證器（verify_turnstile）**
  - 驗證 token，統一回傳 `(success, error_response)`
- **Session 狀態管理**
  - `api_cookies`, `api_token`, `student_no`, `structure`
- **分享資料管理**
  - 寫入 `shared_grades/<id>.json`
  - 背景執行緒定時清理過期分享檔
- **資料抓取委派器**
  - 透過 `GradeFetcher` 與外部校務系統通訊

## 3.3 抓取元件（`fetcher.py`）

- **login_and_get_tokens**：登入 + 取 cookies + API token
- **get_structure_via_api**：查詢可用學年/學期，並平行查詢每個學期可用考試
- **get_exams_via_api**：依年期取 exam list
- **fetch_grades_via_api**：依 year/term/exam 查詢成績

---

## 4. 前端 ↔ 後端 溝通一覽（完整）

> 前端主要使用 `fetch` 呼叫 `/api/*`，登入與查詢流程採 session cookie。

| API | Method | 前端呼叫點 | 主要用途 | 是否需登入 |
|---|---|---|---|---|
| `/api/turnstile-site-key` | GET | GlobalTurnstileManager.init | 取得 Turnstile site key | 否 |
| `/api/login` | POST | handleLogin | 登入（帳密 + turnstile） | 否 |
| `/api/structure` | GET | openSelectExamModal | 取得學年/考試結構 | 是 |
| `/api/fetch` | POST | confirmFetch | 取得指定考試成績 | 是 |
| `/api/logout` | POST | logoutBtn click | 清除 session | 是 |
| `/api/upload` | POST | （目前前端未主流程使用） | 匯入 JSON 成績資料 | 否 |
| `/api/share` | POST | createLinkBtn click | 建立分享連結 | 否（但需 Turnstile） |
| `/api/share/<share_id>` | GET | checkSharedLink | 載入分享資料 | 否 |
| `/api/check_login` | GET | （可供擴充） | 檢查 session 登入狀態 | 否 |
| `/health` | GET | 健康檢查 | 容器健康監控 | 否 |

---

## 5. 後端 ↔ 外部校務系統 溝通流程（完整）

### 5.1 登入流程

1. `GET CloudLogin`：拿 hidden token（`__RequestVerificationToken`）。
2. `POST DoCloudLoginCheck`：送帳密 + token。
3. 成功後 `GET StudentInfo/Index?page=成績查詢`：再拿 API token。
4. 整理 session cookies（dict）與 `student_no`。

### 5.2 結構與成績查詢

- `POST GetGradeCanQueryYearTermListByStudentNo`：取學年學期。
- 對每個學年學期，平行呼叫：
  - `POST GetGradeCanQueryExamNoListByStudentNo`：取可查考試。
- `POST GetScoreForStudentExamContent`：查詢指定考試完整成績。

---

## 6. API 規格（詳細）

## 6.1 `GET /api/turnstile-site-key`

- **用途**：提供前端初始化 Turnstile 的 site key。
- **回應**：

```json
{ "siteKey": "..." }
```

---

## 6.2 `POST /api/login`

- **用途**：登入並建立 session。
- **Request Body**：

```json
{
  "username": "學號",
  "password": "密碼",
  "cf-turnstile-response": "token"
}
```

- **成功回應**（200）：

```json
{ "success": true, "message": "登入成功" }
```

- **失敗回應**：
  - `400`：缺帳密或缺 Turnstile token
  - `401`：帳密錯誤或校務系統拒絕
  - `403`：Turnstile 驗證失敗
  - `500`：系統錯誤 / 驗證服務錯誤

---

## 6.3 `GET /api/structure[?reload=true]`

- **用途**：取得學年與考試結構（優先用 session cache）。
- **成功回應**（200）：

```json
{
  "structure": {
    "113學年度上學期": {
      "year_value": "113_1",
      "exams": [
        { "text": "第一次段考", "value": "1" }
      ]
    }
  }
}
```

- **失敗回應**：
  - `401`：未登入或 session 過期
  - `500`：外部 API 錯誤

---

## 6.4 `POST /api/fetch`

- **用途**：依學期與考試抓成績。
- **Request Body**：

```json
{
  "year_value": "113_1",
  "exam_value": "1"
}
```

- **成功回應**（200）：

```json
{
  "success": true,
  "message": "成績已更新",
  "data": { "Result": { "SubjectExamInfoList": [] } }
}
```

- **失敗回應**：
  - `401`：未登入
  - `500`：外部 API 錯誤

---

## 6.5 `POST /api/logout`

- **用途**：清除 session。
- **成功回應**：

```json
{ "success": true, "message": "已登出" }
```

---

## 6.6 `POST /api/upload`

- **用途**：上傳 JSON 檔或直接送 JSON 成績資料。
- **支援格式**：
  - multipart file（`file`）
  - `application/json`
- **校驗**：需包含 `Result` 欄位。

- **成功回應**：

```json
{
  "success": true,
  "data": { "Result": {} }
}
```

- **失敗回應**：
  - `400`：格式錯誤或缺欄位
  - `500`：解析錯誤

---

## 6.7 `POST /api/share`

- **用途**：建立分享連結（儲存資料快照）。
- **Request Body**：
  - 任意成績 JSON + `cf-turnstile-response`
- **成功回應**：

```json
{ "success": true, "id": "15字元分享ID" }
```

- **失敗回應**：
  - `400`：未提供資料或缺驗證
  - `403`：Turnstile 驗證失敗
  - `500`：儲存失敗

---

## 6.8 `GET /api/share/<share_id>`

- **用途**：取得分享資料。
- **成功回應**：

```json
{
  "success": true,
  "data": { "Result": {} }
}
```

- **失敗回應**：
  - `400`：ID 格式錯誤
  - `404`：連結過期或不存在
  - `500`：讀取失敗

---

## 6.9 `GET /health`

- **用途**：服務健康檢查。
- **回應**：

```json
{ "status": "ok" }
```

---

## 7. Session 與資料流

### 7.1 Session 內容

- `username`
- `api_cookies`
- `student_no`
- `api_token`
- `structure`（快取）

### 7.2 前端快取

- `localStorage['gradesData']`：存最近一次成績 JSON。

### 7.3 分享資料儲存

- 路徑：`shared_grades/<share_id>.json`
- 生命週期：2 小時
- 清理週期：每 10 分鐘背景執行緒掃描刪除

---

## 8. 安全與可靠性設計

- **Turnstile 驗證**：登入與分享建立皆可驗證機器人。
- **Session Cookie 設定**：
  - `HttpOnly=True`
  - `Secure=True`
  - `SameSite=Lax`
- **ProxyFix**：信任反向代理 header（部署於代理後方時）。
- **靜態檔白名單**：限制可下載副檔名，阻擋 dotfiles。
- **檔案大小限制**：`MAX_CONTENT_LENGTH = 2MB`。
- **Log**：RotatingFileHandler，避免單一 log 無限增長。

---

## 9. 可改善建議（架構層）

1. 將 API 路由與服務層拆分（Blueprint + service module），提升可維護性。
2. 為外部 API 請求加上 retry/backoff 與 timeout 統一策略。
3. 將 share 資料由檔案系統改為 Redis/DB，支援多實例與更穩定過期管理。
4. 將 `verify=False` 改為正規 TLS 驗證（若外部系統允許）。
5. 將 `/api/upload` 與主流程整合或明確標示用途，避免功能歧義。

