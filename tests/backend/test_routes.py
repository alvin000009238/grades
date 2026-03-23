import pytest
import os
from unittest.mock import patch
from app import create_app

@pytest.fixture
def client():
    # Set APP_ENV to testing so create_app won't complain about SECRET_KEY
    os.environ['APP_ENV'] = 'testing'
    with patch('app.Session.init_app'):
        app = create_app()
        app.config['TESTING'] = True
        with app.test_client() as client:
            yield client

def test_health_check(client):
    rv = client.get('/health')
    assert rv.status_code == 200

def test_index_redirect_or_serve(client):
    rv = client.get('/')
    # It might respond with 200 if public/index.html is present, or 404 if not
    assert rv.status_code in [200, 404]
