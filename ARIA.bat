@echo off
setlocal EnableDelayedExpansion
title ARIA

if /i "%~1"=="setup"   goto SETUP
if /i "%~1"=="build"   goto BUILD
if /i "%~1"=="rebuild" goto BUILD
if /i "%~1"=="help"    goto HELP
if /i "%~1"=="-h"      goto HELP
if /i "%~1"=="--help"  goto HELP
goto LAUNCH

:: =========================================================
:LAUNCH
:: =========================================================
where java >nul 2>nul
if errorlevel 1 (
    echo.
    echo  [!] Java is not installed or not in PATH.
    echo.
    echo      ARIA requires JDK 21 ^(LTS^). Download the installer here:
    echo      https://adoptium.net/temurin/releases/?version=21
    echo.
    echo      Use the .msi installer. During install, tick:
    echo        "Set JAVA_HOME variable"  and  "Add to PATH"
    echo.
    echo      After installing, open a new terminal and run ARIA.bat again.
    echo.
    pause & exit /b 1
)
where javac >nul 2>nul
if errorlevel 1 (
    echo.
    echo  [!] Only the JRE is installed ^(no javac found^).
    echo      ARIA needs the full JDK — not just the runtime.
    echo.
    echo      Download JDK 21 LTS:
    echo      https://adoptium.net/temurin/releases/?version=21
    echo.
    echo      Uninstall the JRE first, then install the JDK.
    echo      After installing, open a new terminal and run ARIA.bat again.
    echo.
    pause & exit /b 1
)

if not exist "target\classes\aria\Main.class" (
    echo  First run — building ARIA...
    echo  ^(This takes 2-5 minutes while Maven downloads dependencies^)
    echo.
    call mvnw.cmd clean package -DskipTests -q
    if errorlevel 1 (
        echo.
        echo  [!] Build failed. Run  ARIA.bat build  to see the full error.
        pause & exit /b 1
    )
    echo  Build complete.
    echo.
)

call mvnw.cmd javafx:run -q
if errorlevel 1 (
    echo.
    echo  ARIA exited with an error.
    pause
)
goto END

:: =========================================================
:BUILD
:: =========================================================
title ARIA - Rebuild
echo.
echo  Rebuilding ARIA...
echo.
call mvnw.cmd clean package -DskipTests
echo.
if errorlevel 1 (
    echo  [!] Build failed. See errors above.
) else (
    echo  Done. Double-click ARIA.bat to launch.
)
pause
goto END

:: =========================================================
:SETUP
:: =========================================================
title ARIA - First Time Setup
echo.
echo  =============================================
echo    ARIA v1.3  —  First Time Setup
echo  =============================================
echo.

echo  [1/4] Checking Java...
where java >nul 2>nul
if errorlevel 1 (
    echo.
    echo   X  Java is not installed or not in PATH.
    echo.
    echo      Download Adoptium Temurin JDK 21:
    echo      https://adoptium.net/temurin/releases/?version=21
    echo.
    echo      Use the .msi installer. During install:
    echo      tick "Set JAVA_HOME" and "Add to PATH".
    echo.
    pause & exit /b 1
)
where javac >nul 2>nul
if errorlevel 1 (
    echo.
    echo   X  Only the JRE is installed — no javac found.
    echo      ARIA needs the full JDK ^(not just the runtime^).
    echo.
    echo      Uninstall the JRE, then install JDK 21 from:
    echo      https://adoptium.net/temurin/releases/?version=21
    echo.
    echo      After installing, open a new terminal and run setup again.
    echo.
    pause & exit /b 1
)
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set JV=%%v
echo   OK  JDK found: %JV%

echo.
echo  [2/4] Checking config file...
if not exist ".env" (
    echo   Creating .env template...
    (
        echo # ARIA Configuration
        echo # Free providers: Groq ^(console.groq.com^), Gemini ^(aistudio.google.com^), HuggingFace ^(huggingface.co/settings/tokens^)
        echo # Local AI:       Ollama ^(ollama.ai^)
        echo.
        echo GROQ_API_KEY=
        echo GEMINI_API_KEY=
        echo HF_API_KEY=
        echo DEEPSEEK_API_KEY=
        echo ANTHROPIC_API_KEY=
        echo OPENAI_API_KEY=
        echo LLM_PROVIDER=groq
        echo CLAUDE_MODEL=claude-haiku-4-5-20251001
        echo OPENAI_MODEL=gpt-4o-mini
        echo GROQ_MODEL=llama-3.3-70b-versatile
        echo GEMINI_MODEL=gemini-1.5-flash
        echo HF_MODEL=meta-llama/Llama-3.2-3B-Instruct
        echo OLLAMA_MODEL=llama3.2
        echo DEEPSEEK_MODEL=deepseek-chat
        echo MAX_TOKENS=1000
        echo PERSIST_HISTORY=false
        echo THEME=dark
        echo AUTHOR_NAME=You
        echo HOME_LAT=8.1500
        echo HOME_LON=124.2333
        echo TTS_ENABLED=false
        echo TTS_SPEED=1.0
        echo TTS_STYLE=neutral
    ) > .env
    echo   OK  Created .env — add your API keys here, or via Settings inside ARIA.
) else (
    echo   OK  .env already exists.
)

echo.
echo  [3/4] Preparing folders...
if not exist "config"         mkdir config
if not exist "exports"        mkdir exports
if not exist "exports\images" mkdir exports\images
if not exist "prompts"        mkdir prompts
echo   OK  Folders ready.

echo.
echo  [3.5/4] Checking optional features...
python -c "import kokoro" >nul 2>nul
if not errorlevel 1 (
    echo   OK  Kokoro TTS detected — voice output available in Settings.
) else (
    echo   --  Kokoro TTS not found ^(optional^).
    echo       To enable voice: pip install kokoro sounddevice
)

echo.
echo  [4/4] Building ARIA...
echo        Downloading Maven dependencies on first run — be patient.
echo.
call mvnw.cmd clean package -DskipTests -q
if errorlevel 1 (
    echo.
    echo   X  Build failed. Run  ARIA.bat build  for full output.
    pause & exit /b 1
)

echo.
echo  =============================================
echo    Setup complete!
echo  =============================================
echo.
echo    Double-click ARIA.bat to launch.
echo.
echo    LLM providers: Groq ^(free^), Gemini ^(free^), HuggingFace ^(free^),
echo                   Ollama ^(local^), DeepSeek, Claude, OpenAI
echo.
pause
goto END

:: =========================================================
:HELP
:: =========================================================
echo.
echo  ARIA Launcher
echo.
echo    ARIA.bat           Launch ARIA  ^(auto-builds on first run^)
echo    ARIA.bat setup     First-time setup wizard
echo    ARIA.bat build     Force a clean rebuild
echo    ARIA.bat help      Show this message
echo.
pause
goto END

:END
endlocal
