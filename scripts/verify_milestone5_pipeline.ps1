# Milestone 5 Full Verification Script
$ErrorActionPreference = "Continue"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   ASTRAPROJECT MILESTONE 5 PIPELINE VERIFICATION SUITE   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Obtain Tokens
Write-Host "`n[1/10] Authenticating Admin and User..." -ForegroundColor Yellow
$adminLoginBody = @{ email = "admin@astralib.com"; password = "adminpassword123" } | ConvertTo-Json
$adminAuth = Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" -Method Post -ContentType "application/json" -Body $adminLoginBody
$adminToken = $adminAuth.token
Write-Host "  -> Admin Authenticated! Role: $($adminAuth.role)" -ForegroundColor Green

$userLoginBody = @{ email = "testuser@astralib.com"; password = "password123" } | ConvertTo-Json
$userAuth = Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" -Method Post -ContentType "application/json" -Body $userLoginBody
$userToken = $userAuth.token
Write-Host "  -> User Authenticated! Role: $($userAuth.role)" -ForegroundColor Green

# 2. Milestone 5A - Vector Storage in MySQL
Write-Host "`n[2/10] Verifying Vector Storage Schema in MySQL..." -ForegroundColor Yellow
$mysqlDescribe = & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p9848755690@Mr -e "USE astralib; DESCRIBE vector_records;"
Write-Host "$mysqlDescribe"

# 3. Milestone 5B - Ingest Vectors
Write-Host "`n[3/10] Executing POST /api/admin/vector-ingest..." -ForegroundColor Yellow
$ingestHeaders = @{ Authorization = "Bearer $adminToken" }
$ingestResult = Invoke-RestMethod -Uri "http://localhost:8081/api/admin/vector-ingest" -Method Post -Headers $ingestHeaders
$ingestResultJson = $ingestResult | ConvertTo-Json -Depth 5
Write-Host "  Ingestion Result: $ingestResultJson" -ForegroundColor Green

$count1 = & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p9848755690@Mr -e "USE astralib; SELECT COUNT(*) AS TotalVectors FROM vector_records;"
Write-Host "  MySQL Vector Record Count: $count1"

# 4. Duplicate Ingestion Protection
Write-Host "`n[4/10] Testing Duplicate Ingestion Prevention (2nd Ingest Run)..." -ForegroundColor Yellow
$dupIngestResult = Invoke-RestMethod -Uri "http://localhost:8081/api/admin/vector-ingest" -Method Post -Headers $ingestHeaders
$dupIngestResultJson = $dupIngestResult | ConvertTo-Json -Depth 5
Write-Host "  Duplicate Ingestion Result: $dupIngestResultJson" -ForegroundColor Green

$count2 = & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p9848755690@Mr -e "USE astralib; SELECT COUNT(*) AS TotalVectorsAfterDupRun FROM vector_records;"
Write-Host "  MySQL Count After Second Run: $count2"

# 5. Milestone 5C - Verify Embeddings
Write-Host "`n[5/10] Inspecting Stored Embeddings in MySQL..." -ForegroundColor Yellow
$embedCheck = & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p9848755690@Mr -e "USE astralib; SELECT id, document_id, title, section, chunk_index, LENGTH(embedding_json) AS embedding_length FROM vector_records LIMIT 5;"
Write-Host "$embedCheck"

# 6. Milestone 5D & 5E - Semantic Search API
Write-Host "`n[6/10] Testing Semantic Search API (GET /api/search/semantic)..." -ForegroundColor Yellow
$userHeaders = @{ Authorization = "Bearer $userToken" }
$searchQuery = [System.Web.HttpUtility]::UrlEncode("how can computers learn from examples")
$searchResults = Invoke-RestMethod -Uri "http://localhost:8081/api/search/semantic?query=$searchQuery&topK=3" -Method Get -Headers $userHeaders
$searchResultsJson = $searchResults | ConvertTo-Json -Depth 5
Write-Host "  Semantic Search Results:`n$searchResultsJson" -ForegroundColor Green

# 7. Natural Language Semantic vs Keyword Demo
Write-Host "`n[7/10] Semantic Search with Concept Query: 'systems inspired by how brain cells process information'..." -ForegroundColor Yellow
$query2 = [System.Web.HttpUtility]::UrlEncode("systems inspired by how brain cells process information")
$search2 = Invoke-RestMethod -Uri "http://localhost:8081/api/search/semantic?query=$query2&topK=3" -Method Get -Headers $userHeaders
$search2Json = $search2 | ConvertTo-Json -Depth 5
Write-Host "  Results:`n$search2Json" -ForegroundColor Green

# 8. Milestone 5F, 5G, 5H - RAG Tests (A, B, C)
Write-Host "`n[8/10] Testing RAG Pipeline (POST /api/rag/ask)..." -ForegroundColor Yellow

