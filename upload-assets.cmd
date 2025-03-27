@echo off

set CURRENT_DIR=%~dp0
set CURRENT_UNIT=%CURRENT_DIR:~0,2%

cd "%CURRENT_DIR%src\main\resources"

%CURRENT_UNIT%

curl -F %~1=@%~1 https://%~2@neocities.org/api/upload