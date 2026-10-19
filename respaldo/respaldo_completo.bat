@echo off
setlocal

rem ---------- CONFIGURACION (cambie solo lo que sea distinto en su PC) ----------
set "MYSQL_BIN=C:\xampp\mysql\bin"
set "BASE=hotel_san_antonio"
rem Usuario, clave, host y puerto estan en este archivo, fuera del proyecto (vea LEEME.txt).
set "CREDENCIALES=%USERPROFILE%\HotelSanAntonio\respaldo.cnf"
rem Carpeta donde se guardan las copias.
set "DESTINO=%USERPROFILE%\HotelSanAntonio\copias-seguridad-db"
set "DIAS_GUARDAR=28"
rem -----------------------------------------------------------------------------

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HH-mm"') do set "FECHA=%%i"

if not exist "%DESTINO%" mkdir "%DESTINO%"

if not exist "%CREDENCIALES%" (
    echo [%FECHA%] ERROR: falta el archivo de credenciales %CREDENCIALES%>> "%DESTINO%\respaldo.log"
    echo ERROR: falta el archivo de credenciales %CREDENCIALES%
    exit /b 1
)

if not exist "%DESTINO%\completa" mkdir "%DESTINO%\completa"
set "ARCHIVO=%DESTINO%\completa\completo_%FECHA%.sql"

echo Haciendo respaldo completo de %BASE% ...

rem --flush-logs y --master-data=2: el incremental continua desde este punto
"%MYSQL_BIN%\mysqldump.exe" --defaults-extra-file="%CREDENCIALES%" --single-transaction --flush-logs --master-data=2 --routines --triggers --default-character-set=utf8mb4 --databases %BASE% --result-file="%ARCHIVO%"

if errorlevel 1 (
    echo [%FECHA%] ERROR: fallo el respaldo completo>> "%DESTINO%\respaldo.log"
    echo ERROR: no se pudo hacer el respaldo. Revise que MySQL este encendido.
    if exist "%ARCHIVO%" del "%ARCHIVO%"
    exit /b 1
)

echo [%FECHA%] OK completo_%FECHA%.sql>> "%DESTINO%\respaldo.log"
echo Listo: %ARCHIVO%

forfiles /p "%DESTINO%\completa" /m completo_*.sql /d -%DIAS_GUARDAR% /c "cmd /c del @path" >nul 2>&1

exit /b 0
