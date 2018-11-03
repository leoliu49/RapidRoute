#!/bin/bash

declare -a bw=( 2 4 6 8 )

rm -rf routes/
mkdir routes

for bit in "${bw[@]}"
do
    limit=$(($bit-1))
    echo "[routes]" > routes/testa2_"$bit"b_route.conf
    echo "in = reg0_b0_b"$limit >> routes/testa2_"$bit"b_route.conf
    echo "out = reg1_b0_b"$limit >> routes/testa2_"$bit"b_route.conf

    echo "reg0_b0_b"$limit" = reg1_b0_b"$limit >> routes/testa2_"$bit"b_route.conf
done

