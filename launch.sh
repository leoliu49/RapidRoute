#!/bin/bash

ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"


function printHelp() {
    echo "$0 [-h] [FILE_NAME] [-j NUM_JOBS]"
    exit 0
}

FILE_NAME="rw_router/device_toolkit.py"
NUM_JOBS=""

while [[ $# -gt 0 ]]
do
key="$1"

case $key in
    -h)
    printHelp
    ;;
    *.py)
    FILE_NAME="$1"
    shift
    ;;
    -j|--jobs)
    NUM_JOBS="$2"
    shift
    shift
    ;;
    *)
    printHelp
    ;;
esac
done

if [ "$NUM_JOBS" == "" ] &&[ "$FILE_NAME" == "rw_router/device_toolkit.py" ]; then
    printHelp
fi

. env.sh "$ROOT_DIR"/../RapidWright
gradle jar

./jython -i "$FILE_NAME" "$NUM_JOBS"
