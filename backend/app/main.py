from __future__ import annotations

import hmac
import os
from typing import Any
from urllib.parse import urlparse

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException, status
from pydantic import BaseModel, Field

API_TOKEN = os.getenv("API_TOKEN", "")
QBITTORRENT_URL = os.getenv("QBITTORRENT_URL", "http://qbittorrent:8080").rstrip("/")
QBITTORRENT_USERNAME = os.getenv("QBITTORRENT_USERNAME", "admin")
QBITTORRENT_PASSWORD = os.getenv("QBITTORRENT_PASSWORD", "")
DOWNLOAD_DIR = os.getenv("DOWNLOAD_DIR", "/downloads")

app = FastAPI(title="CloudTorrent API", version="0.1.0")


class AddTorrentRequest(BaseModel):
    magnet_uri: str = Field(min_length=20, max_length=8192)


async def require_token(authorization: str | None = Header(default=None)) -> None:
    if not API_TOKEN:
        raise HTTPException(status_code=503, detail="API_TOKEN is not configured")
    expected = f"Bearer {API_TOKEN}"
    if authorization is None or not hmac.compare_digest(authorization, expected):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid API token")


async def qbit_request(method: str, path: str, **kwargs: Any) -> httpx.Response:
    if not QBITTORRENT_PASSWORD:
        raise HTTPException(status_code=503, detail="qBittorrent password is not configured")
    timeout = httpx.Timeout(20.0, connect=10.0)
    async with httpx.AsyncClient(base_url=QBITTORRENT_URL, timeout=timeout) as client:
        login = await client.post(
            "/api/v2/auth/login",
            data={"username": QBITTORRENT_USERNAME, "password": QBITTORRENT_PASSWORD},
        )
        if login.status_code != 200 or login.text.strip() != "Ok.":
            raise HTTPException(status_code=502, detail="Could not authenticate with qBittorrent")
        response = await client.request(method, path, cookies=login.cookies, **kwargs)
        if response.status_code >= 400:
            raise HTTPException(status_code=502, detail="qBittorrent request failed")
        return response


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "cloudtorrent-api"}


@app.get("/api/torrents", dependencies=[Depends(require_token)])
async def list_torrents() -> list[dict[str, Any]]:
    response = await qbit_request("GET", "/api/v2/torrents/info")
    return response.json()


@app.post("/api/torrents", status_code=202, dependencies=[Depends(require_token)])
async def add_torrent(request: AddTorrentRequest) -> dict[str, str]:
    parsed = urlparse(request.magnet_uri)
    if parsed.scheme.lower() != "magnet" or "xt=" not in parsed.query:
        raise HTTPException(status_code=400, detail="A valid magnet URI is required")
    response = await qbit_request(
        "POST",
        "/api/v2/torrents/add",
        data={"urls": request.magnet_uri, "savepath": DOWNLOAD_DIR},
    )
    if response.text.strip() not in ("Ok.", ""):
        raise HTTPException(status_code=502, detail="qBittorrent did not accept the torrent")
    return {"status": "accepted"}


@app.post("/api/torrents/{torrent_hash}/pause", dependencies=[Depends(require_token)])
async def pause_torrent(torrent_hash: str) -> dict[str, str]:
    await qbit_request("POST", "/api/v2/torrents/stop", data={"hashes": torrent_hash})
    return {"status": "paused"}


@app.post("/api/torrents/{torrent_hash}/resume", dependencies=[Depends(require_token)])
async def resume_torrent(torrent_hash: str) -> dict[str, str]:
    await qbit_request("POST", "/api/v2/torrents/start", data={"hashes": torrent_hash})
    return {"status": "resumed"}


@app.delete("/api/torrents/{torrent_hash}", dependencies=[Depends(require_token)])
async def delete_torrent(torrent_hash: str, delete_files: bool = False) -> dict[str, str]:
    await qbit_request(
        "POST",
        "/api/v2/torrents/delete",
        data={"hashes": torrent_hash, "deleteFiles": str(delete_files).lower()},
    )
    return {"status": "deleted"}
