import json
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

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False, slow_mo=150)
    ctx = browser.new_context(storage_state=STATE)
    page = ctx.new_page()

    page.goto(GRADES_URL, wait_until="networkidle")

    # 1️⃣ 切到「單次考試所有成績」
    page.wait_for_selector(TAB)
    page.click(TAB)

    # 2️⃣ 選擇「114學年度」
    page.click(YEAR_DROPDOWN)
    page.wait_for_selector(YEAR_LISTBOX)
    page.click(f'{YEAR_LISTBOX} >> text=114學年度')

    # 3️⃣ 選擇「期末考」
    page.click(EXAM_DROPDOWN)
    page.wait_for_selector(EXAM_LISTBOX)
    page.click(f'{EXAM_LISTBOX} >> text=期末考')

    # 4️⃣ 點「查詢」並攔 API 回應
    with page.expect_response(
        lambda r: r.url.startswith(API_URL) and r.status == 200
    ) as rinfo:
        page.click(BTN_QUERY)

    resp = rinfo.value

    # 5️⃣ 儲存 JSON
    try:
        data = resp.json()
        with open("grades_raw.json", "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print("抓到 JSON，已輸出 grades_raw.json")
        print("型別：", type(data))
    except Exception:
        text = resp.text()
        with open("grades_raw.txt", "w", encoding="utf-8") as f:
            f.write(text)
        print("回應不是 JSON，已輸出 grades_raw.txt")

    browser.close()
