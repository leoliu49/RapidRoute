#!/bin/bash


declare -a bw=( "4" "6" "8" )

rm -rf routes/
mkdir routes

for bit in "${bw[@]}"
do
    limit=$(($bit-1))

    echo "reg0[$limit..0] <= reg3[$limit..0]" >> routes/testb1_"$bit"b_route.conf
    echo "reg1[$limit..0] <= reg0[$limit..0]" >> routes/testb1_"$bit"b_route.conf
    echo "reg2[$limit..0] <= reg1[$limit..0]" >> routes/testb1_"$bit"b_route.conf
    echo "reg3[$limit..0] <= reg2[$limit..0]" >> routes/testb1_"$bit"b_route.conf
done

