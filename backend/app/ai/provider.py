from __future__ import annotations

import json
from datetime import date

import httpx

from ..config import settings
from .schemas import CalendarDraft


class ZhipuProviderError(RuntimeError):
    def __init__(self, message: str, status_code: int | None = None):
        super().__init__(message)
        self.status_code = status_code


class ZhipuCalendarProvider:
    """HTTP adapter kept behind Tempo API so Android never receives the API key."""

    def __init__(self, client: httpx.AsyncClient | None = None):
        self.client = client

    async def parse_audio(self, audio: bytes, filename: str, content_type: str | None,
                          timezone: str, today: date) -> tuple[str, CalendarDraft]:
        if not settings.zhipu_api_key:
            raise RuntimeError("AI provider is not configured")
        close_client = self.client is None
        client = self.client or httpx.AsyncClient(timeout=httpx.Timeout(45.0, connect=10.0))
        try:
            headers = {"Authorization": f"Bearer {settings.zhipu_api_key}"}
            files = {"file": (filename, audio, content_type or "application/octet-stream")}
            asr = await client.post(
                f"{settings.zhipu_base_url}/audio/transcriptions",
                headers=headers,
                files=files,
                data={"model": settings.zhipu_asr_model, "stream": "false"},
            )
            asr.raise_for_status()
            transcript = str(asr.json().get("text", "")).strip()
            if not transcript:
                raise ValueError("speech transcription is empty")

            schema_hint = {
                "title": "string, required",
                "description": "string or null",
                "date": "YYYY-MM-DD or null",
                "start_time": "HH:MM or null",
                "end_time": "HH:MM or null",
                "category": "工作/学习/游戏/聚会/会议/自定义",
                "status": "HARD/FREE/FLEXIBLE",
                "flexible_tail_minutes": "0/15/30/45/60",
                "confidence": "number from 0 to 1",
                "missing_fields": "array of missing field names",
            }
            system = (
                "你是 Tempo 日历解析器。只输出 JSON，不要输出 Markdown。"
                f"当前日期是 {today.isoformat()}，用户时区是 {timezone}。"
                "相对日期根据当前日期解析；没有明确的日期或时间不要猜，填 null 并列入 missing_fields。"
                f"JSON 字段结构：{json.dumps(schema_hint, ensure_ascii=False)}"
            )
            parsed = await client.post(
                f"{settings.zhipu_base_url}/chat/completions",
                headers={**headers, "Content-Type": "application/json"},
                json={
                    "model": settings.zhipu_text_model,
                    "temperature": 0.1,
                    "response_format": {"type": "json_object"},
                    "messages": [
                        {"role": "system", "content": system},
                        {"role": "user", "content": transcript},
                    ],
                },
            )
            parsed.raise_for_status()
            content = parsed.json()["choices"][0]["message"]["content"]
            if isinstance(content, list):
                content = "".join(item.get("text", "") for item in content if isinstance(item, dict))
            return transcript, CalendarDraft.model_validate(json.loads(content))
        except httpx.HTTPStatusError as exc:
            if exc.response.status_code == 429:
                raise ZhipuProviderError("智谱语音服务当前余额不足或没有可用资源包", 429) from exc
            raise ZhipuProviderError("智谱服务暂时不可用", exc.response.status_code) from exc
        except (httpx.HTTPError, KeyError, TypeError, ValueError, json.JSONDecodeError) as exc:
            raise RuntimeError("AI calendar parsing failed") from exc
        finally:
            if close_client:
                await client.aclose()
