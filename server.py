from flask import Flask, send_from_directory, jsonify, request, session
from flask_cors import CORS
from werkzeug.middleware.proxy_fix import ProxyFix
import json
import os
import requests as http_requests
import logging
from logging.handlers import RotatingFileHandler
from redis import Redis
from fetcher import GradeFetcher
from app.services.share_service import ShareService, is_valid_share_id
from app.services.share_store_redis import RedisShareStore

SHARE_TTL_SECONDS = int(os.environ.get('SHARE_TTL_SECONDS', '7200'))
REDIS_URL = os.environ.get('REDIS_URL', 'redis://redis:6379/0')

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

# Turnstile 設定
TURNSTILE_SECRET_KEY = os.environ.get('TURNSTILE_SECRET_KEY', '')
TURNSTILE_SITE_KEY = os.environ.get('TURNSTILE_SITE_KEY', '')

def verify_turnstile(token, remote_ip, context=''):
    """共用 Turnstile 驗證函式。回傳 (success: bool, error_response or None)。"""
    if not TURNSTILE_SECRET_KEY:
        return True, None
    if not token:
        return False, (jsonify({'success': False, 'message': '請完成人機驗證'}), 400)
    try:
        verify_res = http_requests.post(
            'https://challenges.cloudflare.com/turnstile/v0/siteverify',
            data={
                'secret': TURNSTILE_SECRET_KEY,
                'response': token,
                'remoteip': remote_ip
            },
            timeout=10
        )
        verify_data = verify_res.json()
        if not verify_data.get('success'):
            logger.warning(f"Turnstile verification failed ({context}): {verify_data}")
            return False, (jsonify({'success': False, 'message': '人機驗證失敗，請重試'}), 403)
    except Exception as e:
        logger.error(f"Turnstile verification error ({context}): {e}")
        return False, (jsonify({'success': False, 'message': '驗證服務暫時無法使用，請稍後再試'}), 500)
    return True, None


redis_client = Redis.from_url(REDIS_URL)
share_service = ShareService(store=RedisShareStore(redis_client), ttl_seconds=SHARE_TTL_SECONDS)

@app.route('/')
def index():
    logger.info("Accessing index page")
    return send_from_directory('public', 'index.html')

ALLOWED_STATIC_EXT = {'.html', '.css', '.js', '.json', '.png', '.jpg', '.jpeg', '.gif', '.svg', '.ico', '.woff', '.woff2', '.ttf', '.xml', '.txt'}

@app.route('/<path:filename>')
def static_files(filename):
    # Block sensitive files
    ext = os.path.splitext(filename)[1].lower()
    if ext not in ALLOWED_STATIC_EXT:
        return jsonify({'error': 'Forbidden'}), 403
    # Block dotfiles and sensitive paths
    if any(part.startswith('.') for part in filename.split('/')):
        return jsonify({'error': 'Forbidden'}), 403
    return send_from_directory('public', filename)


@app.route('/api/turnstile-site-key')
def get_turnstile_site_key():
    return jsonify({'siteKey': TURNSTILE_SITE_KEY})

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
    
    # Turnstile 驗證
    turnstile_token = data.get('cf-turnstile-response', '')
    ok, err = verify_turnstile(turnstile_token, request.remote_addr, context=f'login:{username}')
    if not ok:
        return err
        
    try:
        success, message, cookies, student_no, token = GradeFetcher.login_and_get_tokens(username, data.get('password'))
        
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

        # Turnstile 驗證
        turnstile_token = data.pop('cf-turnstile-response', '')
        ok, err = verify_turnstile(turnstile_token, request.remote_addr, context='share')
        if not ok:
            return err

        share_id = share_service.create_share(data)
            
        return jsonify({'success': True, 'id': share_id})
    except Exception as e:
        logger.error(f"Error creating share: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/share/<share_id>', methods=['GET'])
def get_shared_grades(share_id):
    try:
        if not is_valid_share_id(share_id):
             return jsonify({'error': 'Invalid ID format'}), 400

        data = share_service.get_share(share_id)
        if data is None:
            return jsonify({'error': 'Link expired or not found'}), 404
            
        return jsonify({'success': True, 'data': data})
    except Exception as e:
        logger.error(f"Error reading share: {e}")
        return jsonify({'error': str(e)}), 500
        
@app.route('/share/<share_id>')
def view_shared_page(share_id):
    # Just serve the index page, JS will handle the rest based on URL
    return send_from_directory('public', 'index.html')

@app.route('/health')
def health_check():
    return jsonify({'status': 'ok'}), 200

if __name__ == '__main__':
    logger.info("Starting School Grades Server...")
    app.run(host='0.0.0.0', port=5000, debug=True)
