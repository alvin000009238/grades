import json
import time
from playwright.sync_api import sync_playwright

STATE = "state.json"
GRADES_URL = "https://shcloud2.k12ea.gov.tw/CLHSTYC/ICampus/StudentInfo/Index?page=%E6%88%90%E7%B8%BE%E6%9F%A5%E8%A9%A2"
API_URL = "https://shcloud2.k12ea.gov.tw/CLHSTYC/ICampus/TutorShGrade/GetScoreForStudentExamContent"

TAB = 'div.tabSwitch[data-tab="單次考試所有成績"]'
BTN_QUERY = "button:has-text('查詢')"

# Kendo 下拉控制項
YEAR_DROPDOWN = 'span.k-select[aria-controls="gradeYearTermFor單次考試所有成績_listbox"]'
YEAR_LISTBOX  = '#gradeYearTermFor單次考試所有成績_listbox'

EXAM_DROPDOWN = 'span.k-select[aria-controls="gradeExam_listbox"]'
EXAM_LISTBOX  = '#gradeExam_listbox'

def get_options(page, dropdown_selector, listbox_selector):
    """
    點擊 dropdown，等待 listbox 出現，然後回傳所有選項的文字列表。
    """
    page.click(dropdown_selector)
    page.wait_for_selector(listbox_selector, state="visible")
    
    # 抓取 listbox 內的所有選項 (Kendo UI 通常使用 .k-item)
    items = page.locator(f"{listbox_selector} .k-item").all()
    if not items:
        items = page.locator(f"{listbox_selector} li").all()
    
    options = [item.text_content().strip() for item in items]
    
    # 點擊第一個選項或是空白處來關閉選單 (如果不關閉可能會遮擋)
    if items:
        items[0].click()
    
    return options

def select_option(page, dropdown_selector, listbox_selector, text):
    """
    選擇特定的選項
    """
    page.click(dropdown_selector)
    page.wait_for_selector(listbox_selector, state="visible")
    # 使用 Playwright 的 text selector 精確定位
    page.click(f'{listbox_selector} >> li:has-text("{text}")')

with sync_playwright() as p:
    print("啟動瀏覽器...")
    browser = p.chromium.launch(headless=True, slow_mo=100)
    ctx = browser.new_context(storage_state=STATE)
    page = ctx.new_page()

    print(f"前往 {GRADES_URL} ...")
    page.goto(GRADES_URL, wait_until="networkidle")

    # 1️⃣ 切到「單次考試所有成績」
    print("切換到「單次考試所有成績」頁籤...")
    page.wait_for_selector(TAB)
    page.click(TAB)

    # 給一點緩衝時間讓 Kendo UI 初始化
    time.sleep(1)

    # 2️⃣ 掃描所有「學年度」與「考試」組合
    print("正在掃描可用的學年度與考試...")
    
    combinations = []
    
    # 取得所有學年度
    years = get_options(page, YEAR_DROPDOWN, YEAR_LISTBOX)
    
    for year in years:
        # 選取該學年度
        select_option(page, YEAR_DROPDOWN, YEAR_LISTBOX, year)
        
        # 等待考試選單更新 (可能有 AJAX) - 這裡用簡單的 sleep 或是 wait_for_response 更好
        # 因為不確定後端是否會發 request，保險起見睡一下
        time.sleep(1) # 等待連動
        
        # 取得該學年度下的所有考試
        exams = get_options(page, EXAM_DROPDOWN, EXAM_LISTBOX)
        
        for exam in exams:
            combinations.append((year, exam))
            
    print("\n抓取到的所有組合：")
    for i, (y, e) in enumerate(combinations):
        print(f"{i+1}. {y} - {e}")
        
    if not combinations:
        print("沒有找到任何可用的考試組合。")
        browser.close()
        exit()

    # 3️⃣ 讓使用者選擇
    while True:
        try:
            choice = input("\n請輸入編號選擇 (例如 1): ")
            idx = int(choice) - 1
            if 0 <= idx < len(combinations):
                selected_year, selected_exam = combinations[idx]
                break
            else:
                print("輸入無效，請重新輸入。")
        except ValueError:
            print("請輸入數字。")

    print(f"\n您選擇了：{selected_year} - {selected_exam}")

    # 4️⃣ 執行查詢
    select_option(page, YEAR_DROPDOWN, YEAR_LISTBOX, selected_year)
    time.sleep(0.5) # 等待連動
    select_option(page, EXAM_DROPDOWN, EXAM_LISTBOX, selected_exam)

    print("點擊查詢並等待結果...")
    # 攔 API 回應
    with page.expect_response(
        lambda r: r.url.startswith(API_URL) and r.status == 200
    ) as rinfo:
        page.click(BTN_QUERY)

    resp = rinfo.value

    # 5️⃣ 儲存 JSON
    
    data = resp.json()
    filename = "grades_raw.json"
    with open(filename, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"✅ 成功！已輸出 {filename}")
        
    # 簡單預覽
    if isinstance(data, list):
        print(f"收到 {len(data)} 筆資料")
    else:
        print("資料型別：", type(data))
            
    

    browser.close()
