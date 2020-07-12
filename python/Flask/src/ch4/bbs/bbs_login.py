from flask import session, redirect

# ログイン用ユーザの定義
USERLIST = {
    'taro': 'aaa',
    'jiro': 'bbb',
    'sabu': 'ccc',
}

# ログインしているか調べる
def is_login():
    return 'login' in session

# ログイン処理
def try_login(user, password):
    # 該当ユーザがいるか
    if user not in USERLIST: return False
    # パスワードがあっているか
    if USERLIST[user] != password: return False
    # ログイン処理
    session['login'] = user
    return True

# ログアウト処理
def try_logout():
    session.pop('login', None)
    return True

# セッションからユーザ名取得
def get_user():
    if is_login(): return session['login']
    return 'not login'