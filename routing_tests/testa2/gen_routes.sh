#!/bin/bash


declare -a bw=( "2" "4" "6" "8" )

rm -rf routes/
mkdir routes

for bit in "${bw[@]}"
do
    limit=$(($bit-1))
    echo "reg0[$limit..0] <= in[$limit..0]" >> routes/testa2_"$bit"b_route.conf
    echo "out[$limit..0] <= reg1[$limit..0]">> routes/testa2_"$bit"b_route.conf

    echo "reg1[$limit..0] <= reg0[$limit..0]" >> routes/testa2_"$bit"b_route.conf
done


