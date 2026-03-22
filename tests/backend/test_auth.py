from app.services.auth_service import is_logged_in, login_and_build_session_payload
from unittest.mock import Mock

def test_is_logged_in():
    assert is_logged_in({'api_cookies': 'c', 'api_token': 't'})
    assert not is_logged_in({'api_cookies': 'c'})
    assert not is_logged_in({})

def test_login_and_build_session_payload():
    fetcher = Mock()
    fetcher.login_and_get_tokens.return_value = (True, 'Success', 'cookies', '123', 'token')
    
    success, msg, payload = login_and_build_session_payload(fetcher, 'user', 'pass')
    assert success
    assert payload['api_cookies'] == 'cookies'
    assert payload['student_no'] == '123'
    assert payload['api_token'] == 'token'

def test_login_and_build_session_payload_fail():
    fetcher = Mock()
    fetcher.login_and_get_tokens.return_value = (False, 'Failed to login', None, None, None)
    
    success, msg, payload = login_and_build_session_payload(fetcher, 'user', 'pass')
    assert not success
    assert msg == 'Failed to login'
    assert payload is None
