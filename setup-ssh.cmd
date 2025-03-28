@echo off
::# Remove Inheritance:
Icacls "%UserProfile%\.ssh\id_rsa" /c /t /Inheritance:d
::# Set Ownership to Owner:
:: # Key's within %UserProfile%:
Icacls "%UserProfile%\.ssh\id_rsa" /c /t /Grant "%UserName%":F
:: # Key's outside of %UserProfile%:
TakeOwn /F "%UserProfile%\.ssh\id_rsa"
Icacls "%UserProfile%\.ssh\id_rsa" /c /t /Grant:r "%UserName%":F
::# Remove All Users, except for Owner:
Icacls "%UserProfile%\.ssh\id_rsa" /c /t /Remove:g "Authenticated Users" BUILTIN\Administrators BUILTIN Everyone System Users
::# Verify:
Icacls "%UserProfile%\.ssh\id_rsa"

sc start ssh-agent
echo.
echo to prevent ssh agent asking for passphrase type the command 'ssh-add %HOMEDRIVE%%HOMEPATH%\.ssh\id_rsa'
echo.
echo.