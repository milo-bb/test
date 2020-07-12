from flask import Flask, render_template

# Flaskのインスタンスを作成
app = Flask(__name__)
@app.route('/')
def index():
    # データ設定
    username = "ユスケ"
    age = 20
    email = "yusuke@example.com"
    return render_template("card-age.html", username=username, age=age, email=email)

# 実行する:
if __name__ == '__main__':
    app.run(host='127.0.0.1')