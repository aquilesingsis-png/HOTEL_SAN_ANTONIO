@echo off
setlocal

rem ---------- CONFIGURACION (cambie solo lo que sea distinto en su PC) ----------
set "MYSQL_BIN=C:\xampp\mysql\bin"
set "CARPETA_DATOS=C:\xampp\mysql\data"
set "PREFIJO=mysql-bin"
rem Usuario, clave, host y puerto estan en este archivo, fuera del proyecto (vea LEEME.txt).
set "CREDENCIALES=%USERPROFILE%\HotelSanAntonio\respaldo.cnf"
rem Carpeta donde se guardan las copias (la misma que usa el respaldo completo).
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

if not exist "%DESTINO%\incremental" mkdir "%DESTINO%\incremental"

echo Haciendo respaldo incremental ...

rem Cierra el registro binario actual para que quede completo
"%MYSQL_BIN%\mysqladmin.exe" --defaults-extra-file="%CREDENCIALES%" flush-logs
if errorlevel 1 (
    echo [%FECHA%] ERROR: no se pudo cerrar el registro binario>> "%DESTINO%\respaldo.log"
    echo ERROR: revise que MySQL este encendido y que el registro binario este activado.
    exit /b 1
)

robocopy "%CARPETA_DATOS%" "%DESTINO%\incremental" %PREFIJO%.0* /R:1 /W:1 /NFL /NDL /NJH /NJS /NP >nul
if errorlevel 8 (
    echo [%FECHA%] ERROR: no se pudieron copiar los registros binarios>> "%DESTINO%\respaldo.log"
    echo ERROR: no se pudo copiar. Revise la carpeta %CARPETA_DATOS%
    exit /b 1
)

echo [%FECHA%] OK incremental>> "%DESTINO%\respaldo.log"
echo Listo: copiado en %DESTINO%\incremental

forfiles /p "%DESTINO%\incremental" /m %PREFIJO%.0* /d -%DIAS_GUARDAR% /c "cmd /c del @path" >nul 2>&1

exit /b 0
