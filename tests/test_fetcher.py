import sys
import unittest
from unittest.mock import MagicMock

# Mock module imports that would cause circular dependencies or require app context
sys.modules['flask'] = MagicMock()
sys.modules['app'] = MagicMock()
sys.modules['app.services'] = MagicMock()
sys.modules['app.services.http_client'] = MagicMock()

# Now we can safely import GradeFetcher
from fetcher import GradeFetcher

class TestFetcherGetHiddenToken(unittest.TestCase):
    def test_get_hidden_token_success(self):
        html = '<html><body><input name="__RequestVerificationToken" value="secret_token_123"></body></html>'
        token = GradeFetcher._get_hidden_token(html)
        self.assertEqual(token, "secret_token_123")

    def test_get_hidden_token_missing_input(self):
        html = '<html><body><input name="otherToken" value="123"></body></html>'
        with self.assertRaisesRegex(RuntimeError, "找不到 __RequestVerificationToken hidden input"):
            GradeFetcher._get_hidden_token(html)

    def test_get_hidden_token_missing_value(self):
        html = '<html><body><input name="__RequestVerificationToken"></body></html>'
        with self.assertRaisesRegex(RuntimeError, "找不到 __RequestVerificationToken hidden input"):
            GradeFetcher._get_hidden_token(html)

    def test_get_hidden_token_empty_value(self):
        html = '<html><body><input name="__RequestVerificationToken" value=""></body></html>'
        with self.assertRaisesRegex(RuntimeError, "找不到 __RequestVerificationToken hidden input"):
            GradeFetcher._get_hidden_token(html)

    def test_get_hidden_token_empty_html(self):
        html = ''
        with self.assertRaisesRegex(RuntimeError, "找不到 __RequestVerificationToken hidden input"):
            GradeFetcher._get_hidden_token(html)

    def test_get_hidden_token_multiple_inputs(self):
        html = '''
        <html>
            <body>
                <input name="username" value="test">
                <input name="__RequestVerificationToken" value="the_real_token">
                <input name="password" value="pass">
            </body>
        </html>
        '''
        token = GradeFetcher._get_hidden_token(html)
        self.assertEqual(token, "the_real_token")

if __name__ == '__main__':
    unittest.main()
