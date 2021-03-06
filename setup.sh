#!/bin/zsh

ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
cd ../

echo "Setting up RapidWright in parent directory of $ROOT_DIR ."
git clone https://github.com/Xilinx/RapidWright.git
cd RapidWright
git checkout d87dd2c

wget https://github.com/Xilinx/RapidWright/releases/download/v2018.2.5-beta/rapidwright_data.zip
unzip rapidwright_data.zip
rm rapidwright_data.zip
wget https://github.com/Xilinx/RapidWright/releases/download/v2018.2.5-beta/rapidwright_jars.zip
unzip rapidwright_jars.zip
rm rapidwright_jars.zip
cd ../

export RAPIDWRIGHT_PATH=$(pwd)/RapidWright
export CLASSPATH=$RAPIDWRIGHT_PATH:$(echo $RAPIDWRIGHT_PATH/jars/*.jar | tr ' ' ':')
gradle build -p $RAPIDWRIGHT_PATH

echo "Copying RapidWright JAR output to deps/ directory."
cp RapidWright/build/libs/*.jar RapidRoute/deps/

echo "RapidWright source is in your parent directory. Do not remove it."

cd ../RapidRoute
gradle build -p ./

echo "Built RapidRoute"
