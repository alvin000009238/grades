from app import create_app

app = create_app()

if __name__ == '__main__':
    logger = app.config['LOGGER']
    logger.info('Starting School Grades Server...')
    app.run(host='0.0.0.0', port=5000, debug=True)

