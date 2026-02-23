import requests
import urllib3
from bs4 import BeautifulSoup
from concurrent.futures import ThreadPoolExecutor

# Disable insecure request warnings
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

class GradeFetcher:
    BASE = "https://shcloud2.k12ea.gov.tw/CLHSTYC"
    LOGIN_PAGE = f"{BASE}/Auth/Auth/CloudLogin"
    DO_CHECK = f"{BASE}/Auth/Auth/DoCloudLoginCheck"
    GRADES_PAGE = f"{BASE}/ICampus/StudentInfo/Index?page=%E6%88%90%E7%B8%BE%E6%9F%A5%E8%A9%A2"
    API_BASE = f"{BASE}/ICampus"

    @staticmethod
    def _get_hidden_token(html: str) -> str:
        """Extract __RequestVerificationToken from HTML."""
        soup = BeautifulSoup(html, "html.parser")
        el = soup.select_one('input[name="__RequestVerificationToken"]')
        if not el or not el.get("value"):
            raise RuntimeError("找不到 __RequestVerificationToken hidden input")
        return el["value"]

    @staticmethod
    def login_and_get_tokens(username, password):
        """Login via requests session, return (success, message, cookies_dict, student_no, token)."""
        try:
            print(f"Attempting login for user: {username} (requests mode)")
            s = requests.Session()

            # 1) GET login page to obtain cookies + hidden token
            r = s.get(GradeFetcher.LOGIN_PAGE, timeout=30, verify=False)
            r.raise_for_status()
            login_token = GradeFetcher._get_hidden_token(r.text)

            # 2) POST login
            headers = {
                "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
                "Referer": GradeFetcher.LOGIN_PAGE,
                "Origin": "https://shcloud2.k12ea.gov.tw",
                "X-Requested-With": "XMLHttpRequest",
            }
            data = {
                "SchoolCode": "030305",
                "LoginId": username,
                "PassString": password,
                "LoginType": "Student",
                "IsKeepLogin": "false",
                "IdentityId": "6",
                "SchoolName": "國立中大壢中",
                "GoogleToken": "8",
                "isRegistration": "false",
                "ShCaptchaGenCode": "10",
                "__RequestVerificationToken": login_token,
            }

            resp = s.post(GradeFetcher.DO_CHECK, data=data, headers=headers, timeout=30, verify=False)
            resp.raise_for_status()

            try:
                j = resp.json()
            except Exception:
                return False, "登入回應格式錯誤", None, None, None

            ok = bool(j.get("Result", {}).get("IsLoginSuccess"))
            if not ok:
                msg = j.get("Result", {}).get("DisplayMsg") or j.get("Message") or "登入失敗"
                return False, msg, None, None, None

            print("Login OK, fetching grades page for API token...")

            # 3) GET grades page to obtain the API-specific __RequestVerificationToken
            r2 = s.get(GradeFetcher.GRADES_PAGE, timeout=30, verify=False)
            r2.raise_for_status()
            api_token = GradeFetcher._get_hidden_token(r2.text)

            # 4) Extract cookies as dict (filter out malformed cookies like 'no-cache')
            cookies_dict = {}
            for c in s.cookies:
                try:
                    if c.name and c.value is not None and c.domain is not None:
                        cookies_dict[c.name] = c.value
                except Exception:
                    pass
            student_no = username

            print(f"Successfully obtained credentials for {student_no}")
            return True, "登入成功", cookies_dict, student_no, api_token

        except Exception as e:
            print(f"Login Exception: {e}")
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
            "searchType": "各次考試單科成績",
            "studentNo": student_no,
            "__RequestVerificationToken": token
        }
        
        try:
            print(f"Requesting structure for {student_no}...")
            response = requests.post(url, headers=headers, data=data, cookies=cookies, verify=False)
            response.raise_for_status()
            
            years_data = response.json()
            structure = {}
            
            # Collect items to fetch
            items = []
            for item in years_data:
                name = item.get('DisplayText') or item.get('text')
                value = item.get('Value') or item.get('value')
                if value:
                    items.append((name, value))
            
            # Fetch all exams in parallel
            def _fetch_one(name_value):
                n, v = name_value
                exams = GradeFetcher.get_exams_via_api(cookies, student_no, token, v)
                return n, v, exams
            
            with ThreadPoolExecutor(max_workers=len(items) or 1) as pool:
                for name, value, exams in pool.map(_fetch_one, items):
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
        
        print(f"API Fetching grades: Year={year}, Term={term}, Exam={exam_value}")
        
        try:
            response = requests.post(url, headers=headers, data=data, cookies=cookies, verify=False)
            response.raise_for_status()
            return response.json()
        except Exception as e:
            print(f"API Fetch Error: {e}")
            raise e
