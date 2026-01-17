"""
成績儀表板伺服器
提供靜態檔案服務、成績資料 API 和重新抓取功能
"""

from flask import Flask, send_from_directory, jsonify, request
from flask_cors import CORS
import subprocess
import json
import os

app = Flask(__name__, static_folder='.')
CORS(app)

GRADES_FILE = 'grades_raw.json'

# 提供靜態檔案
@app.route('/')
def index():
    return send_from_directory('.', 'grades_dashboard.html')

@app.route('/<path:filename>')
def static_files(filename):
    return send_from_directory('.', filename)

# 取得成績資料
@app.route('/api/grades')
def get_grades():
    try:
        with open(GRADES_FILE, 'r', encoding='utf-8') as f:
            data = json.load(f)
        return jsonify(data)
    except FileNotFoundError:
        return jsonify({'error': '找不到成績資料檔案'}), 404
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# 重新抓取成績
@app.route('/api/refresh', methods=['POST'])
def refresh_grades():
    try:
        # 執行 fetch_grades.py
        result = subprocess.run(
            ['python', 'fetch_grades.py'],
            capture_output=True,
            text=True,
            cwd=os.path.dirname(os.path.abspath(__file__)),
            timeout=60
        )
        
        if result.returncode == 0:
            # 讀取剛抓到的資料
            with open(GRADES_FILE, 'r', encoding='utf-8') as f:
                data = json.load(f)
            return jsonify({
                'success': True,
                'message': '成績已更新',
                'data': data
            })
        else:
            return jsonify({
                'success': False,
                'error': result.stderr or '抓取失敗'
            }), 500
    except subprocess.TimeoutExpired:
        return jsonify({
            'success': False,
            'error': '抓取超時，請稍後再試'
        }), 500
    except Exception as e:
        return jsonify({
            'success': False,
            'error': str(e)
        }), 500

# 上傳成績 JSON
@app.route('/api/upload', methods=['POST'])
def upload_grades():
    try:
        if 'file' in request.files:
            file = request.files['file']
            content = file.read().decode('utf-8')
            data = json.loads(content)
        elif request.is_json:
            data = request.get_json()
        else:
            return jsonify({'error': '請提供 JSON 檔案或資料'}), 400
        
        # 驗證資料結構
        if 'Result' not in data:
            return jsonify({'error': '無效的成績資料格式'}), 400
        
        return jsonify({
            'success': True,
            'data': data
        })
    except json.JSONDecodeError:
        return jsonify({'error': '無效的 JSON 格式'}), 400
    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    print("成績儀表板伺服器啟動中...")
    print("請在瀏覽器開啟: http://localhost:5000")
    app.run(host='0.0.0.0', port=5000, debug=True)
