@echo off
REM Script para executar análise de performance do backend

echo.
echo ========================================
echo  Analisador de Performance
echo  HelmetStore Backend
echo ========================================
echo.

REM Verifica se Python está instalado
python --version >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Python nao encontrado!
    echo Por favor, instale Python 3.7+ de https://www.python.org/
    pause
    exit /b 1
)

REM Instala requests se necessário
python -c "import requests" >nul 2>&1
if errorlevel 1 (
    echo [INFO] Instalando biblioteca requests...
    python -m pip install requests
    echo.
)

REM Executa análise
echo [INFO] Executando analise...
echo.
python analyze_performance.py

REM Pausa para ver resultado
echo.
pause
