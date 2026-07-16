from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from .config import settings
from .database import check_db, init_db
from .routes import auth, events, friends, invites, sync

init_db()

app = FastAPI(title="Tempo Calendar API", version="0.2.0")
app.add_middleware(CORSMiddleware, allow_origins=settings.origins, allow_credentials=True,
                   allow_methods=["*"], allow_headers=["*"])


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
app.include_router(events.router)
app.include_router(invites.router)
app.include_router(sync.router)
