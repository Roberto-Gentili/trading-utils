@echo off

set CURRENT_DIR=%~dp0
set CURRENT_UNIT=%CURRENT_DIR:~0,2%

%CURRENT_UNIT%
cd "%CURRENT_DIR%"

%CURRENT_UNIT%

call git pull --ff-only
call git add *
call git commit -am "Updated asset report"
call git push