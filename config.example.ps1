# ============================================================
# AstraLib Local Configuration
# Copy this file to config.ps1 and enter local values.
# Never commit config.ps1.
# ============================================================

# ---------------- DATABASE ----------------
$env:DB_URL = "jdbc:mysql://localhost:3306/astralib"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "CHANGE_ME"

# ---------------- SECURITY ----------------
$env:JWT_SECRET = "CHANGE_ME_TO_A_LONG_RANDOM_SECRET"
$env:JWT_EXPIRATION = "86400000"

# ---------------- AI / GEMINI ----------------
$env:LLM_PROVIDER = "gemini"

# Embeddings must come from the same model that produced the stored vectors.
# The curated collection is indexed with the local service, so keep this
# "local" unless every vector has been regenerated with another provider.
$env:LLM_EMBEDDING_PROVIDER = "local"
$env:LLM_LOCAL_EMBEDDING_URL = "http://127.0.0.1:8001/embed"
$env:LLM_API_KEY = "CHANGE_ME"

$env:LLM_CHAT_URL = "https://generativelanguage.googleapis.com/v1beta/models"
$env:LLM_EMBEDDING_URL = "https://generativelanguage.googleapis.com/v1beta/models"

$env:LLM_MODEL = "gemini-3.6-flash"
$env:LLM_EMBEDDING_MODEL = "all-MiniLM-L6-v2"
$env:LLM_EMBEDDING_DIMENSION = "384"

# ---------------- SERVER ----------------
$env:SERVER_PORT = "8082"

# ---------------- DATA ----------------
# Normally these do NOT need to be changed.
$env:ASTRALIB_DATA_ROOT = "../data"
$env:ASTRALIB_UPLOAD_ROOT = "uploads"
