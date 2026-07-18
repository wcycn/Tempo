import logging
import time
import uuid

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from .config import settings
from .database import check_db, init_db
from .routes import ai, auth, events, friends, groups, invites, notifications, sync

logging.basicConfig(level=getattr(logging, settings.log_level.upper(), logging.INFO), format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger("tempo.api")

init_db()

app = FastAPI(title="Tempo Calendar API", version="0.2.0")
app.add_middleware(CORSMiddleware, allow_origins=settings.origins, allow_credentials=True,
                   allow_methods=["*"], allow_headers=["*"])


@app.middleware("http")
async def access_log(request: Request, call_next):
    request_id = uuid.uuid4().hex[:12]
    started = time.perf_counter()
    try:
        response = await call_next(request)
        return response
    finally:
        elapsed_ms = (time.perf_counter() - started) * 1000
        status_code = locals().get("response").status_code if "response" in locals() else 500
        logger.info("request_id=%s method=%s path=%s status=%s duration_ms=%.1f", request_id, request.method, request.url.path, status_code, elapsed_ms)
        if "response" in locals():
            response.headers["X-Request-ID"] = request_id
            response.headers["X-Content-Type-Options"] = "nosniff"
            response.headers["X-Frame-Options"] = "DENY"
            response.headers["Referrer-Policy"] = "no-referrer"
            if request.url.path.startswith("/api/auth"):
                response.headers["Cache-Control"] = "no-store"


@app.exception_handler(Exception)
async def unhandled_error(_: Request, exc: Exception):
    logger.exception("unhandled_exception=%s", exc.__class__.__name__)
    return JSONResponse(status_code=500, content={"error": "internal_server_error", "message": "服务器内部错误，请稍后重试"})


@app.get("/api/health", tags=["system"])
def health():
    database_ok = check_db()
    return {"ok": database_ok, "service": "tempo-calendar", "environment": settings.app_env,
            "database": "ok" if database_ok else "unavailable"}


@app.exception_handler(RequestValidationError)
async def validation_error(_: Request, exc: RequestValidationError):
    return JSONResponse(status_code=422, content={"error": "validation_error", "details": exc.errors()})


app.include_router(auth.router)
app.include_router(friends.router)
app.include_router(groups.router)
app.include_router(events.router)
app.include_router(invites.router)
app.include_router(notifications.router)
app.include_router(sync.router)
app.include_router(ai.router)
