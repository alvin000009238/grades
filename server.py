from flask import Flask, send_from_directory, jsonify, request, session
from flask_cors import CORS
import json
import os
import logging
from logging.handlers import RotatingFileHandler
from grade_fetcher import GradeFetcher

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger('SchoolGradesServer')
logger.setLevel(logging.INFO)

# Create file handler which logs even debug messages
fh = RotatingFileHandler('server.log', maxBytes=1024*1024, backupCount=5, encoding='utf-8')
fh.setLevel(logging.INFO)

# Create console handler with a higher log level
ch = logging.StreamHandler()
ch.setLevel(logging.INFO)

# Create formatter and add it to the handlers
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
fh.setFormatter(formatter)
ch.setFormatter(formatter)

# Add the handlers to the logger
logger.addHandler(fh)
logger.addHandler(ch)

app = Flask(__name__, static_folder='.')
app.secret_key = 'dev_secret_key' # Required for session management
CORS(app)

GRADES_FILE = 'grades_raw.json'

def get_user_fetcher(username=None):
    """Create a GradeFetcher instance for the specific user."""
    if not username:
        username = session.get('username')
    
    if not username:
        return None
        
    # Ensure sessions directory exists
    if not os.path.exists('sessions'):
        os.makedirs('sessions')
        
    state_file = f"sessions/{username}.json"
    return GradeFetcher(state_file=state_file, headless=True)

@app.route('/')
def index():
    logger.info("Accessing index page")
    return send_from_directory('.', 'index.html')

@app.route('/<path:filename>')
def static_files(filename):
    return send_from_directory('.', filename)

@app.route('/api/grades')
def get_grades():
    logger.info("Fetching local grades file")
    try:
        with open(GRADES_FILE, 'r', encoding='utf-8') as f:
            data = json.load(f)
        logger.info("Successfully loaded grades file")
        return jsonify(data)
    except FileNotFoundError:
        logger.warning(f"Grades file not found: {GRADES_FILE}")
        return jsonify({'error': '找不到成績資料檔案'}), 404
    except Exception as e:
        logger.error(f"Error reading grades file: {str(e)}", exc_info=True)
        return jsonify({'error': str(e)}), 500

@app.route('/api/check_login')
def check_login():
    logger.info("Checking login status")
    fetcher = get_user_fetcher()
    if not fetcher:
        logger.info("No fetcher (not logged in)")
        return jsonify({'logged_in': False}), 401
        
    try:
        with fetcher:
            is_logged_in = fetcher.check_login_status()
            logger.info(f"Login status: {is_logged_in}")
            if is_logged_in:
                return jsonify({'logged_in': True})
            else:
                return jsonify({'logged_in': False}), 401
    except Exception as e:
        logger.error(f"Error checking login status: {str(e)}", exc_info=True)
        return jsonify({'logged_in': False, 'error': str(e)}), 401

@app.route('/api/login', methods=['POST'])
def login():
    data = request.json
    username = data.get('username')
    # password = data.get('password') # Don't log password
    
    logger.info(f"Login attempt for user: {username}")
    
    if not username or not data.get('password'):
        logger.warning("Login failed: Missing credentials")
        return jsonify({'success': False, 'message': '請輸入帳號密碼'}), 400
        
    # Create fetcher specifically for this user
    fetcher = get_user_fetcher(username)
    if not fetcher:
        logger.error("System error: Could not create fetcher")
        return jsonify({'success': False, 'message': '系統錯誤'}), 500

    try:
        with fetcher:
            success, message = fetcher.login(username, data.get('password'))
            if success:
                logger.info(f"Login successful for user: {username}")
                session['username'] = username # Save user to session
                return jsonify({'success': True, 'message': message})
            else:
                logger.warning(f"Login failed for user {username}: {message}")
                return jsonify({'success': False, 'message': message}), 401
    except Exception as e:
         logger.error(f"Login error for user {username}: {str(e)}", exc_info=True)
         return jsonify({'success': False, 'message': f"Login error: {str(e)}"}), 500

