FROM mcr.microsoft.com/playwright/python:v1.40.0-jammy

WORKDIR /app

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Create necessary directories
RUN mkdir -p sessions

# Expose port
EXPOSE 5000

# Use gunicorn for production
# --timeout 120: Playwright operations (login, fetching grades) can take time
# --workers 2: Keep low since Playwright is memory-intensive
CMD ["gunicorn", "--bind", "0.0.0.0:5000", "--timeout", "120", "--workers", "2", "--threads", "4", "server:app"]
