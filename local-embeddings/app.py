from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer

MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2"

model = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global model

    print(f"Loading embedding model: {MODEL_NAME}")
    model = SentenceTransformer(MODEL_NAME, device="cpu")
    print("Embedding model loaded successfully!")

    yield

    model = None


app = FastAPI(
    title="AstraLib Local Embedding Service",
    lifespan=lifespan,
)


class EmbedRequest(BaseModel):
    text: str = Field(min_length=1, max_length=20000)


class BatchEmbedRequest(BaseModel):
    texts: list[str] = Field(min_length=1, max_length=32)


@app.get("/health")
def health():
    return {
        "status": "ok",
        "model_loaded": model is not None,
        "model": MODEL_NAME,
    }


# TEMPORARY DEBUG ENDPOINT
# Checks what the Java HTTP client actually sends.
@app.post("/debug-request")
async def debug_request(request: Request):
    body = await request.body()

    return {
        "method": request.method,
        "content_type": request.headers.get("content-type"),
        "content_length": request.headers.get("content-length"),
        "transfer_encoding": request.headers.get("transfer-encoding"),
        "body_bytes_received": len(body),
        "body_starts_with_json": body.lstrip().startswith(b"{"),
    }


@app.post("/embed")
def embed(request: EmbedRequest):
    if model is None:
        raise HTTPException(
            status_code=503,
            detail="Model not loaded",
        )

    text = request.text.strip()

    if not text:
        raise HTTPException(
            status_code=422,
            detail="Text cannot be empty",
        )

    vector = model.encode(
        text,
        normalize_embeddings=True,
        convert_to_numpy=True,
    )

    return {
        "model": MODEL_NAME,
        "dimensions": int(vector.shape[0]),
        "embedding": vector.tolist(),
    }


@app.post("/embed-batch")
def embed_batch(request: BatchEmbedRequest):
    if model is None:
        raise HTTPException(
            status_code=503,
            detail="Model not loaded",
        )

    texts = [text.strip() for text in request.texts]

    if any(not text for text in texts):
        raise HTTPException(
            status_code=422,
            detail="Texts cannot be empty",
        )

    vectors = model.encode(
        texts,
        normalize_embeddings=True,
        convert_to_numpy=True,
        batch_size=8,
    )

    return {
        "model": MODEL_NAME,
        "dimensions": int(vectors.shape[1]),
        "embeddings": vectors.tolist(),
    }