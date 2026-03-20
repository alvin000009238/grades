
def get_structure(fetcher, cookies, student_no, token):
    return fetcher.get_structure_via_api(cookies, student_no, token)


SUBJECT_KEYS = ('SubjectName', 'ScoreDisplay', 'Score', 'ClassAVGScoreDisplay', 'ClassAVGScore', 'ClassRank', 'ClassRankCount', 'YearRank', 'YearRankCount', 'YearTermDisplay')
STD_KEYS = {'SubjectName', '頂標', '前標', '均標', '後標', '底標', '標準差'}
COUNT_KEYS = {'大於90Count', '大於80Count', '大於70Count', '大於60Count', '大於50Count', '大於40Count', '大於30Count', '大於20Count', '大於10Count', '大於0Count'}


def filter_grades_data(data):
    if not data or 'Result' not in data:
        return data

    result = data['Result']
    filtered_result = {
        'StudentName': result.get('StudentName'),
        'StudentClassName': result.get('StudentClassName'),
        'StudentSeatNo': result.get('StudentSeatNo'),
        'StudentNo': result.get('StudentNo'),
        'GetDataTimeDisplay': result.get('GetDataTimeDisplay'),
        'Show班級排名': result.get('Show班級排名'),
        'Show班級排名人數': result.get('Show班級排名人數'),
        'Show類組排名': result.get('Show類組排名'),
        'Show類組排名人數': result.get('Show類組排名人數'),
    }

    if 'ExamItem' in result and result['ExamItem']:
        exam = result['ExamItem']
        filtered_result['ExamItem'] = {
            'ExamName': exam.get('ExamName'),
            'ClassRank': exam.get('ClassRank'),
            'ClassCount': exam.get('ClassCount'),
            '類組排名': exam.get('類組排名'),
            '類組排名Count': exam.get('類組排名Count')
        }

    if 'SubjectExamInfoList' in result:
        filtered_result['SubjectExamInfoList'] = [
            {k: subject.get(k) for k in SUBJECT_KEYS}
            for subject in result['SubjectExamInfoList']
        ]

    if '成績五標List' in result:
        filtered_result['成績五標List'] = [
            {k: std.get(k, 0) if k in COUNT_KEYS else std.get(k) for k in STD_KEYS | COUNT_KEYS}
            for std in result['成績五標List']
        ]

    # 保留原本的 Message, Status 等外層結構
    data['Result'] = filtered_result
    return data


def fetch_grades(fetcher, cookies, student_no, token, year_value, exam_value):
    raw_data = fetcher.fetch_grades_via_api(cookies, student_no, token, year_value, exam_value)
    return filter_grades_data(raw_data)
