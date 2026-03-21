with open('app/__init__.py', 'r') as f:
    content = f.read()

content = content.replace("app.config['SESSION_TYPE'] = 'null'", "app.config['SESSION_TYPE'] = 'filesystem'\n        app.config['SESSION_FILE_DIR'] = os.path.join(app.root_path, 'flask_session')")

with open('app/__init__.py', 'w') as f:
    f.write(content)
