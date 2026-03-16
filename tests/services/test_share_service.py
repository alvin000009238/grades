import sys
import os
import unittest

# Add app/services to sys.path to avoid importing app/__init__.py and its redis dependency
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../../app/services')))

import share_service

class TestShareService(unittest.TestCase):

    def test_is_valid_share_id_happy_path(self):
        # Letters, digits, and allowed special characters: -_.~
        self.assertTrue(share_service.is_valid_share_id("abcDEF123-_.~"))
        self.assertTrue(share_service.is_valid_share_id("a1"))
        self.assertTrue(share_service.is_valid_share_id("valid-share_id.1~"))
        self.assertTrue(share_service.is_valid_share_id(share_service.generate_share_id()))

    def test_is_valid_share_id_empty_string(self):
        # An empty string evaluates to True with all()
        self.assertTrue(share_service.is_valid_share_id(""))

    def test_is_valid_share_id_invalid_characters(self):
        # Characters not in ascii_letters + digits + '-_.~'
        self.assertFalse(share_service.is_valid_share_id("invalid share id")) # Space
        self.assertFalse(share_service.is_valid_share_id("invalid!")) # Exclamation mark
        self.assertFalse(share_service.is_valid_share_id("invalid@")) # At symbol
        self.assertFalse(share_service.is_valid_share_id("invalid#")) # Hash
        self.assertFalse(share_service.is_valid_share_id("invalid$")) # Dollar
        self.assertFalse(share_service.is_valid_share_id("invalid%")) # Percent
        self.assertFalse(share_service.is_valid_share_id("invalid^")) # Caret
        self.assertFalse(share_service.is_valid_share_id("invalid&")) # Ampersand
        self.assertFalse(share_service.is_valid_share_id("invalid*")) # Asterisk
        self.assertFalse(share_service.is_valid_share_id("invalid(")) # Open parenthesis
        self.assertFalse(share_service.is_valid_share_id("invalid)")) # Close parenthesis
        self.assertFalse(share_service.is_valid_share_id("invalid+")) # Plus
        self.assertFalse(share_service.is_valid_share_id("invalid=")) # Equals
        self.assertFalse(share_service.is_valid_share_id("invalid{")) # Open brace
        self.assertFalse(share_service.is_valid_share_id("invalid}")) # Close brace
        self.assertFalse(share_service.is_valid_share_id("invalid[")) # Open bracket
        self.assertFalse(share_service.is_valid_share_id("invalid]")) # Close bracket
        self.assertFalse(share_service.is_valid_share_id("invalid|")) # Pipe
        self.assertFalse(share_service.is_valid_share_id("invalid\\")) # Backslash
        self.assertFalse(share_service.is_valid_share_id("invalid:")) # Colon
        self.assertFalse(share_service.is_valid_share_id("invalid;")) # Semicolon
        self.assertFalse(share_service.is_valid_share_id("invalid\"")) # Double quote
        self.assertFalse(share_service.is_valid_share_id("invalid'")) # Single quote
        self.assertFalse(share_service.is_valid_share_id("invalid<")) # Less than
        self.assertFalse(share_service.is_valid_share_id("invalid>")) # Greater than
        self.assertFalse(share_service.is_valid_share_id("invalid,")) # Comma
        self.assertFalse(share_service.is_valid_share_id("invalid?")) # Question mark
        self.assertFalse(share_service.is_valid_share_id("invalid/")) # Slash
        self.assertFalse(share_service.is_valid_share_id("測試")) # Non-ASCII characters

    def test_is_valid_share_id_none(self):
        # Passing None should raise a TypeError
        with self.assertRaises(TypeError):
            share_service.is_valid_share_id(None)

if __name__ == '__main__':
    unittest.main()
