# CloudTorrent Backend

FastAPI service for managing server-side torrent jobs with qBittorrent.

## Status

Backend scaffold. Google Drive OAuth, resumable Drive uploads, authenticated streaming, and production deployment hardening are not implemented yet.

## Development

The backend is designed to run alongside qBittorrent using Docker Compose. Keep the qBittorrent Web UI private and expose the API only through HTTPS with authentication.
