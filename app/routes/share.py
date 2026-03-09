from flask import Blueprint, current_app, jsonify, request, send_from_directory

from app.services.share_service import (
    generate_share_id,
    is_valid_share_id,
    read_shared_data,
    write_shared_data,
)
from app.services.turnstile_service import verify_turnstile

bp = Blueprint('share', __name__)


@bp.route('/api/share', methods=['POST'])
def create_share_link():
    try:
        data = request.json
        if not data:
            return jsonify({'error': 'No data provided'}), 400

        turnstile_token = data.pop('cf-turnstile-response', '')
        ok, err = verify_turnstile(
            turnstile_token,
            request.remote_addr,
            current_app.config['TURNSTILE_SECRET_KEY'],
            current_app.config['LOGGER'],
            context='share',
        )
        if not ok:
            return err

        share_id = generate_share_id()
        write_shared_data(current_app.config['SHARED_FOLDER'], share_id, data)
        return jsonify({'success': True, 'id': share_id})
    except Exception as exc:
        current_app.config['LOGGER'].error(f'Error creating share: {exc}', exc_info = True)
        return jsonify({'error': str(exc)}), 500


@bp.route('/api/share/<share_id>', methods=['GET'])
def get_shared_grades(share_id):
    try:
        if not is_valid_share_id(share_id):
            return jsonify({'error': 'Invalid ID format'}), 400

        data = read_shared_data(current_app.config['SHARED_FOLDER'], share_id)
        if data is None:
            return jsonify({'error': 'Link expired or not found'}), 404

        return jsonify({'success': True, 'data': data})
    except Exception as exc:
        current_app.config['LOGGER'].error(f'Error reading share: {exc}', exc_info = True)
        return jsonify({'error': str(exc)}), 500


@bp.route('/share/<share_id>')
def view_shared_page(share_id):
    return send_from_directory('public', 'index.html')
