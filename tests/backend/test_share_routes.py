import json
import pytest

from app import create_app


class FakeRedis:
    def __init__(self):
        self.store = {}
        self.ttl_map = {}
        self.counter = {}
        self.setex_calls = []

    def ping(self):
        return True

    def setex(self, key, ttl, value):
        self.store[key] = value
        self.ttl_map[key] = ttl
        self.setex_calls.append((key, ttl, value))

    def get(self, key):
        return self.store.get(key)

    def expire(self, key, ttl):
        if key in self.store:
            self.ttl_map[key] = ttl
            return 1
        return 0

    def incr(self, key):
        self.counter[key] = self.counter.get(key, 0) + 1
        return self.counter[key]

    def ttl(self, key):
        return self.ttl_map.get(key, -1)


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setenv('APP_ENV', 'testing')
    app = create_app()
    app.config['TESTING'] = True
    app.config['SHARE_TTL'] = 7200
    app.config['REDIS_CLIENT'] = FakeRedis()

    monkeypatch.setattr('app.routes.share.verify_turnstile_token', lambda *_args, **_kwargs: (True, None))

    with app.test_client() as client:
        yield client


def _share_payload():
    return {
        'Result': {
            'SubjectExamInfoList': []
        },
        'turnstile_token': 'test-token'
    }


def test_share_create_and_update_by_owner_resets_ttl(client):
    with client.session_transaction() as sess:
        sess['student_no'] = 'A123'

    create_res = client.post('/api/share', json=_share_payload())
    assert create_res.status_code == 200
    body = create_res.get_json()
    assert body['success'] is True
    share_id = body['id']

    redis_client = client.application.config['REDIS_CLIENT']
    assert json.loads(redis_client.get(f'share_meta:{share_id}'))['creator_student_no'] == 'A123'

    redis_client.ttl_map[f'share:{share_id}'] = 100
    redis_client.ttl_map[f'share_meta:{share_id}'] = 100

    update_res = client.put(f'/api/share/{share_id}', json=_share_payload())
    assert update_res.status_code == 200
    assert update_res.get_json()['success'] is True

    assert redis_client.ttl(f'share:{share_id}') == 7200
    assert redis_client.ttl(f'share_meta:{share_id}') == 7200


def test_share_update_by_other_user_forbidden(client):
    with client.session_transaction() as sess:
        sess['student_no'] = 'A123'

    create_res = client.post('/api/share', json=_share_payload())
    share_id = create_res.get_json()['id']

    with client.session_transaction() as sess:
        sess['student_no'] = 'B456'

    update_res = client.put(f'/api/share/{share_id}', json=_share_payload())
    assert update_res.status_code == 403
    assert update_res.get_json()['error'] == 'Forbidden'


def test_share_update_requires_login(client):
    with client.session_transaction() as sess:
        sess['student_no'] = 'A123'

    create_res = client.post('/api/share', json=_share_payload())
    share_id = create_res.get_json()['id']

    with client.session_transaction() as sess:
        sess.pop('student_no', None)

    update_res = client.put(f'/api/share/{share_id}', json=_share_payload())
    assert update_res.status_code == 401
    assert update_res.get_json()['error'] == 'Unauthorized'


def test_share_update_forbidden_when_metadata_has_no_owner(client):
    with client.session_transaction() as sess:
        sess['student_no'] = 'A123'

    create_res = client.post('/api/share', json=_share_payload())
    share_id = create_res.get_json()['id']

    redis_client = client.application.config['REDIS_CLIENT']
    redis_client.setex(f'share_meta:{share_id}', 7200, json.dumps({'created_at': '2026-01-01T00:00:00+00:00'}))

    update_res = client.put(f'/api/share/{share_id}', json=_share_payload())
    assert update_res.status_code == 403
    assert update_res.get_json()['error'] == 'Forbidden'


def test_share_create_requires_login(client):
    with client.session_transaction() as sess:
        sess.pop('student_no', None)

    create_res = client.post('/api/share', json=_share_payload())
    assert create_res.status_code == 401
    assert create_res.get_json()['error'] == 'Unauthorized'


def test_share_update_rate_limited(client):
    with client.session_transaction() as sess:
        sess['student_no'] = 'A123'

    create_res = client.post('/api/share', json=_share_payload())
    share_id = create_res.get_json()['id']

    last_response = None
    for _ in range(11):
        last_response = client.put(f'/api/share/{share_id}', json=_share_payload())

    assert last_response is not None
    assert last_response.status_code == 429
    assert 'Retry-After' in last_response.headers


def test_share_create_returns_503_when_redis_unavailable(client):
    with client.session_transaction() as sess:
        sess['student_no'] = 'A123'

    client.application.config['REDIS_CLIENT'] = None
    create_res = client.post('/api/share', json=_share_payload())

    assert create_res.status_code == 503
    assert create_res.get_json()['error'] == 'Share service unavailable'


def test_share_update_returns_503_when_redis_unavailable(client):
    with client.session_transaction() as sess:
        sess['student_no'] = 'A123'

    create_res = client.post('/api/share', json=_share_payload())
    share_id = create_res.get_json()['id']

    client.application.config['REDIS_CLIENT'] = None
    update_res = client.put(f'/api/share/{share_id}', json=_share_payload())

    assert update_res.status_code == 503
    assert update_res.get_json()['error'] == 'Share service unavailable'


def test_share_get_returns_503_when_redis_unavailable(client):
    client.application.config['REDIS_CLIENT'] = None
    get_res = client.get('/api/share/aaaaaaaaaaaaaaa')

    assert get_res.status_code == 503
    assert get_res.get_json()['error'] == 'Share service unavailable'


def test_share_update_returns_409_when_metadata_ttl_refresh_fails(client):
    with client.session_transaction() as sess:
        sess['student_no'] = 'A123'

    create_res = client.post('/api/share', json=_share_payload())
    share_id = create_res.get_json()['id']

    redis_client = client.application.config['REDIS_CLIENT']
    redis_client.expire = lambda *_args, **_kwargs: 0

    update_res = client.put(f'/api/share/{share_id}', json=_share_payload())

    assert update_res.status_code == 409
    assert update_res.get_json()['error'] == 'Share metadata state conflict'
