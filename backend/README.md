# CloudTorrent Backend

FastAPI control service for server-side torrent jobs managed by qBittorrent.

## Current implementation

- Bearer-token protected torrent list, add, pause, resume, and delete endpoints.
- qBittorrent Web API integration over the private Docker network.
- Shared download volume for later processing and cloud-storage upload.

## Not implemented yet

Google Drive OAuth, resumable Drive uploads, user accounts, authenticated HTTP Range streaming, and production deployment automation are still pending. This scaffold is not a complete CloudTorrent service.

## Local setup

1. Install Docker Engine and the Docker Compose plugin.
2. Copy `.env.example` to `.env`.
3. Set a long random `API_TOKEN` and a qBittorrent password in `.env`.
4. Start the services with `docker compose up -d --build`.
5. Read the qBittorrent container logs to obtain its initial Web UI password, then sign in to its Web UI from a trusted/private network and set the permanent password to match `QBITTORRENT_PASSWORD` in `.env`.
6. Restart the API with `docker compose restart api`.

The qBittorrent Web UI is intentionally not published as a host port. The API is bound to host loopback (127.0.0.1:8000); use a properly configured HTTPS reverse proxy before remote access.

## API

All `/api/*` routes require:

```http
Authorization: Bearer YOUR_API_TOKEN
```

- `GET /health` — health check (no token)
- `GET /api/torrents` — list torrents
- `POST /api/torrents` — add a magnet URI; JSON body: `{"magnet_uri":"magnet:?xt=..."}`
- `POST /api/torrents/{hash}/pause`
- `POST /api/torrents/{hash}/resume`
- `DELETE /api/torrents/{hash}?delete_files=false`

## Security

- Never commit `.env`, API tokens, or qBittorrent credentials.
- Do not expose port 8000 directly to the public internet.
- Use HTTPS, firewall rules, strong unique secrets, and authenticated user sessions before production use.
- Only download and distribute content you are authorized to access.
