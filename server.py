from app import create_app
from app.services.share_service import start_cleanup_thread

app = create_app()


if __name__ == '__main__':
    logger = app.config['LOGGER']
    start_cleanup_thread(app, logger)
    logger.info('Starting School Grades Server...')
    app.run(host='0.0.0.0', port=5000, debug=True)
