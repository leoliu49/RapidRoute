#!/bin/bash

ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"


function printHelp() {
    echo "$0 [-h] [FILE_NAME] [--interactive]"
    exit 0
}

FILE_NAME="rapidroute/device_toolkit.py"

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
    -i|--interactive)
    INTERACTIVE="1"
    shift
    ;;
    *)
    printHelp
    ;;
esac
done

if [ "$FILE_NAME" == "rapidroute/device_toolkit.py" ]; then
    printHelp
fi

. env.sh $(pwd)/../RapidWright

if [ -z ${INTERACTIVE+x} ]; then
    java org.python.util.jython "$FILE_NAME"
else
    java org.python.util.jython -i "$FILE_NAME"
fi

