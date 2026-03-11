import os
import redis

from flask import Flask
from werkzeug.middleware.proxy_fix import ProxyFix
from flask_session import Session

from app.extensions import configure_logger, cors
from app.routes import auth_bp, grades_bp, share_bp, system_bp
from fetcher import GradeFetcher


def create_app():
    root_path = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    app = Flask(__name__, static_folder='.', root_path=root_path)

    secret_key = os.environ.get('SECRET_KEY')
    if not secret_key:
        flask_env = os.environ.get('FLASK_ENV', '').lower()
        app_env = os.environ.get('APP_ENV', '').lower()
        if flask_env in ('development', 'testing') or app_env in ('development', 'testing'):
            secret_key = os.urandom(24).hex()
        else:
            raise RuntimeError("SECRET_KEY environment variable must be set in production.")
    app.secret_key = secret_key
    app.config['MAX_CONTENT_LENGTH'] = 2 * 1024 * 1024
    app.config['SESSION_COOKIE_SAMESITE'] = 'Lax'
    app.config['SESSION_COOKIE_SECURE'] = True
    app.config['SESSION_COOKIE_HTTPONLY'] = True

    redis_url = os.environ.get('REDIS_URL', 'redis://127.0.0.1:6379/0')
    redis_client = redis.from_url(redis_url)
    
    flask_env = os.environ.get('FLASK_ENV', '').lower()
    app_env = os.environ.get('APP_ENV', '').lower()

    try:
        redis_client.ping()
        app.config['REDIS_CLIENT'] = redis_client
        app.config['SESSION_TYPE'] = 'redis'
        app.config['SESSION_REDIS'] = redis_client
        app.config['SESSION_USE_SIGNER'] = False
        app.config['SESSION_KEY_PREFIX'] = 'session:'
        app.config['PERMANENT_SESSION_LIFETIME'] = 86400  # 1 day
        Session(app)
    except redis.exceptions.ConnectionError:
        app.config['REDIS_CLIENT'] = None
        # 退回不使用 Redis session
        app.config['SESSION_TYPE'] = 'null'
        Session(app)
        print("WARNING: Redis not available. Using default cookie session for development.")

    app.config['TURNSTILE_SITE_KEY'] = os.environ.get('TURNSTILE_SITE_KEY', '')
    app.config['TURNSTILE_SECRET_KEY'] = os.environ.get('TURNSTILE_SECRET_KEY', '')

    app.config['SHARE_TTL'] = int(os.environ.get('SHARE_TTL', 7200))

    app.config['GRADE_FETCHER'] = GradeFetcher

    app.wsgi_app = ProxyFix(app.wsgi_app, x_for=1, x_proto=1, x_host=1, x_prefix=1)

    allowed_origins = os.environ.get('CORS_ORIGINS', 'http://localhost:5000,http://127.0.0.1:5000').split(',')
    cors.init_app(app, supports_credentials=True, origins=allowed_origins)

    logger = configure_logger()
    app.config['LOGGER'] = logger

    app.register_blueprint(auth_bp)
    app.register_blueprint(grades_bp)
    app.register_blueprint(share_bp)
    app.register_blueprint(system_bp)

    @app.after_request
    def add_cache_headers(response):
        # response may not have mimetype attribute or it could be None
        # using request.path to check the file extension is safer
        from flask import request
        if request.path.endswith('.woff2'):
            response.headers['Cache-Control'] = 'public, max-age=31536000, immutable'
        return response

    return app
