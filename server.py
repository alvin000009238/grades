from flask import Flask, send_from_directory, jsonify, request, session
from flask_cors import CORS
from werkzeug.middleware.proxy_fix import ProxyFix
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
app.secret_key = os.environ.get('SECRET_KEY', os.urandom(24).hex())

# 信任反向代理 (Caddy) 傳來的 headers
app.wsgi_app = ProxyFix(app.wsgi_app, x_for=1, x_proto=1, x_host=1, x_prefix=1)

# Session cookie 設定
app.config['SESSION_COOKIE_SAMESITE'] = 'Lax'
app.config['SESSION_COOKIE_SECURE'] = True  # HTTPS 必須為 True
app.config['SESSION_COOKIE_HTTPONLY'] = True

# CORS 設定 - 允許 credentials
CORS(app, supports_credentials=True)

GRADES_FILE = 'grades_raw.json'

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
    # Only check if we have tokens in session
    if session.get('api_cookies') and session.get('api_token'):
        return jsonify({'logged_in': True})
    return jsonify({'logged_in': False}), 401

@app.route('/api/login', methods=['POST'])
def login():
    data = request.json
    username = data.get('username')
    
    logger.info(f"Login attempt for user: {username}")
    
    if not username or not data.get('password'):
        return jsonify({'success': False, 'message': '請輸入帳號密碼'}), 400
        
    fetcher = GradeFetcher(headless=True)
    try:
        success, message, cookies, student_no, token = fetcher.login_and_get_tokens(username, data.get('password'))
        
        if success:
            logger.info(f"Login successful for user: {username}")
            session['username'] = username
            session['api_cookies'] = cookies
            session['student_no'] = student_no
            session['api_token'] = token
            
            # Fetch structure immediately using requests
            try:
                logger.info("Fetching structure via API...")
                structure = GradeFetcher.get_structure_via_api(cookies, student_no, token)
                session['structure'] = structure
                logger.info(f"Structure cached: {len(structure)} semesters")
            except Exception as e:
                logger.error(f"Failed to fetch structure: {e}")
                
            return jsonify({'success': True, 'message': message})
        else:
            return jsonify({'success': False, 'message': message}), 401
    except Exception as e:
         logger.error(f"Login error: {str(e)}", exc_info=True)
         return jsonify({'success': False, 'message': f"Login error: {str(e)}"}), 500

@app.route('/api/fetch', methods=['POST'])
def fetch_grades():
    year_value = request.json.get('year_value')
    exam_value = request.json.get('exam_value')
    
    cookies = session.get('api_cookies')
    token = session.get('api_token')
    student_no = session.get('student_no')
    
    if not cookies or not token:
        return jsonify({'error': '未登入'}), 401
    
    try:
        data = GradeFetcher.fetch_grades_via_api(cookies, student_no, token, year_value, exam_value)
        
        with open(GRADES_FILE, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            
        return jsonify({
            'success': True,
            'message': '成績已更新',
            'data': data
        })
    except Exception as e:
        logger.error(f"Error fetching grades (API): {str(e)}", exc_info=True)
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/structure', methods=['GET'])
def get_structure():
    if not session.get('api_token'):
        return jsonify({'error': '未登入'}), 401
    
    force_reload = request.args.get('reload') == 'true'
    cached = session.get('structure')
    
    if cached and not force_reload:
        return jsonify({'structure': cached})
        
    # Retry fetch if not cached or forced
    try:
        if not session.get('api_cookies') or not session.get('student_no'):
             return jsonify({'error': '連線過期，請重新登入'}), 401

        structure = GradeFetcher.get_structure_via_api(
            session['api_cookies'], 
            session['student_no'], 
            session['api_token']
        )
        session['structure'] = structure
        return jsonify({'structure': structure})
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/logout', methods=['POST'])
def logout():
    session.clear()
    return jsonify({'success': True, 'message': '已登出'})

@app.route('/api/upload', methods=['POST'])
def upload_grades():
    logger.info("Uploading grades file")
    try:
        if 'file' in request.files:
            file = request.files['file']
            content = file.read().decode('utf-8')
            data = json.loads(content)
        elif request.is_json:
            data = request.get_json()
        else:
            return jsonify({'error': '請提供 JSON 檔案或資料'}), 400
        
        if 'Result' not in data:
            return jsonify({'error': '無效的成績資料格式'}), 400
        
        return jsonify({
            'success': True,
            'data': data
        })
    except Exception as e:
        logger.error(f"Error during upload: {str(e)}", exc_info=True)
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    logger.info("Starting School Grades Server (Hybrid Mode)...")
    app.run(host='0.0.0.0', port=5000, debug=True)
