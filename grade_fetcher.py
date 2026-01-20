import os
import time
from playwright.sync_api import sync_playwright

class GradeFetcher:
    def __init__(self, state_file="state.json", headless=True):
        self.state_file = state_file
        self.headless = headless
        self.playwright = None
        self.browser = None
        self.context = None
        self.page = None
        
        # Selectors
        self.LOGIN_URL = "https://shcloud2.k12ea.gov.tw/CLHSTYC/Auth/Auth/CloudLogin?sys=Auth"
        self.GRADES_URL = "https://shcloud2.k12ea.gov.tw/CLHSTYC/ICampus/StudentInfo/Index?page=%E6%88%90%E7%B8%BE%E6%9F%A5%E8%A9%A2"
        self.API_URL = "https://shcloud2.k12ea.gov.tw/CLHSTYC/ICampus/TutorShGrade/GetScoreForStudentExamContent"
        
        self.TAB = 'div.tabSwitch[data-tab="單次考試所有成績"]'
        self.BTN_QUERY = "button:has-text('查詢')"
        self.YEAR_DROPDOWN = 'span.k-select[aria-controls="gradeYearTermFor單次考試所有成績_listbox"]'
        self.YEAR_LISTBOX  = '#gradeYearTermFor單次考試所有成績_listbox'
        self.EXAM_DROPDOWN = 'span.k-select[aria-controls="gradeExam_listbox"]'
        self.EXAM_LISTBOX  = '#gradeExam_listbox'

    def start_browser(self, use_saved_state=True):
        """Start the browser if not already started."""
        if not self.browser:
            self.playwright = sync_playwright().start()
            self.browser = self.playwright.chromium.launch(headless=self.headless, slow_mo=100)
            
            # Check if state file exists and load it ONLY if requested
            if use_saved_state and os.path.exists(self.state_file):
                print(f"Loading session from {self.state_file}")
                self.context = self.browser.new_context(storage_state=self.state_file)
            else:
                print("Starting with new session")
                self.context = self.browser.new_context()
                
            self.page = self.context.new_page()

    def check_login_status(self):
        """Check if current session is valid."""
        try:
            self.start_browser(use_saved_state=True)
            self.page.goto(self.GRADES_URL, wait_until="networkidle", timeout=10000)
            # If redirected to login page, session is invalid
            if "CloudLogin" in self.page.url:
                return False
            return True
        except Exception as e:
            print(f"Check login error: {e}")
            return False

    def login(self, username, password):
        """Login to the system."""
        try:
            print(f"Attempting login for user: {username}")
            # Force new session for login to avoid redirect loop if already logged in
            self.start_browser(use_saved_state=False)
            print(f"Navigating to {self.LOGIN_URL}")
            self.page.goto(self.LOGIN_URL)
            
            # Handle popup if exists
            if self.page.get_by_role("button", name="Close this dialog").is_visible():
                print("Closing dialog popup")
                self.page.get_by_role("button", name="Close this dialog").click()
                
            print("Clicking '學生'")
            self.page.get_by_text("學生").click()
            
            print("Filling credentials")
            self.page.get_by_role('textbox', name='請輸入帳號').fill(username)
            self.page.get_by_placeholder('請輸入密碼').fill(password)
            
            print("Clicking checkbox")
            # Try/Except for checkbox slightly more robustly?
            try:
                self.page.get_by_role('checkbox').click()
            except Exception as e:
                print(f"Warning: Could not click checkbox: {e}")

            print("Clicking Login button and waiting for API check")
            # Intercept the login check API
            with self.page.expect_response(
                lambda response: "DoCloudLoginCheck" in response.url and response.request.method == "POST"
            ) as response_info:
                self.page.get_by_role('button', name='登入').click()
            
            api_response = response_info.value
            try:
                result_json = api_response.json()
                print(f"Login API Response: {result_json}")
                
                # Check API result
                if result_json.get("Status") == "Error" or (result_json.get("Result") and not result_json["Result"].get("IsLoginSuccess")):
                    error_msg = result_json.get("Message") or (result_json.get("Result") and result_json["Result"].get("DisplayMsg")) or "登入失敗"
                    print(f"Login API reported failure: {error_msg}")
                    return False, f"登入失敗: {error_msg}"
                    
            except Exception as json_err:
                print(f"Error parsing login API response: {json_err}")
                # Continue to check URL if JSON parsing fails, just in case
            
            print("API check passed/skipped, waiting for redirect...")
            try:
                # Wait up to 20 seconds for the URL to change to the success page
                self.page.wait_for_url("**/ICampus/**", timeout=20000)
            except Exception:
                print("Timed out waiting for URL redirect")

            current_url = self.page.url
            print(f"Current URL after wait: {current_url}")
            
            if "ICampus/Home/Index2" in current_url or "ICampus" in current_url:
                print("Login successful, saving state")
                # Save state
                self.context.storage_state(path=self.state_file)
                return True, "登入成功"
            else:
                print("Login failed: Redirected url does not match expected")
                return False, f"登入失敗，請檢查帳號密碼 (URL: {current_url})"
                
        except Exception as e:
            print(f"Login Exception: {e}")
            import traceback
            traceback.print_exc()
            return False, f"登入發生錯誤: {str(e)}"

    def _get_options(self, dropdown_selector, listbox_selector):
        """Helper to get options from Kendo dropdown."""
        self.page.click(dropdown_selector)
        self.page.wait_for_selector(listbox_selector, state="visible")
        
        items = self.page.locator(f"{listbox_selector} .k-item").all()
        if not items:
            items = self.page.locator(f"{listbox_selector} li").all()
            
        options = [item.text_content().strip() for item in items]
        
        # Close dropdown
        if items:
            items[0].click()
            
        return options

    def _select_option(self, dropdown_selector, listbox_selector, text):
        """Helper to select an option."""
        self.page.click(dropdown_selector)
        self.page.wait_for_selector(listbox_selector, state="visible")
        self.page.click(f'{listbox_selector} >> li:has-text("{text}")')

    def get_years(self):
        """Get available academic years."""
        try:
            self.start_browser()
            # Ensure we are on grades page
            if self.GRADES_URL not in self.page.url:
                self.page.goto(self.GRADES_URL, wait_until="networkidle")
            
            self.page.wait_for_selector(self.TAB)
            self.page.click(self.TAB)
            time.sleep(1) # Wait for UI
            
            return self._get_options(self.YEAR_DROPDOWN, self.YEAR_LISTBOX)
        except Exception as e:
            print(f"Error getting years: {e}")
            return []

    def get_exams(self, year):
        """Get available exams for a specific year."""
        try:
            self.start_browser()
            if self.GRADES_URL not in self.page.url:
                self.page.goto(self.GRADES_URL, wait_until="networkidle")
            
            self.page.wait_for_selector(self.TAB)
            self.page.click(self.TAB)
            
            # Select year
            self._select_option(self.YEAR_DROPDOWN, self.YEAR_LISTBOX, year)
            time.sleep(1) # Wait for exams to load
            
            return self._get_options(self.EXAM_DROPDOWN, self.EXAM_LISTBOX)
        except Exception as e:
            print(f"Error getting exams: {e}")
            return []

    def fetch_grades(self, year, exam):
        """Fetch grades for specific year and exam."""
        try:
            self.start_browser()
            if self.GRADES_URL not in self.page.url:
                self.page.goto(self.GRADES_URL, wait_until="networkidle")
                
            self.page.wait_for_selector(self.TAB)
            self.page.click(self.TAB)
            
            # Select year and exam
            self._select_option(self.YEAR_DROPDOWN, self.YEAR_LISTBOX, year)
            time.sleep(0.5)
            self._select_option(self.EXAM_DROPDOWN, self.EXAM_LISTBOX, exam)
            
            # Query
            with self.page.expect_response(
                lambda r: r.url.startswith(self.API_URL) and r.status == 200
            ) as rinfo:
                self.page.click(self.BTN_QUERY)
                
            resp = rinfo.value
            return resp.json()
            
        except Exception as e:
            print(f"Error fetching grades: {e}")
            raise e

    def get_all_structure(self):
        """Get all years and their corresponding exams."""
        structure = {}
        try:
            print("Fetching all structure...")
            years = self.get_years()
            
            for year in years:
                print(f"Fetching exams for year: {year}")
                exams = self.get_exams(year)
                structure[year] = exams
                
            return structure
        except Exception as e:
            print(f"Error getting structure: {e}")
            import traceback
            traceback.print_exc()
            return structure

    def close(self):
        """Close the browser."""
        try:
            if self.context:
                self.context.close()
            if self.browser:
                self.browser.close()
            if self.playwright:
                self.playwright.stop()
        except Exception as e:
            print(f"Error closing fetcher: {e}")
        finally:
            self.context = None
            self.browser = None
            self.playwright = None

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
