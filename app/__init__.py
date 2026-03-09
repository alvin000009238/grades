import os

from flask import Flask
from werkzeug.middleware.proxy_fix import ProxyFix

from app.extensions import configure_logger, cors
from app.routes import auth_bp, grades_bp, share_bp, system_bp
from app.services.share_service import ensure_shared_folder, start_cleanup_thread
from fetcher import GradeFetcher


def create_app():
    app = Flask(__name__, static_folder='.')

    app.secret_key = os.environ.get('SECRET_KEY', os.urandom(24).hex())
    app.config['MAX_CONTENT_LENGTH'] = 2 * 1024 * 1024
    app.config['SESSION_COOKIE_SAMESITE'] = 'Lax'
    app.config['SESSION_COOKIE_SECURE'] = True
    app.config['SESSION_COOKIE_HTTPONLY'] = True

    app.config['TURNSTILE_SECRET_KEY'] = os.environ.get('TURNSTILE_SECRET_KEY', '')
    app.config['TURNSTILE_SITE_KEY'] = os.environ.get('TURNSTILE_SITE_KEY', '')
    app.config['SHARED_FOLDER'] = 'shared_grades'
    app.config['CLEANUP_INTERVAL'] = 600
    app.config['FILE_LIFETIME'] = 7200
    app.config['GRADE_FETCHER'] = GradeFetcher

    app.wsgi_app = ProxyFix(app.wsgi_app, x_for=1, x_proto=1, x_host=1, x_prefix=1)

    cors.init_app(app, supports_credentials=True)

    logger = configure_logger()
    app.config['LOGGER'] = logger

    ensure_shared_folder(app.config['SHARED_FOLDER'])
    start_cleanup_thread(app, logger)

    app.register_blueprint(auth_bp)
    app.register_blueprint(grades_bp)
    app.register_blueprint(share_bp)
    app.register_blueprint(system_bp)

    return app
