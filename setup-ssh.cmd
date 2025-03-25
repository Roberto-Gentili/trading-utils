@echo off
::# Set Key File Variable:
Set Key="%UserProfile%\.ssh\id_rsa"
::# Remove Inheritance:
Icacls "%Key%" /c /t /Inheritance:d
::# Set Ownership to Owner:
:: # Key's within %UserProfile%:
Icacls "%Key%" /c /t /Grant "%UserName%":F
:: # Key's outside of %UserProfile%:
TakeOwn /F %Key%
Icacls "%Key%" /c /t /Grant:r "%UserName%":F
::# Remove All Users, except for Owner:
Icacls "%Key%" /c /t /Remove:g "Authenticated Users" BUILTIN\Administrators BUILTIN Everyone System Users
::# Verify:
Icacls "%Key%"
::# Remove Variable:
set "Key="

sc start ssh-agent