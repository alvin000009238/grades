import asyncio
import logging
import threading

import requests
import urllib3
from playwright.async_api import async_playwright

# Disable insecure request warnings
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

logger = logging.getLogger(__name__)


class GradeFetcher:
    def __init__(self, state_file="state.json", headless=True):
        self.state_file = state_file
        self.headless = headless
        self.playwright = None
        self.browser = None
        self.context = None
        self.page = None

        self._loop = None
        self._loop_thread = None
        self._loop_ready = threading.Event()

        # URLs
        self.LOGIN_URL = "https://shcloud2.k12ea.gov.tw/CLHSTYC/Auth/Auth/CloudLogin?sys=Auth"
        self.GRADES_URL = "https://shcloud2.k12ea.gov.tw/CLHSTYC/ICampus/StudentInfo/Index?page=%E6%88%90%E7%B8%BE%E6%9F%A5%E8%A9%A2"
        self.API_BASE = "https://shcloud2.k12ea.gov.tw/CLHSTYC/ICampus"

    def _ensure_loop_thread(self):
        if self._loop_thread and self._loop_thread.is_alive() and self._loop:
            return

        self._loop_ready.clear()

        def _runner():
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            self._loop = loop
            self._loop_ready.set()
            loop.run_forever()

        self._loop_thread = threading.Thread(target=_runner, daemon=True)
        self._loop_thread.start()
        self._loop_ready.wait()

    def start_browser(self, use_saved_state=True):
        """Start the browser if not already started."""
        self._run_coro_sync(self._start_browser())

    async def start_browser_async(self, use_saved_state=True):
        """Start browser in async callers without sync bridge."""
        await self._start_browser()

    async def _start_browser(self):
        """Async browser startup to support asyncio environments."""
        if not self.browser:
            self.playwright = await async_playwright().start()
            self.browser = await self.playwright.chromium.launch(headless=self.headless, slow_mo=100)
            self.context = await self.browser.new_context()
            self.page = await self.context.new_page()

    def login_and_get_tokens(self, username, password):
        return self._run_coro_sync(self._login_and_get_tokens_async(username, password))

    async def login_and_get_tokens_async(self, username, password):
        return await self._login_and_get_tokens_async(username, password)

    async def _login_and_get_tokens_async(self, username, password):
        """Login, extract tokens, close browser, and return credentials."""
        try:
            await self._start_browser()
            await self.page.goto(self.LOGIN_URL)

            # Handle popup if exists
            popup_close_btn = self.page.locator("button.swal2-close")
            if await popup_close_btn.is_visible():
                await popup_close_btn.click()
            elif await self.page.get_by_role("button", name="Close this dialog").is_visible():
                await self.page.get_by_role("button", name="Close this dialog").click()

            await self.page.get_by_text("學生", exact=True).click()

            await self.page.get_by_role('textbox', name='請輸入帳號').fill(username)
            await self.page.get_by_placeholder('請輸入密碼').fill(password)

            try:
                await self.page.get_by_role('checkbox').click()
            except Exception:
                # Checkbox may not always be present.
                pass

            async with self.page.expect_response(
                lambda r: "DoCloudLoginCheck" in r.url and r.request.method == "POST"
            ) as response_info:
                await self.page.get_by_role('button', name='登入').click()

            api_response = await response_info.value
            try:
                result = api_response.json()
                if result.get("Status") == "Error" or (
                    result.get("Result") and not result["Result"].get("IsLoginSuccess")
                ):
                    await self._close_async()
                    return False, "登入失敗", None, None, None
            except Exception:
                # Some successful flows can redirect and make this payload non-JSON.
                pass

            try:
                await self.page.wait_for_url("**/ICampus/**", timeout=20000)
            except Exception:
                # Continue; we explicitly navigate to grades page next.
                pass

            # Go to Grades page specifically to ensure tokens are present
            await self.page.goto(self.GRADES_URL, wait_until="networkidle")

            # Scrape tokens
            student_no = username

            token = await self.page.locator('input[name="__RequestVerificationToken"]').first.get_attribute('value')

            if not token:
                await self._close_async()
                return False, "無法取得驗證代碼", None, None, None

            cookies = {c['name']: c['value'] for c in await self.context.cookies()}

            await self._close_async()
            return True, "登入成功", cookies, student_no, token

        except Exception:
            logger.warning("Login flow failed due to unexpected browser error")
            await self._close_async()
            return False, "登入錯誤，請稍後再試", None, None, None

    @staticmethod
    def get_structure_via_api(cookies, student_no, token):
        """Fetch structure using requests"""
        url = "https://shcloud2.k12ea.gov.tw/CLHSTYC/ICampus/CommonData/GetGradeCanQueryYearTermListByStudentNo"
        headers = {
            "Accept": "*/*",
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "X-Requested-With": "XMLHttpRequest",
            "Origin": "https://shcloud2.k12ea.gov.tw",
            "Referer": "https://shcloud2.k12ea.gov.tw/CLHSTYC/ICampus/StudentInfo/Index?page=%E6%88%90%E7%B8%BE%E6%9F%A5%E8%A9%A2"
        }
        data = {
            "searchType": "各次考試單科成績",
            "studentNo": student_no,
            "__RequestVerificationToken": token
        }

        try:
            response = requests.post(url, headers=headers, data=data, cookies=cookies, verify=False)
            response.raise_for_status()

            years_data = response.json()
            structure = {}

            for item in years_data:
                name = item.get('DisplayText') or item.get('text')
                value = item.get('Value') or item.get('value')

                if not value:
                    continue

                exams = GradeFetcher.get_exams_via_api(cookies, student_no, token, value)

                structure[name] = {
                    "year_value": value,
                    "exams": exams
                }

            return structure

        except Exception:
            logger.warning("Failed to fetch grade structure")
            return {}

    @staticmethod
    def get_exams_via_api(cookies, student_no, token, year_value):
        """Helper to fetch exams for a year"""
        url = "https://shcloud2.k12ea.gov.tw/CLHSTYC/ICampus/CommonData/GetGradeCanQueryExamNoListByStudentNo"

        try:
            if "_" in str(year_value):
                year, term = str(year_value).split("_")
            elif len(str(year_value)) >= 4:
                year = year_value[:-1]
                term = year_value[-1]
            else:
                year = "114"
                term = "1"
        except Exception:
            year = "114"
            term = "1"

        headers = {
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "X-Requested-With": "XMLHttpRequest",
            "Referer": "https://shcloud2.k12ea.gov.tw/CLHSTYC/ICampus/StudentInfo/Index?page=%E6%88%90%E7%B8%BE%E6%9F%A5%E8%A9%A2"
        }
        data = {
            "searchType": "單次考試所有成績",
            "studentNo": student_no,
            "year": year,
            "term": term,
            "__RequestVerificationToken": token
        }
        try:
            resp = requests.post(url, headers=headers, data=data, cookies=cookies, verify=False)
            if resp.status_code == 200:
                exams = []
                for item in resp.json():
                    exams.append({
                        "text": item.get('DisplayText') or item.get('text'),
                        "value": item.get('Value') or item.get('value')
                    })
                return exams
        except Exception:
            logger.warning("Failed to fetch exam list")
        return []

    @staticmethod
    def fetch_grades_via_api(cookies, student_no, token, year_value, exam_value):
        """Fetch grades using requests"""
        url = "https://shcloud2.k12ea.gov.tw/CLHSTYC/ICampus/TutorShGrade/GetScoreForStudentExamContent"
        headers = {
            "Accept": "*/*",
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "X-Requested-With": "XMLHttpRequest",
            "Referer": "https://shcloud2.k12ea.gov.tw/CLHSTYC/ICampus/StudentInfo/Index?page=%E6%88%90%E7%B8%BE%E6%9F%A5%E8%A9%A2"
        }

        try:
            if "_" in str(year_value):
                year, term = str(year_value).split("_")
            elif len(str(year_value)) >= 4:
                year = year_value[:-1]
                term = year_value[-1]
            else:
                year = "114"
                term = "2"
        except Exception:
            year = "114"
            term = "2"

        data = {
            "StudentNo": student_no,
            "SearchType": "單次考試所有成績",
            "__RequestVerificationToken": token,
            "Year": year,
            "Term": term,
            "ExamNo": exam_value
        }

        try:
            response = requests.post(url, headers=headers, data=data, cookies=cookies, verify=False)
            response.raise_for_status()
            return response.json()
        except Exception:
            logger.warning("Failed to fetch grades content")
            raise

    def close(self):
        """Close the browser."""
        try:
            self._run_coro_sync(self._close_async())
        finally:
            self._stop_loop_thread()

    async def close_async(self):
        """Close browser in async callers without sync bridge."""
        await self._close_async()

    async def _close_async(self):
        """Async close for playwright resources."""
        try:
            if self.context:
                await self.context.close()
            if self.browser:
                await self.browser.close()
            if self.playwright:
                await self.playwright.stop()
        except Exception:
            logger.warning("Error while closing browser resources")
        finally:
            self.context = None
            self.browser = None
            self.playwright = None

    def _stop_loop_thread(self):
        if self._loop and self._loop.is_running():
            self._loop.call_soon_threadsafe(self._loop.stop)
        if self._loop_thread and self._loop_thread.is_alive():
            self._loop_thread.join(timeout=2)
        self._loop = None
        self._loop_thread = None

    def _run_coro_sync(self, coro):
        """Run coroutine from sync contexts without recreating event loops per call."""
        try:
            asyncio.get_running_loop()
            coro.close()
            raise RuntimeError(
                "Cannot call sync GradeFetcher methods inside an active asyncio loop; "
                "use the async methods instead."
            )
        except RuntimeError as exc:
            if "Cannot call sync GradeFetcher methods" in str(exc):
                raise

        self._ensure_loop_thread()
        future = asyncio.run_coroutine_threadsafe(coro, self._loop)
        return future.result()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        try:
            self.close()
        finally:
            self._stop_loop_thread()
