#!/bin/bash

RAPIDWRIGHT_PATH=$1
ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

BUILD_DIR=$ROOT_DIR/build/libs
DEPS_DIR=$ROOT_DIR/deps

export RAPIDWRIGHT_PATH=$RAPIDWRIGHT_PATH
export CLASSPATH=$CLASSPATH:$(echo $BUILD_DIR/*.jar | tr ' ' ':')
