@echo off
REM Carrega as variáveis do .env
for /f "tokens=*" %%i in (src\main\resources\.env) do set %%i
REM Executa o backend
.\mvnw.cmd spring-boot:run
