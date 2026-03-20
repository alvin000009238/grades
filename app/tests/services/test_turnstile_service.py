import unittest
import sys
from unittest.mock import patch, MagicMock
from flask import Flask

# Mock redis module before importing anything from app
sys.modules['redis'] = MagicMock()
sys.modules['flask_session'] = MagicMock()

from app.services.turnstile_service import verify_turnstile_token

class TestTurnstileService(unittest.TestCase):
    def setUp(self):
        self.app = Flask(__name__)
        self.app.config['TURNSTILE_SECRET_KEY'] = 'fake_secret_key'
        self.app.config['LOGGER'] = MagicMock()
        self.app_context = self.app.app_context()
        self.app_context.push()

    def tearDown(self):
        self.app_context.pop()

    @patch('app.services.turnstile_service.http_requests.post')
    def test_verify_turnstile_token_success(self, mock_post):
        mock_resp = MagicMock()
        mock_resp.json.return_value = {'success': True}
        mock_post.return_value = mock_resp

        success, msg = verify_turnstile_token('valid_token')
        self.assertTrue(success)
        self.assertIsNone(msg)
        mock_post.assert_called_once()

    @patch('app.services.turnstile_service.http_requests.post')
    def test_verify_turnstile_token_failure(self, mock_post):
        mock_resp = MagicMock()
        mock_resp.json.return_value = {'success': False, 'error-codes': ['invalid-input-response']}
        mock_post.return_value = mock_resp

        success, msg = verify_turnstile_token('invalid_token')
        self.assertFalse(success)
        self.assertEqual(msg, '人機驗證失敗，請重試')
        self.app.config['LOGGER'].warning.assert_called_once()

    def test_verify_turnstile_token_missing_token(self):
        success, msg = verify_turnstile_token('')
        self.assertFalse(success)
        self.assertEqual(msg, '缺少人機驗證 token')

    def test_verify_turnstile_token_no_secret_key(self):
        self.app.config['TURNSTILE_SECRET_KEY'] = None
        success, msg = verify_turnstile_token('some_token')
        self.assertTrue(success)
        self.assertIsNone(msg)
        self.app.config['LOGGER'].warning.assert_called_once()

    @patch('app.services.turnstile_service.http_requests.post')
    def test_verify_turnstile_token_exception(self, mock_post):
        mock_post.side_effect = Exception("Network Error")

        success, msg = verify_turnstile_token('some_token')
        self.assertFalse(success)
        self.assertEqual(msg, '驗證服務異常，請稍後再試')
        self.app.config['LOGGER'].error.assert_called_once()
        args, kwargs = self.app.config['LOGGER'].error.call_args
        self.assertTrue('Turnstile verification error: Network Error' in args[0])
        self.assertTrue(kwargs.get('exc_info'))

if __name__ == '__main__':
    unittest.main()
