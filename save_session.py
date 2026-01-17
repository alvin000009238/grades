from playwright.sync_api import sync_playwright

LOGIN_URL = "https://shcloud2.k12ea.gov.tw/CLHSTYC/Auth/Auth/CloudLogin?sys=Auth"
STATE = "state.json"

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False)
    ctx = browser.new_context()
    page = ctx.new_page()
    page.goto(LOGIN_URL)

    print("請在瀏覽器中完成登入，看到成績系統後回來按 Enter")
    input()

    ctx.storage_state(path=STATE)
    browser.close()
