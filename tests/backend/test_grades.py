from app.services.grades_service import filter_grades_data

def test_filter_grades_data_empty():
    assert filter_grades_data({}) == {}
    assert filter_grades_data({'Missing': 'Result'}) == {'Missing': 'Result'}

def test_filter_grades_data_valid():
    raw_data = {
        'Result': {
            'StudentName': 'Test',
            'SubjectExamInfoList': [
                {'SubjectName': 'Math', 'Score': 90, 'YearRank': 1, 'YearRankCount': 100}
            ],
            '成績五標List': [
                {'SubjectName': 'Math', '頂標': 88}
            ]
        }
    }
    filtered = filter_grades_data(raw_data)
    
    assert filtered['Result']['StudentName'] == 'Test'
    assert len(filtered['Result']['SubjectExamInfoList']) == 1
    assert filtered['Result']['SubjectExamInfoList'][0]['SubjectName'] == 'Math'
    assert filtered['Result']['成績五標List'][0]['頂標'] == 88
