import asyncio
from playwright.async_api import async_playwright

async def run():
    async with async_playwright() as p:
        # We need an HTTP server for Playwright to interact with.
        # Starting a quick static server here since it's just testing HTML layout
        import http.server
        import socketserver
        import threading

        PORT = 8000
        Handler = http.server.SimpleHTTPRequestHandler

        class TCPServer(socketserver.TCPServer):
            allow_reuse_address = True

        httpd = TCPServer(("", PORT), Handler)
        thread = threading.Thread(target=httpd.serve_forever)
        thread.daemon = True
        thread.start()

        try:
            browser = await p.chromium.launch()
            page = await browser.new_page()
            await page.goto(f"http://localhost:{PORT}/public/index.html")

            # Check if labels have 'for' attributes matching the inputs
            for_username = await page.get_attribute('label >> text="帳號"', 'for')
            for_password = await page.get_attribute('label >> text="密碼"', 'for')
            for_year = await page.get_attribute('label >> text="學年度"', 'for')
            for_exam = await page.get_attribute('label >> text="考試名稱"', 'for')

            assert for_username == "usernameInput", f"Expected 'usernameInput', got {for_username}"
            assert for_password == "passwordInput", f"Expected 'passwordInput', got {for_password}"
            assert for_year == "yearSelect", f"Expected 'yearSelect', got {for_year}"
            assert for_exam == "examSelect", f"Expected 'examSelect', got {for_exam}"

            print("All labels are correctly associated with their inputs/selects!")
            await browser.close()
        finally:
            httpd.shutdown()
            httpd.server_close()

asyncio.run(run())
