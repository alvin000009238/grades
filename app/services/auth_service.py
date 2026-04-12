

def login_and_build_session_payload(fetcher, username, password, captcha_code=None, login_context=None):
    success, message, cookies, student_no, token = fetcher.login_and_get_tokens(
        username,
        password,
        captcha_code=captcha_code,
        login_context=login_context,
    )

    if not success:
        return False, message, None

    payload = {
        'username': username,
        'api_cookies': cookies,
        'student_no': student_no,
        'api_token': token,
    }

    return True, message, payload


def is_logged_in(sess):
    return bool(sess.get('api_cookies') and sess.get('api_token'))
