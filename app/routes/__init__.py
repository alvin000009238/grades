from app.routes.auth import bp as auth_bp
from app.routes.grades import bp as grades_bp
from app.routes.share import bp as share_bp
from app.routes.system import bp as system_bp

__all__ = ['auth_bp', 'grades_bp', 'share_bp', 'system_bp']
