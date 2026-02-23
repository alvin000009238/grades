import asyncio
import threading
import requests
import urllib3
from playwright.async_api import async_playwright

# Disable insecure request warnings
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

class GradeFetcher:
    def __init__(self, state_file="state.json", headless=True):
        self.state_file = state_file
        self.headless = headless
        self.playwright = None
        self.browser = None
        self.context = None
        self.page = None

        # URLs
        self.LOGIN_URL = "https://shcloud2.k12ea.gov.tw/CLHSTYC/Auth/Auth/CloudLogin?sys=Auth"
        self.GRADES_URL = "https://shcloud2.k12ea.gov.tw/CLHSTYC/ICampus/StudentInfo/Index?page=%E6%88%90%E7%B8%BE%E6%9F%A5%E8%A9%A2"
        self.API_BASE = "https://shcloud2.k12ea.gov.tw/CLHSTYC/ICampus"

    def start_browser(self, use_saved_state=True):
        """Start the browser if not already started."""
        self._run_coro_sync(self._start_browser())

    async def _start_browser(self):
        """Async browser startup to support asyncio environments."""
        if not self.browser:
            self.playwright = await async_playwright().start()
            self.browser = await self.playwright.chromium.launch(headless=self.headless, slow_mo=100)
            self.context = await self.browser.new_context()
            self.page = await self.context.new_page()

    def login_and_get_tokens(self, username, password):
        return self._run_coro_sync(self._login_and_get_tokens_async(username, password))

    async def _login_and_get_tokens_async(self, username, password):
        """Login, extract tokens, close browser, and return credentials."""
        try:
            print(f"Attempting login for user: {username} (Hybrid Mode)")
            await self._start_browser()
            print(f"Navigating to {self.LOGIN_URL}")
            await self.page.goto(self.LOGIN_URL)

            # Handle popup if exists
            popup_close_btn = self.page.locator("button.swal2-close")
            if await popup_close_btn.is_visible():
                print("Found swal2 popup, closing it")
                await popup_close_btn.click()
            elif await self.page.get_by_role("button", name="Close this dialog").is_visible():
                print("Closing dialog popup via aria-label")
                await self.page.get_by_role("button", name="Close this dialog").click()

            print("Clicking '學生'")
            await self.page.get_by_text("學生", exact=True).click()

            print("Filling credentials")
            await self.page.get_by_role('textbox', name='請輸入帳號').fill(username)
            await self.page.get_by_placeholder('請輸入密碼').fill(password)

            try:
                await self.page.get_by_role('checkbox').click()
            except Exception:
                pass

            print("Clicking Login button...")
            async with self.page.expect_response(lambda r: "DoCloudLoginCheck" in r.url and r.request.method == "POST") as response_info:
                await self.page.get_by_role('button', name='登入').click()

            api_response = await response_info.value
            try:
                result = api_response.json()
                if result.get("Status") == "Error" or (result.get("Result") and not result["Result"].get("IsLoginSuccess")):
                    return False, "登入失敗", None, None, None
            except Exception as e:
                print(f"Warning: Could not parse login API response (likely redirected): {e}")
                # Don't return False here, assume potential success and let the URL check decide

            print("Login API passed, waiting for redirect to Grades page...")
            try:
                await self.page.wait_for_url("**/ICampus/**", timeout=20000)
            except Exception:
                print("Timed out waiting for URL redirect")

            # Go to Grades page specifically to ensure tokens are present
            print("Navigating to grades page to scrape tokens...")
            await self.page.goto(self.GRADES_URL, wait_until="networkidle")

            # Scrape tokens
            print("Scraping tokens...")
            student_no = username # Confirmed by user
            
            # Try to get verification token from hidden input
            token = await self.page.locator('input[name="__RequestVerificationToken"]').first.get_attribute('value')
            
            if not token:
                print("Failed to find __RequestVerificationToken")
                return False, "無法取得驗證代碼", None, None, None

            # Get Cookies
            cookies = {c['name']: c['value'] for c in await self.context.cookies()}
            
            print(f"Successfully obtained credentials for {student_no}")
            
            # Close browser immediately to save resources
            await self._close_async()
            
            return True, "登入成功", cookies, student_no, token

        except Exception as e:
            print(f"Login Exception: {e}")
            await self._close_async()
            return False, f"登入錯誤: {str(e)}", None, None, None

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
            "searchType": "各次考試單科成績", # Based on user info
            "studentNo": student_no,
            "__RequestVerificationToken": token
        }
        
        try:
            print(f"Requesting structure for {student_no}...")
            response = requests.post(url, headers=headers, data=data, cookies=cookies, verify=False)
            response.raise_for_status()
            
            # Convert API response to format expected by frontend
            # The API likely returns a list of years/terms. We need to fetch exams for each?
            # Actually, per user trace, this API returns years.
            # Let's inspect the raw response logic from previous Playwright code...
            # The previous code iterated through years and exams.
            # Ideally we need `GetGradeCanQueryExamNoListByStudentNo` too?
            # But user only gave `GetGradeCanQueryYearTermListByStudentNo`.
            # Let's assume for now we return the years and frontend handles it, 
            # OR we try to fetch exams if we can guess the API.
            # User trace showed `GetGradeCanQueryExamNoListByStudentNo` in the list!
            # Let's try to implement a basic structure first.
            
            years_data = response.json()
            structure = {}
            
            for item in years_data:
                # Assuming item has 'text' and 'value' or similar based on Kendo
                # Kendo usually maps from DisplayText/Value.
                # Let's handle both.
                name = item.get('DisplayText') or item.get('text')
                value = item.get('Value') or item.get('value')
                
                if not value: continue
                
                # We need exams for each year. 
                # Attempt to call `GetGradeCanQueryExamNoListByStudentNo`
                exams = GradeFetcher.get_exams_via_api(cookies, student_no, token, value)
                
                structure[name] = {
                    "year_value": value,
                    "exams": exams
                }
                
            return structure
            
        except Exception as e:
            print(f"Error fetching structure: {e}")
            return {}

    @staticmethod
    def get_exams_via_api(cookies, student_no, token, year_value):
        """Helper to fetch exams for a year"""
        url = "https://shcloud2.k12ea.gov.tw/CLHSTYC/ICampus/CommonData/GetGradeCanQueryExamNoListByStudentNo"
        
        # Parse year and term from year_value (e.g., "114_1")
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
            "searchType": "單次考試所有成績", # Based on user trace
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
        except Exception as e:
            print(f"Error fetching exams via API: {e}")
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
        
        # Parse Year and Term from year_value (e.g., "114_2" -> Year: 114, Term: 2)
        try:
            if "_" in str(year_value):
                year, term = str(year_value).split("_")
            elif len(str(year_value)) >= 4:
                year = year_value[:-1]
                term = year_value[-1]
            else:
                 # Fallback/Default
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
        
        print(f"API Fetching grades: Year={year}, Term={term}, Exam={exam_value}")
        
        try:
            response = requests.post(url, headers=headers, data=data, cookies=cookies, verify=False)
            response.raise_for_status()
            return response.json()
        except Exception as e:
            print(f"API Fetch Error: {e}")
            raise e

    def close(self):
        """Close the browser."""
        self._run_coro_sync(self._close_async())

    async def _close_async(self):
        """Async close for playwright resources."""
        try:
            if self.context:
                await self.context.close()
            if self.browser:
                await self.browser.close()
            if self.playwright:
                await self.playwright.stop()
        except Exception as e:
            print(f"Error closing fetcher: {e}")
        finally:
            self.context = None
            self.browser = None
            self.playwright = None

    @staticmethod
    def _run_coro_sync(coro):
        """Run coroutine from both sync and async environments."""
        try:
            asyncio.get_running_loop()
            in_async_loop = True
        except RuntimeError:
            in_async_loop = False

        if not in_async_loop:
            return asyncio.run(coro)

        result = {}
        error = {}

        def _runner():
            try:
                result['value'] = asyncio.run(coro)
            except Exception as exc:
                error['value'] = exc

        thread = threading.Thread(target=_runner, daemon=True)
        thread.start()
        thread.join()

        if 'value' in error:
            raise error['value']
        return result.get('value')

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