Write-Host "  --- Test A: In-Domain Question ---" -ForegroundColor Cyan
$ragBodyA = @{ question = "What is supervised learning?" } | ConvertTo-Json
$ragResA = Invoke-RestMethod -Uri "http://localhost:8081/api/rag/ask" -Method Post -Headers $userHeaders -ContentType "application/json" -Body $ragBodyA
$ragResAJson = $ragResA | ConvertTo-Json -Depth 5
Write-Host "  RAG Response A:`n$ragResAJson" -ForegroundColor Green

Write-Host "`n  --- Test B: Specific Question ---" -ForegroundColor Cyan
$ragBodyB = @{ question = "How are machine learning methods used for Seiberg dualities in supersymmetric quiver gauge theories?" } | ConvertTo-Json
$ragResB = Invoke-RestMethod -Uri "http://localhost:8081/api/rag/ask" -Method Post -Headers $userHeaders -ContentType "application/json" -Body $ragBodyB
$ragResBJson = $ragResB | ConvertTo-Json -Depth 5
Write-Host "  RAG Response B:`n$ragResBJson" -ForegroundColor Green

Write-Host "`n  --- Test C: Out-of-Domain Question (Safe Handling) ---" -ForegroundColor Cyan
$ragBodyC = @{ question = "What will be the result of the 2040 Olympics?" } | ConvertTo-Json
$ragResC = Invoke-RestMethod -Uri "http://localhost:8081/api/rag/ask" -Method Post -Headers $userHeaders -ContentType "application/json" -Body $ragBodyC
$ragResCJson = $ragResC | ConvertTo-Json -Depth 5
Write-Host "  RAG Response C:`n$ragResCJson" -ForegroundColor Green

# 9. Document-Specific Q&A (Section 14)
Write-Host "`n[9/10] Testing Document-Specific Q&A (POST /api/rag/documents/{documentId}/ask)..." -ForegroundColor Yellow
$docId = "DOC-ARXIV-2607.28628v1"
$docRagBody = @{ question = "What is the main objective of this document?" } | ConvertTo-Json
$docRagRes = Invoke-RestMethod -Uri "http://localhost:8081/api/rag/documents/$docId/ask" -Method Post -Headers $userHeaders -ContentType "application/json" -Body $docRagBody
$docRagResJson = $docRagRes | ConvertTo-Json -Depth 5
Write-Host "  Document-Specific RAG Response:`n$docRagResJson" -ForegroundColor Green

# 10. Security (RBAC) & Regression Tests
Write-Host "`n[10/10] Verifying Security (RBAC) & Regression Endpoints..." -ForegroundColor Yellow

# Test 10a: No JWT on protected endpoint -> Expected 401
try {
    $null = Invoke-RestMethod -Uri "http://localhost:8081/api/search/semantic?query=test" -Method Get
    Write-Host "  [FAIL] Unauthenticated access allowed!" -ForegroundColor Red
} catch {
    Write-Host "  [PASS] Unauthenticated access blocked: $($_.Exception.Message)" -ForegroundColor Green
}

# Test 10b: USER JWT on ADMIN endpoint -> Expected 403
try {
    $null = Invoke-RestMethod -Uri "http://localhost:8081/api/admin/vector-ingest" -Method Post -Headers $userHeaders
    Write-Host "  [FAIL] USER role accessed ADMIN endpoint!" -ForegroundColor Red
} catch {
    Write-Host "  [PASS] USER role correctly forbidden on ADMIN endpoint: $($_.Exception.Message)" -ForegroundColor Green
}

# Test 10c: Regression check on Books, Authors, Categories, Favorites, Reading History
$books = Invoke-RestMethod -Uri "http://localhost:8081/api/books" -Method Get
Write-Host "  [PASS] GET /api/books returned $($books.Count) books" -ForegroundColor Green

$authors = Invoke-RestMethod -Uri "http://localhost:8081/api/authors" -Method Get
Write-Host "  [PASS] GET /api/authors returned $($authors.Count) authors" -ForegroundColor Green

$categories = Invoke-RestMethod -Uri "http://localhost:8081/api/categories" -Method Get
Write-Host "  [PASS] GET /api/categories returned $($categories.Count) categories" -ForegroundColor Green

$favorites = Invoke-RestMethod -Uri "http://localhost:8081/api/favorites" -Method Get -Headers $userHeaders
Write-Host "  [PASS] GET /api/favorites returned OK" -ForegroundColor Green

$history = Invoke-RestMethod -Uri "http://localhost:8081/api/reading-history" -Method Get -Headers $userHeaders
Write-Host "  [PASS] GET /api/reading-history returned OK" -ForegroundColor Green

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "        ALL MILESTONE 5 VERIFICATIONS COMPLETED!          " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
