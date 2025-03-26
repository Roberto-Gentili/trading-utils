@echo off

set CURRENT_DIR=%~dp0
set CURRENT_UNIT=%CURRENT_DIR:~0,2%

cd "%CURRENT_DIR%src\main\resources"

%CURRENT_UNIT%

curl -F assets.html=@assets.html https://rg-shared:ShaSpa2023@neocities.org/api/upload