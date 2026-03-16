import unittest
import string
import sys
import os

# To avoid missing redis dependency in app/__init__.py, add services directly to sys.path
# and import the service module directly.
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../../services')))

from share_service import generate_share_id, CHARS

class TestShareService(unittest.TestCase):
    def test_generate_share_id_default_length(self):
        """Test that default length is 15."""
        share_id = generate_share_id()
        self.assertEqual(len(share_id), 15)

    def test_generate_share_id_custom_length(self):
        """Test generating a share ID with a custom length."""
        share_id = generate_share_id(length=20)
        self.assertEqual(len(share_id), 20)

    def test_generate_share_id_zero_length(self):
        """Test generating a share ID with zero length."""
        share_id = generate_share_id(length=0)
        self.assertEqual(len(share_id), 0)
        self.assertEqual(share_id, '')

    def test_generate_share_id_characters(self):
        """Test that only valid characters are used."""
        share_id = generate_share_id(length=100)
        for char in share_id:
            self.assertIn(char, CHARS)

    def test_generate_share_id_randomness(self):
        """Test that consecutive generated IDs are likely different."""
        id1 = generate_share_id()
        id2 = generate_share_id()
        self.assertNotEqual(id1, id2)

if __name__ == '__main__':
    unittest.main()
