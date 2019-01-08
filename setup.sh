#!/bin/bash

ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
cd ../

echo "Setting up RapidWright in parent directory of $ROOT_DIR"
git clone https://github.com/Xilinx/RapidWright.git

cd RapidWright/
wget https://github.com/Xilinx/RapidWright/releases/download/v2018.2.5-beta/rapidwright_data.zip
unzip rapidwright_data.zip
rm rapidwright_data.zip
wget https://github.com/Xilinx/RapidWright/releases/download/v2018.2.5-beta/rapidwright_jars.zip
unzip rapidwright_jars.zip
rm rapidwright_jars.zip

export RAPIDWRIGHT_PATH=$(pwd)/
export CLASSPATH=$RAPIDWRIGHT_PATH:$(echo $RAPIDWRIGHT_PATH/jars/*.jar | tr ' ' ':')

gradle build -p $RAPIDWRIGHT_PATH
