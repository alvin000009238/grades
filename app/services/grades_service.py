
def get_structure(fetcher, cookies, student_no, token):
    return fetcher.get_structure_via_api(cookies, student_no, token)


def fetch_grades(fetcher, cookies, student_no, token, year_value, exam_value):
    return fetcher.fetch_grades_via_api(cookies, student_no, token, year_value, exam_value)
