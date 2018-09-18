@echo off

SET ROOT_DIR="%cd%"
cd ../

ECHO Setting up RapidWright in parent directory of %ROOT_DIR%
git clone https://github.com/Xilinx/RapidWright.git

cd RapidWright/
wget https://github.com/Xilinx/RapidWright/releases/download/v2018.2.5-beta/rapidwright_data.zip
unzip rapidwright_data.zip
rm rapidwright_data.zip
wget https://github.com/Xilinx/RapidWright/releases/download/v2018.2.5-beta/rapidwright_jars.zip
unzip rapidwright_jars.zip
rm rapidwright_jars.zip

SET RAPIDWRIGHT_PATH=%cd%
SET CLASSPATH=%cd%

cd jars/

@ECHO OFF &SETLOCAL
for %%f in (.\*) do call set "CLASSPATH=%%CLASSPATH%%;%%cd%%\%%f"

gradle build -p %RAPIDWRIGHT_PATH%
