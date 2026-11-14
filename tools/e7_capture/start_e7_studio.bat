@echo off
cd /d "%~dp0"
python e7_server.py
if errorlevel 1 (
  echo Khong tim thay Python. Vui long mo Epic Seven Studio bang nut "Tai Epic Seven..." trong TotemConverter.exe!
  pause
)
