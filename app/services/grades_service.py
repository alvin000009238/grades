
def get_structure(fetcher, cookies, student_no, token):
    return fetcher.get_structure_via_api(cookies, student_no, token)


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
            {
                'SubjectName': subject.get('SubjectName'),
                'ScoreDisplay': subject.get('ScoreDisplay'),
                'Score': subject.get('Score'),
                'ClassAVGScoreDisplay': subject.get('ClassAVGScoreDisplay'),
                'ClassAVGScore': subject.get('ClassAVGScore'),
                'ClassRank': subject.get('ClassRank'),
                'ClassRankCount': subject.get('ClassRankCount'),
                'YearRank': subject.get('YearRank'),
                'YearRankCount': subject.get('YearRankCount'),
                'YearTermDisplay': subject.get('YearTermDisplay')
            }
            for subject in result['SubjectExamInfoList']
        ]

    if '成績五標List' in result:
        filtered_result['成績五標List'] = [
            {
                'SubjectName': std.get('SubjectName'),
                '頂標': std.get('頂標'),
                '前標': std.get('前標'),
                '均標': std.get('均標'),
                '後標': std.get('後標'),
                '底標': std.get('底標'),
                '標準差': std.get('標準差'),
                '大於90Count': std.get('大於90Count', 0),
                '大於80Count': std.get('大於80Count', 0),
                '大於70Count': std.get('大於70Count', 0),
                '大於60Count': std.get('大於60Count', 0),
                '大於50Count': std.get('大於50Count', 0),
                '大於40Count': std.get('大於40Count', 0),
                '大於30Count': std.get('大於30Count', 0),
                '大於20Count': std.get('大於20Count', 0),
                '大於10Count': std.get('大於10Count', 0),
                '大於0Count': std.get('大於0Count', 0)
            }
            for std in result['成績五標List']
        ]

    # 保留原本的 Message, Status 等外層結構
    data['Result'] = filtered_result
    return data


def fetch_grades(fetcher, cookies, student_no, token, year_value, exam_value):
    raw_data = fetcher.fetch_grades_via_api(cookies, student_no, token, year_value, exam_value)
    return filter_grades_data(raw_data)
