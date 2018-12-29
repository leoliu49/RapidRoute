#!/bin/bash

ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"


function printHelp() {
    echo "$0 [-h] [--py FILE_NAME] [-d DESIGN_NAME] [-p DEVICE_PART] [-j NUM_JOBS]"
    exit 0
}

FILE_NAME="rw_router/device_toolkit.py"
DESIGN_NAME=""
DEVICE_PART=""
NUM_JOBS=""

while [[ $# -gt 0 ]]
do
key="$1"

case $key in
    -h)
    printHelp
    ;;
    --py)
    FILE_NAME="$2"
    shift
    shift
    ;;
    -d|--design)
    DESIGN_NAME="$2"
    shift
    shift
    ;;
    -p|--part)
    DEVICE_PART="$2"
    shift
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

if [ "$DESIGN_NAME" == "" ]; then
    printHelp
fi
if [ "$DEVICE_PART" == "" ]; then
    printHelp
fi
if [ "$NUM_JOBS" == "" ]; then
    printHelp
fi

. env.sh "$ROOT_DIR"/../RapidWright
gradle jar

./jython -i "$FILE_NAME" "$DESIGN_NAME" "$DEVICE_PART" "$NUM_JOBS"
