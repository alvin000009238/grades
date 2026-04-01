from app.services.share_service import generate_share_id, is_valid_share_id, validate_share_payload

def test_generate_share_id():
    sid = generate_share_id(15)
    assert len(sid) == 15
    assert is_valid_share_id(sid)


def test_is_valid_share_id_rejects_invalid_length():
    assert not is_valid_share_id('a' * 14)
    assert not is_valid_share_id('a' * 16)

def test_validate_share_payload_invalid():
    # Not a dict
    is_valid, err, _ = validate_share_payload([])
    assert not is_valid
    assert err == 'Payload 必須為 JSON 物件'

    # Missing Result
    is_valid, err, _ = validate_share_payload({})
    assert not is_valid
    assert err == '缺少 Result 資料'
    
    # Missing SubjectExamInfoList
    is_valid, err, _ = validate_share_payload({'Result': {}})
    assert not is_valid
    assert err == '缺少 SubjectExamInfoList 成績清單'

def test_validate_share_payload_valid():
    payload = {
        'Result': {
            'SubjectExamInfoList': []
        },
        'turnstile_token': 'abc'
    }
    is_valid, err, cleaned = validate_share_payload(payload)
    assert is_valid
    assert err is None
    assert 'turnstile_token' not in cleaned
    assert 'Result' in cleaned
