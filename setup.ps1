param(
    [switch]$ForceDatabaseImport
)

$ErrorActionPreference = "Stop"

$PROJECT_ROOT = $PSScriptRoot
$BACKEND_DIR = Join-Path $PROJECT_ROOT "astralib-backend"
$FRONTEND_DIR = Join-Path $PROJECT_ROOT "astralib-frontend"
$CONFIG_FILE = Join-Path $PROJECT_ROOT "config.ps1"
$CONFIG_EXAMPLE = Join-Path $PROJECT_ROOT "config.example.ps1"
$DB_DUMP = Join-Path $PROJECT_ROOT "database\astralib.sql"

function Fail-Setup {
    param([string]$Message)
    throw $Message
}

function Require-Command {
    param(
        [string]$Name,
        [string]$FriendlyName
    )

    $cmd = Get-Command $Name -ErrorAction SilentlyContinue

    if (!$cmd) {
        Fail-Setup "$FriendlyName was not found. Install it and run setup.ps1 again."
    }

    return $cmd.Source
}

try {

    Write-Host ""
    Write-Host "============================================"
    Write-Host "          AstraLib System Setup"
    Write-Host "============================================"
    Write-Host ""

    # -------------------------------------------------
    # 1. Verify project files
    # -------------------------------------------------

    $requiredFiles = @(
        (Join-Path $BACKEND_DIR "pom.xml"),
        (Join-Path $FRONTEND_DIR "package.json"),
        $CONFIG_EXAMPLE,
        $DB_DUMP,
        (Join-Path $PROJECT_ROOT "data\fulltext\books\sherlock_holmes\sherlock_holmes.epub"),
        (Join-Path $BACKEND_DIR "uploads\1788089313593_AstraLib_SRS.pdf")
    )

    foreach ($file in $requiredFiles) {
        if (!(Test-Path $file)) {
            Fail-Setup "Required AstraLib file is missing: $file"
        }
    }

    Write-Host "[OK] Project files"

    # -------------------------------------------------
    # 2. Verify Java
    # -------------------------------------------------

    Require-Command "java" "Java 17 or newer" | Out-Null

    $javaOutput = cmd.exe /c "java -version 2>&1"

    if ($LASTEXITCODE -ne 0) {
        Fail-Setup "Java is installed but could not be executed."
    }

    $javaLine = [string]($javaOutput | Select-Object -First 1)

    if ($javaLine -match '"1\.(\d+)') {
        $javaMajor = [int]$Matches[1]
    }
    elseif ($javaLine -match '"(\d+)') {
        $javaMajor = [int]$Matches[1]
    }
    else {
        Fail-Setup "Unable to detect Java version from: $javaLine"
    }

    if ($javaMajor -lt 17) {
        Fail-Setup "Java $javaMajor detected. AstraLib requires Java 17 or newer."
    }

    Write-Host "[OK] Java $javaMajor"

    # -------------------------------------------------
    # 3. Verify Maven
    # -------------------------------------------------

    Require-Command "mvn" "Apache Maven" | Out-Null
    Write-Host "[OK] Maven"

    # -------------------------------------------------
    # 4. Verify Node and npm
    # -------------------------------------------------

    Require-Command "node" "Node.js" | Out-Null
    Require-Command "npm" "npm" | Out-Null

    $nodeText = (& node --version).Trim()

    if ($nodeText -match 'v(\d+)') {
        $nodeMajor = [int]$Matches[1]

        if ($nodeMajor -lt 18) {
            Fail-Setup "Node.js $nodeText detected. Install Node.js 18 or newer."
        }
    }

    Write-Host "[OK] Node.js $nodeText"
    Write-Host "[OK] npm"

    # -------------------------------------------------
    # 5. Locate MySQL client
    # -------------------------------------------------

    $mysqlCommand = Get-Command "mysql.exe" -ErrorAction SilentlyContinue

    if ($mysqlCommand) {
        $MYSQL_EXE = $mysqlCommand.Source
    }
    else {

        $mysqlCandidates = @()

        $mysqlCandidates += Get-ChildItem `
            "$env:ProgramFiles\MySQL\MySQL Server *\bin\mysql.exe" `
            -File `
            -ErrorAction SilentlyContinue

        if (${env:ProgramFiles(x86)}) {
            $mysqlCandidates += Get-ChildItem `
                "${env:ProgramFiles(x86)}\MySQL\MySQL Server *\bin\mysql.exe" `
                -File `
                -ErrorAction SilentlyContinue
        }

        $MYSQL_EXE = $mysqlCandidates |
            Sort-Object FullName -Descending |
            Select-Object -First 1 -ExpandProperty FullName
    }

    if (!$MYSQL_EXE) {
        Fail-Setup "MySQL client was not found. Install MySQL Server and run setup again."
    }

    Write-Host "[OK] MySQL client"
    Write-Host "     $MYSQL_EXE"

    # -------------------------------------------------
    # 6. Create config.ps1 if needed
    # -------------------------------------------------

    if (!(Test-Path $CONFIG_FILE)) {

        Copy-Item $CONFIG_EXAMPLE $CONFIG_FILE

        Write-Host ""
        Write-Host "config.ps1 was created."
        Write-Host ""
        Write-Host "Open:"
        Write-Host "  $CONFIG_FILE"
        Write-Host ""
        Write-Host "Enter:"
        Write-Host "  MySQL password"
        Write-Host "  Gemini API key"
        Write-Host "  JWT secret"
        Write-Host ""
        Write-Host "Then run setup.ps1 again."
        Write-Host ""

        exit 0
    }

    # Load private configuration
    . $CONFIG_FILE

    # -------------------------------------------------
    # 7. Validate configuration
    # -------------------------------------------------

    if (!$env:DB_URL) {
        Fail-Setup "DB_URL is missing from config.ps1"
    }

    if (!$env:DB_USERNAME) {
        Fail-Setup "DB_USERNAME is missing from config.ps1"
    }

    if (!$env:JWT_SECRET -or $env:JWT_SECRET -match "CHANGE_ME") {
        Fail-Setup "Set a real JWT_SECRET in config.ps1"
    }

    if (!$env:LLM_API_KEY -or $env:LLM_API_KEY -match "CHANGE_ME") {
        Fail-Setup "Set your Gemini API key in config.ps1"
    }

    Write-Host "[OK] Private configuration"

    # -------------------------------------------------
    # 8. Parse MySQL URL
    # -------------------------------------------------

    if ($env:DB_URL -notmatch '^jdbc:mysql://([^:/?]+)(?::(\d+))?/([^?]+)') {
        Fail-Setup "Unsupported DB_URL format: $env:DB_URL"
    }

    $dbHost = $Matches[1]

    if ($Matches[2]) {
        $dbPort = $Matches[2]
    }
    else {
        $dbPort = "3306"
    }

    $dbName = $Matches[3]

    # -------------------------------------------------
    # 9. Secure temporary MySQL configuration
    # -------------------------------------------------

    $tempMyCnf = Join-Path `
        $env:TEMP `
        ("astralib_mysql_" + [Guid]::NewGuid().ToString() + ".cnf")

    try {

        $safePassword = $env:DB_PASSWORD
        $safePassword = $safePassword.Replace('\', '\\')
        $safePassword = $safePassword.Replace('"', '\"')

        $mysqlConfig = @"
[client]
host=$dbHost
port=$dbPort
user=$env:DB_USERNAME
password="$safePassword"
"@

        [System.IO.File]::WriteAllText(
            $tempMyCnf,
            $mysqlConfig,
            [System.Text.UTF8Encoding]::new($false)
        )

        # -------------------------------------------------
        # 10. Test MySQL connection
        # -------------------------------------------------

        $connectionResult = & $MYSQL_EXE `
            "--defaults-extra-file=$tempMyCnf" `
            -N `
            -B `
            -e "SELECT 1;" 2>&1

        if ($LASTEXITCODE -ne 0) {
            Fail-Setup "Could not connect to MySQL. Check DB_USERNAME and DB_PASSWORD in config.ps1."
        }

        Write-Host "[OK] MySQL connection"

        # -------------------------------------------------
        # 11. Check database
        # -------------------------------------------------

        $dbExists = & $MYSQL_EXE `
            "--defaults-extra-file=$tempMyCnf" `
            -N `
            -B `
            -e "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='$dbName';"

        if ($LASTEXITCODE -ne 0) {
            Fail-Setup "Unable to check AstraLib database."
        }

        if (!$dbExists -or $ForceDatabaseImport) {

            Write-Host ""
            Write-Host "Importing AstraLib database..."
            Write-Host "This may take a few minutes."
            Write-Host ""

            $mysqlArgument = "--defaults-extra-file=`"$tempMyCnf`""

            $mysqlErrorFile = Join-Path `
                $env:TEMP `
                ("astralib_mysql_error_" + [Guid]::NewGuid().ToString() + ".txt")

            try {

                $process = Start-Process `
                    -FilePath $MYSQL_EXE `
                    -ArgumentList $mysqlArgument `
                    -RedirectStandardInput $DB_DUMP `
                    -RedirectStandardError $mysqlErrorFile `
                    -NoNewWindow `
                    -Wait `
                    -PassThru

                if ($process.ExitCode -ne 0) {

                    $errorText = ""

                    if (Test-Path $mysqlErrorFile) {
                        $errorText = Get-Content $mysqlErrorFile -Raw
                    }

                    Fail-Setup "Database import failed.`n$errorText"
                }
            }
            finally {
                Remove-Item $mysqlErrorFile -Force -ErrorAction SilentlyContinue
            }

            Write-Host "[OK] AstraLib database imported"
        }
        else {
            Write-Host "[OK] AstraLib database already exists - import skipped"
        }
    }
    finally {
        Remove-Item $tempMyCnf -Force -ErrorAction SilentlyContinue
    }

    # -------------------------------------------------
    # 12. Frontend dependencies
    # -------------------------------------------------

    Write-Host ""
    Write-Host "Checking frontend dependencies..."

    Set-Location $FRONTEND_DIR

    $viteReady = Test-Path ".\node_modules\.bin\vite.cmd"
    $esbuildReady = Test-Path ".\node_modules\@esbuild\win32-x64\esbuild.exe"

    if ($viteReady -and $esbuildReady) {

        Write-Host "[OK] Frontend dependencies already installed"

    }
    else {

        Write-Host "Frontend dependencies are missing."
        Write-Host "Installing frontend dependencies..."

        if (Test-Path ".\package-lock.json") {
            npm ci
        }
        else {
            npm install
        }

        if ($LASTEXITCODE -ne 0) {
            Fail-Setup "Frontend dependency installation failed."
        }

        Write-Host "[OK] Frontend dependencies installed"
    }
    # -------------------------------------------------
    # 13. Compile backend
    # -------------------------------------------------

    Write-Host ""
    Write-Host "Compiling AstraLib backend for Java 17..."

    Set-Location $BACKEND_DIR

    mvn clean compile -DskipTests

    if ($LASTEXITCODE -ne 0) {
        Fail-Setup "Backend compilation failed."
    }

    Write-Host "[OK] Backend compiled"

    # -------------------------------------------------
    # 14. Final result
    # -------------------------------------------------

    Set-Location $PROJECT_ROOT

    Write-Host ""
    Write-Host "============================================"
    Write-Host "         ASTRALIB SETUP COMPLETE"
    Write-Host "============================================"
    Write-Host ""
    Write-Host "Start backend:"
    Write-Host "  .\start-backend.ps1"
    Write-Host ""
    Write-Host "Start frontend in another terminal:"
    Write-Host "  .\start-frontend.ps1"
    Write-Host ""
    Write-Host "Open:"
    Write-Host "  http://localhost:5173"
    Write-Host ""
}
catch {

    Write-Host ""
    Write-Host "============================================"
    Write-Host "          SETUP FAILED"
    Write-Host "============================================"
    Write-Host ""
    Write-Host $_.Exception.Message
    Write-Host ""

    exit 1
}