@app.route('/api/years', methods=['GET'])
def get_years():
    logger.info("Fetching available years")
    fetcher = get_user_fetcher()
    if not fetcher:
        logger.warning("Attempted to get years without login")
        return jsonify({'error': '未登入'}), 401

    try:
        with fetcher:
            years = fetcher.get_years()
            logger.info(f"Retrieved years: {years}")
            return jsonify({'years': years})
    except Exception as e:
        logger.error(f"Error getting years: {str(e)}", exc_info=True)
        return jsonify({'error': str(e)}), 500

@app.route('/api/exams', methods=['POST'])
def get_exams():
    year = request.json.get('year')
    logger.info(f"Fetching exams for year: {year}")
    
    fetcher = get_user_fetcher()
    if not fetcher:
        logger.warning("Attempted to get exams without login")
        return jsonify({'error': '未登入'}), 401

    if not year:
        logger.warning("Missing year parameter")
        return jsonify({'error': '缺少學年度'}), 400
        
    try:
        with fetcher:
            exams = fetcher.get_exams(year)
            logger.info(f"Retrieved exams for {year}: {len(exams)} items")
            return jsonify({'exams': exams})
    except Exception as e:
        logger.error(f"Error getting exams: {str(e)}", exc_info=True)
        return jsonify({'error': str(e)}), 500

@app.route('/api/fetch', methods=['POST'])
def fetch_grades():
    year = request.json.get('year')
    exam = request.json.get('exam')
    logger.info(f"Fetching grades for {year} - {exam}")
    
    fetcher = get_user_fetcher()
    if not fetcher:
        logger.warning("Attempted to fetch grades without login")
        return jsonify({'error': '未登入'}), 401
    
    if not year or not exam:
        logger.warning("Missing parameters for fetch grades")
        return jsonify({'error': '缺少參數'}), 400
        
    try:
        with fetcher:
            data = fetcher.fetch_grades(year, exam)
            logger.info("Successfully fetched grades data")
            
            with open(GRADES_FILE, 'w', encoding='utf-8') as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            logger.info(f"Saved grades to {GRADES_FILE}")
                
            return jsonify({
                'success': True,
                'message': '成績已更新',
                'data': data
            })
    except Exception as e:
        logger.error(f"Error fetching grades: {str(e)}", exc_info=True)
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/structure', methods=['GET'])
def get_structure():
    logger.info("Fetching semester structure")
    fetcher = get_user_fetcher()
    if not fetcher:
         logger.warning("Attempted to get structure without login")
         return jsonify({'error': '未登入'}), 401
         
    try:
        with fetcher:
            structure = fetcher.get_all_structure()
            logger.info("Successfully retrieved structure")
            return jsonify({'structure': structure})
    except Exception as e:
        logger.error(f"Error getting structure: {str(e)}", exc_info=True)
        return jsonify({'error': str(e)}), 500

@app.route('/api/upload', methods=['POST'])
def upload_grades():
    logger.info("Uploading grades file")
    try:
        if 'file' in request.files:
            file = request.files['file']
            content = file.read().decode('utf-8')
            data = json.loads(content)
            logger.info("Processed file upload")
        elif request.is_json:
            data = request.get_json()
            logger.info("Processed JSON upload")
        else:
            logger.warning("Invalid upload format")
            return jsonify({'error': '請提供 JSON 檔案或資料'}), 400
        
        if 'Result' not in data:
            logger.warning("Invalid grades data structure in upload")
            return jsonify({'error': '無效的成績資料格式'}), 400
        
        logger.info("Upload successful and validated")
        return jsonify({
            'success': True,
            'data': data
        })
    except json.JSONDecodeError:
        logger.warning("JSON decode error during upload")
        return jsonify({'error': '無效的 JSON 格式'}), 400
    except Exception as e:
        logger.error(f"Error during upload: {str(e)}", exc_info=True)
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    logger.info("Starting School Grades Server...")
    logger.info("Server access: http://localhost:5000")
    app.run(host='0.0.0.0', port=5000, debug=True)
