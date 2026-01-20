from playwright.sync_api import sync_playwright

LOGIN_URL = "https://shcloud2.k12ea.gov.tw/CLHSTYC/Auth/Auth/CloudLogin?sys=Auth"
STATE = "state.json"

account = input("請輸入帳號: ")
pwd = input("請輸入密碼: ")

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False)
    ctx = browser.new_context()
    page = ctx.new_page()
    page.goto(LOGIN_URL)
    if page.get_by_role("button", name="Close this dialog").is_visible():
        page.get_by_role("button", name="Close this dialog").click()
    page.get_by_text("學生").click()
    page.get_by_role('textbox', name='請輸入帳號').fill(account)
    page.get_by_placeholder('請輸入密碼').fill(pwd)
    page.get_by_role('checkbox').click()
    page.get_by_role('button', name='登入').click()
    
    page.wait_for_load_state("networkidle")

    if page.url == "https://shcloud2.k12ea.gov.tw/CLHSTYC/ICampus/Home/Index2":
        print("✓ 登入成功")
    else:
        print("✗ 登入失敗")
        browser.close()
        exit()

    
    ctx.storage_state(path=STATE)
    browser.close()
