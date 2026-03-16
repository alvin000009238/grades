import pytest
import sys

# Create a mock structure for app.services
class MockServices:
    pass

class MockApp:
    services = MockServices()

class MockHttpClient:
    def get_http_session(self):
        pass

# Add all required mocks to sys.modules to avoid circular imports and missing redis dependency in app/__init__.py
sys.modules['app'] = MockApp()
sys.modules['app.services'] = MockServices()
sys.modules['app.services.http_client'] = MockHttpClient()

# Now we can safely import fetcher
from fetcher import GradeFetcher

def test_get_hidden_token_success():
    """Test successful extraction of the hidden token."""
    html = '<input name="__RequestVerificationToken" value="test_token_123" />'
    token = GradeFetcher._get_hidden_token(html)
    assert token == "test_token_123"

def test_get_hidden_token_missing_element():
    """Test behavior when the hidden input element is missing."""
    html = '<div>No token here</div>'
    with pytest.raises(RuntimeError, match="找不到 __RequestVerificationToken hidden input"):
        GradeFetcher._get_hidden_token(html)

def test_get_hidden_token_missing_value():
    """Test behavior when the hidden input lacks a value attribute."""
    html = '<input name="__RequestVerificationToken" />'
    with pytest.raises(RuntimeError, match="找不到 __RequestVerificationToken hidden input"):
        GradeFetcher._get_hidden_token(html)

def test_get_hidden_token_empty_value():
    """Test behavior when the hidden input has an empty value."""
    html = '<input name="__RequestVerificationToken" value="" />'
    with pytest.raises(RuntimeError, match="找不到 __RequestVerificationToken hidden input"):
        GradeFetcher._get_hidden_token(html)
