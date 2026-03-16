import unittest
import sys
import os

# Ensure app/services/ can be imported directly
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../app/services')))

from grades_service import filter_grades_data

class TestGradesService(unittest.TestCase):
    def test_filter_grades_data_none(self):
        self.assertIsNone(filter_grades_data(None))

    def test_filter_grades_data_empty_dict(self):
        self.assertEqual(filter_grades_data({}), {})

    def test_filter_grades_data_missing_result(self):
        self.assertEqual(filter_grades_data({"other": "data"}), {"other": "data"})

    def test_filter_grades_data_result_none(self):
        self.assertEqual(filter_grades_data({"Result": None}), {"Result": None})

    def test_filter_grades_data_not_dict(self):
        self.assertEqual(filter_grades_data("string data"), "string data")
        self.assertEqual(filter_grades_data(["list data"]), ["list data"])

    def test_filter_grades_data_result_not_dict(self):
        self.assertEqual(filter_grades_data({"Result": "string result"}), {"Result": "string result"})

    def test_filter_grades_data_valid(self):
        valid_data = {
            "Result": {
                "StudentName": "John Doe",
                "ExamItem": {"ExamName": "Midterm", "ClassRank": 1},
                "SubjectExamInfoList": [{"SubjectName": "Math", "Score": 95}],
                "成績五標List": [{"SubjectName": "Math", "頂標": 90}]
            }
        }
        filtered = filter_grades_data(valid_data)
        self.assertIn("Result", filtered)
        self.assertEqual(filtered["Result"]["StudentName"], "John Doe")
        self.assertEqual(filtered["Result"]["ExamItem"]["ExamName"], "Midterm")
        self.assertEqual(filtered["Result"]["SubjectExamInfoList"][0]["Score"], 95)
        self.assertEqual(filtered["Result"]["成績五標List"][0]["頂標"], 90)

    def test_filter_grades_data_invalid_lists(self):
        data = {
            "Result": {
                "ExamItem": "not a dict",
                "SubjectExamInfoList": None,
                "成績五標List": "not a list"
            }
        }
        filtered = filter_grades_data(data)
        self.assertIn("Result", filtered)

if __name__ == '__main__':
    unittest.main()
