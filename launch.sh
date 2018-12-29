#!/bin/bash

ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

if [[ $* == *-h* ]]
then
    echo "$0 [-h] [DESIGN_NAME] [DEVICE_PART] [NUM_JOBS]"
    exit 0
fi


DESIGN_NAME="$1"
DEVICE_PART="$2"
NUM_JOBS="$3"

. env.sh "$ROOT_DIR"/../RapidWright
gradle jar

./jython -i rw_router/device_toolkit.py "$DESIGN_NAME" "$DEVICE_PART" "$NUM_JOBS"
