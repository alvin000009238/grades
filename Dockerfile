FROM node:20-slim AS frontend-build

WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY vite.config.js ./
COPY frontend/ ./frontend/
COPY scripts/ ./scripts/
COPY public/ ./public/
ARG VITE_COMMIT_HASH
ENV VITE_COMMIT_HASH=$VITE_COMMIT_HASH

RUN npm run build

FROM python:3.11-slim

WORKDIR /app

# Install system dependencies (curl for healthcheck)
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY app/ ./app/
COPY certs/ ./certs/
COPY fetcher.py server.py ./
COPY public/ ./public/

# Copy Vite build output
COPY --from=frontend-build /build/public/dist/ ./public/dist/
COPY --from=frontend-build /build/public/index.html ./public/index.html

# Expose port
EXPOSE 5000

# Use gunicorn for production
CMD ["gunicorn", "--bind", "0.0.0.0:5000", "--timeout", "120", "--workers", "2", "--threads", "4", "server:app"]
