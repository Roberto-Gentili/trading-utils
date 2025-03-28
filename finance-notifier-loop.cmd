@echo off

set CURRENT_DIR=%~dp0
set CURRENT_UNIT=%CURRENT_DIR:~0,2%
set GIT_HOME=%CURRENT_UNIT%\Shared\Programmi\Git\current
set GIT_SSH=C:\Windows\System32\OpenSSH\ssh.exe
set JAVA_HOME=%CURRENT_UNIT%\Shared\Programmi\Java\jdk\18
set MVN_HOME=%CURRENT_UNIT%\Shared\Programmi\Apache\Maven\3.6.3
set MVN_SETTINGS_PATH=%CURRENT_UNIT%\Shared\Dati\Programmi\Apache\Maven\burningwave_settings.xml
set path=%path%;%MVN_HOME%\bin;%JAVA_HOME%\bin;%GIT_HOME%\bin;

::To retrieve current directory name
::for %%I in (.) do set CurrDirName=%%~nxI
::cd %CurrDirName%

%CURRENT_UNIT%
cd %CURRENT_DIR%

call "%CURRENT_DIR%\setup-ssh.cmd"

set EXTERNAL_MANAGED_LOOP=true

:loop

call git pull --ff-only

call mvn --settings %MVN_SETTINGS_PATH% clean dependency:list install

IF ["ASSET_DETECTOR_LOGGING_ENABLED"] == ["true"] (
	echo.
	call java.exe -DcryptoComApiKey=%CRYPTO_COM_API_KEY% -DcryptoComApiSecret=%CRYPTO_COM_API_SECRET% -DbinanceApiKey=%BINANCE_API_KEY% -DbinanceApiSecret=%BINANCE_API_SECRET% -DemailAccount=%BURNINGWAVE_ORG_ACCOUNT_NAME% -DemailPassword=%BURNINGWAVE_ORG_ACCOUNT_PASSWORD% -DmultiThreadingMode=normal -jar ./target/runner-1.0.0.jar org.rg.service.Runner
) else (
	start "Crypto RSI Change Notifier" javaw.exe -DcryptoComApiKey=%CRYPTO_COM_API_KEY% -DcryptoComApiSecret=%CRYPTO_COM_API_SECRET% -DbinanceApiKey=%BINANCE_API_KEY% -DbinanceApiSecret=%BINANCE_API_SECRET% -DemailAccount=%BURNINGWAVE_ORG_ACCOUNT_NAME% -DemailPassword=%BURNINGWAVE_ORG_ACCOUNT_PASSWORD% -DmultiThreadingMode=normal -jar "%CURRENT_DIR%/target/runner-1.0.0.jar" org.rg.service.Runner
)
::call git pull --ff-only
::call git commit -am "Updated asset report"
::call git push

timeout /t 5 /NOBREAK > NUL
goto loop