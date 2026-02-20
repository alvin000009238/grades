from flask import Flask, send_from_directory, jsonify, request, session
from flask_cors import CORS
from werkzeug.middleware.proxy_fix import ProxyFix
import json
import os
import logging
from logging.handlers import RotatingFileHandler
from grade_fetcher import GradeFetcher
import time
import threading
import secrets
import string

SHARED_FOLDER = 'shared_grades'
CLEANUP_INTERVAL = 600  # 10 minutes
FILE_LIFETIME = 7200    # 2 hours

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
app.config['MAX_CONTENT_LENGTH'] = 2 * 1024 * 1024  # 2MB max upload size

# 信任反向代理 (Caddy) 傳來的 headers
app.wsgi_app = ProxyFix(app.wsgi_app, x_for=1, x_proto=1, x_host=1, x_prefix=1)

# Session cookie 設定
app.config['SESSION_COOKIE_SAMESITE'] = 'Lax'
app.config['SESSION_COOKIE_SECURE'] = True  # HTTPS 必須為 True
app.config['SESSION_COOKIE_HTTPONLY'] = True

# CORS 設定 - 允許 credentials
CORS(app, supports_credentials=True)


if not os.path.exists(SHARED_FOLDER):
    os.makedirs(SHARED_FOLDER)

def cleanup_thread():
    """Background thread to clean up old shared files."""
    while True:
        try:
            now = time.time()
            for filename in os.listdir(SHARED_FOLDER):
                file_path = os.path.join(SHARED_FOLDER, filename)
                if os.path.isfile(file_path):
                    if now - os.path.getmtime(file_path) > FILE_LIFETIME:
                        try:
                            os.remove(file_path)
                            logger.info(f"Deleted expired file: {filename}")
                        except Exception as e:
                            logger.error(f"Error deleting file {filename}: {e}")
        except Exception as e:
            logger.error(f"Error in cleanup thread: {e}")
        time.sleep(CLEANUP_INTERVAL)

# Start cleanup thread
# In debug mode, Flask's reloader spawns two processes. We only start the thread
# in the child (WERKZEUG_RUN_MAIN='true') to avoid running it twice.
# In production (gunicorn), WERKZEUG_RUN_MAIN is not set, so it always starts.
_is_reloader_parent = app.debug and os.environ.get('WERKZEUG_RUN_MAIN') != 'true'
if not _is_reloader_parent:
    threading.Thread(target=cleanup_thread, daemon=True).start()
    logger.info("Cleanup thread started")

@app.route('/')
def index():
    logger.info("Accessing index page")
    return send_from_directory('.', 'index.html')

ALLOWED_STATIC_EXT = {'.html', '.css', '.js', '.json', '.png', '.jpg', '.jpeg', '.gif', '.svg', '.ico', '.woff', '.woff2', '.ttf'}

@app.route('/<path:filename>')
def static_files(filename):
    # Block sensitive files
    ext = os.path.splitext(filename)[1].lower()
    if ext not in ALLOWED_STATIC_EXT:
        return jsonify({'error': 'Forbidden'}), 403
    # Block dotfiles and sensitive paths
    if any(part.startswith('.') for part in filename.split('/')):
        return jsonify({'error': 'Forbidden'}), 403
    return send_from_directory('.', filename)


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

@app.route('/api/share', methods=['POST'])
def create_share_link():
    try:
        data = request.json
        if not data:
            return jsonify({'error': 'No data provided'}), 400

        # Generate 15-char random URL-safe ID with special chars
        # Using A-Z, a-z, 0-9, -, _, ., ~
        chars = string.ascii_letters + string.digits + "-_.~"
        share_id = ''.join(secrets.choice(chars) for _ in range(15))
        
        file_path = os.path.join(SHARED_FOLDER, f"{share_id}.json")
        
        with open(file_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False)
            
        return jsonify({'success': True, 'id': share_id})
    except Exception as e:
        logger.error(f"Error creating share: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/share/<share_id>', methods=['GET'])
def get_shared_grades(share_id):
    try:
        # Basic validation for ID (alphanumeric + special chars)
        if not all(c in string.ascii_letters + string.digits + "-_.~" for c in share_id):
             return jsonify({'error': 'Invalid ID format'}), 400

        file_path = os.path.join(SHARED_FOLDER, f"{share_id}.json")
        if not os.path.exists(file_path):
            return jsonify({'error': 'Link expired or not found'}), 404
            
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
            
        return jsonify({'success': True, 'data': data})
    except Exception as e:
        logger.error(f"Error reading share: {e}")
        return jsonify({'error': str(e)}), 500
        
@app.route('/share/<share_id>')
def view_shared_page(share_id):
    # Just serve the index page, JS will handle the rest based on URL
    return send_from_directory('.', 'index.html')

if __name__ == '__main__':
    logger.info("Starting School Grades Server...")
    app.run(host='0.0.0.0', port=5000, debug=True)
