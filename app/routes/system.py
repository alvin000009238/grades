import os

from flask import Blueprint, current_app, jsonify, send_from_directory

bp = Blueprint('system', __name__)

ALLOWED_STATIC_EXT = {
    '.html', '.css', '.js', '.json', '.png', '.jpg', '.jpeg', '.gif',
    '.svg', '.ico', '.woff', '.woff2', '.ttf', '.xml', '.txt',
}


@bp.route('/')
def index():
    current_app.config['LOGGER'].debug('Accessing index page')
    response = send_from_directory('public', 'index.html')
    response.headers['Cache-Control'] = 'no-cache, no-store, must-revalidate'
    response.headers['Pragma'] = 'no-cache'
    response.headers['Expires'] = '0'
    return response


@bp.route('/<path:filename>')
def static_files(filename):
    ext = os.path.splitext(filename)[1].lower()
    if ext not in ALLOWED_STATIC_EXT:
        return jsonify({'error': 'Forbidden'}), 403

    if any(part.startswith('.') for part in filename.split('/')):
        return jsonify({'error': 'Forbidden'}), 403

    response = send_from_directory('public', filename)
    if ext in ('.js', '.css', '.woff', '.woff2', '.ttf', '.png', '.jpg', '.jpeg', '.gif', '.svg', '.ico'):
        response.cache_control.max_age = 86400
    return response


@bp.route('/health')
def health_check():
    return jsonify({'status': 'ok'}), 200


@bp.route('/api/turnstile-config')
def turnstile_config():
    site_key = current_app.config.get('TURNSTILE_SITE_KEY', '')
    return jsonify({'siteKey': site_key})
