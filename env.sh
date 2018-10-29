#!/bin/bash

RAPIDWRIGHT_PATH=$1
ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

BUILD_DIR=$ROOT_DIR/build/libs
DEPS_DIR=$ROOT_DIR/deps


#export RAPIDWRIGHT_PATH=$RAPIDWRIGHT_PATH

#export CLASSPATH=$CLASSPATH:$RAPIDWRIGHT_PATH/build/libs/rapidwright.jar

#export CLASSPATH=$CLASSPATH:$(echo $RAPIDWRIGHT_PATH/jars/*.jar | tr ' ' ':')

export CLASSPATH=$CLASSPATH:$(echo $BUILD_DIR/*.jar | tr ' ' ':')

export CLASSPATH=$CLASSPATH:$(echo $DEPS_DIR/*.jar | tr ' ' ':')
