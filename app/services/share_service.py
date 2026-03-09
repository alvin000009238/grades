import json
import os
import secrets
import string
import threading
import time

CHARS = string.ascii_letters + string.digits + '-_.~'


def ensure_shared_folder(shared_folder):
    os.makedirs(shared_folder, exist_ok=True)


def generate_share_id(length=15):
    return ''.join(secrets.choice(CHARS) for _ in range(length))


def is_valid_share_id(share_id):
    return all(c in CHARS for c in share_id)


def write_shared_data(shared_folder, share_id, data):
    file_path = os.path.join(shared_folder, f'{share_id}.json')
    with open(file_path, 'w', encoding='utf-8') as file:
        json.dump(data, file, ensure_ascii=False)


def read_shared_data(shared_folder, share_id):
    file_path = os.path.join(shared_folder, f'{share_id}.json')
    if not os.path.exists(file_path):
        return None

    with open(file_path, 'r', encoding='utf-8') as file:
        return json.load(file)


def cleanup_expired_files(shared_folder, file_lifetime, logger):
    now = time.time()
    for filename in os.listdir(shared_folder):
        file_path = os.path.join(shared_folder, filename)
        if os.path.isfile(file_path) and now - os.path.getmtime(file_path) > file_lifetime:
            try:
                os.remove(file_path)
                logger.info(f'Deleted expired file: {filename}')
            except Exception as exc:
                logger.error(f'Error deleting file {filename}: {exc}')


def start_cleanup_thread(app, logger):
    shared_folder = app.config['SHARED_FOLDER']
    cleanup_interval = app.config['CLEANUP_INTERVAL']
    file_lifetime = app.config['FILE_LIFETIME']

    def cleanup_loop():
        while True:
            try:
                cleanup_expired_files(shared_folder, file_lifetime, logger)
            except Exception as exc:
                logger.error(f'Error in cleanup thread: {exc}')
            time.sleep(cleanup_interval)

    is_reloader_parent = app.debug and os.environ.get('WERKZEUG_RUN_MAIN') != 'true'
    if not is_reloader_parent:
        threading.Thread(target=cleanup_loop, daemon=True).start()
        logger.info('Cleanup thread started')
