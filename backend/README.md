# CloudTorrent Backend

FastAPI control service for server-side torrent jobs managed by qBittorrent.

## Current implementation

- Bearer-token protected torrent list, add, pause, resume, and delete endpoints.
- qBittorrent Web API integration over the private Docker network.
- Caddy reverse proxy with automatic HTTPS; the API is not published directly on a public port.
- Shared download volume for later processing and cloud-storage upload.

## Not implemented yet

Google Drive OAuth, resumable Drive uploads, user accounts, authenticated HTTP Range streaming, and production deployment automation are still pending. This scaffold is not a complete CloudTorrent service.

## Local setup

1. Install Docker Engine and the Docker Compose plugin.
2. Copy `.env.example` to `.env`.
3. Set a long random `API_TOKEN` and a qBittorrent password in `.env`.
4. Assign a stable public IPv4 address to the VM and allow inbound TCP 80/443 and TCP+UDP 6881 in both the cloud network rules and the VM firewall. Do not open port 8080 or 8000 publicly.
5. Set `SERVER_DOMAIN` in `.env` to `<public-ip-with-dashes>.sslip.io` (for example, `198-51-100-25.sslip.io`). This DNS service maps the hostname to the embedded public IP; Caddy obtains and renews the HTTPS certificate automatically. Keep the public IP stable.
6. Start the services with `docker compose up -d --build`.
7. Read the qBittorrent container logs to obtain its initial Web UI password. For the first setup, use an SSH tunnel or another private access method to reach its Web UI; do not publish port 8080. Set the permanent password to match `QBITTORRENT_PASSWORD` in `.env`.
8. Restart the API with `docker compose restart api` and check `https://$SERVER_DOMAIN/health`.

Caddy is the only public web entry point. The FastAPI and qBittorrent Web UI ports stay on the private Docker network. Caddy needs inbound TCP ports 80 and 443 for ACME certificate validation and HTTPS; UDP 443 enables HTTP/3.

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
