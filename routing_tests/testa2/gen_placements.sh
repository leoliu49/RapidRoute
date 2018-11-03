#!/bin/bash

declare -a slices_left_0=( 55 52 40 4 )
declare -a tiles_left_0=( 36 34 26 3 )

declare -a slices_left_1=( 54 51 41 2 )
declare -a tiles_left_1=( 35 33 26 1 )

declare -a slices_right_0=( 58 61 74 98 )
declare -a tiles_right_0=( 38 40 48 64 )

declare -a slices_right_1=( 59 63 73 107 )
declare -a tiles_right_1=( 38 41 47 71 )

declare -a bw=( 2 4 6 8 )

rm -rf placements/
mkdir placements

for b in "${bw[@]}"
do
    type0=$(( $b - 2 ))
    type1=$(( $b - 1 ))

    for site in "${slices_left_0[@]}"
    do
        echo "[reg0]" > placements/testa2_"$b"b_place_"$site"_0.conf
        echo "name = reg0" >> placements/testa2_"$b"b_place_"$site"_0.conf
        echo "comp0 = type${type0}, SLICE_X56Y120" >> placements/testa2_"$b"b_place_"$site"_0.conf
        echo "[reg1]" >> placements/testa2_"$b"b_place_"$site"_0.conf
        echo "name = reg1" >> placements/testa2_"$b"b_place_"$site"_0.conf
        echo "comp0 = type${type0}, SLICE_X${site}Y120" >> placements/testa2_"$b"b_place_"$site"_0.conf

    done

    for site in "${slices_left_1[@]}"
    do
        echo "[reg0]" > placements/testa2_"$b"b_place_"$site"_1.conf
        echo "name = reg0" >> placements/testa2_"$b"b_place_"$site"_1.conf
        echo "comp0 = type${type1}, SLICE_X57Y120" >> placements/testa2_"$b"b_place_"$site"_1.conf
        echo "[reg1]" >> placements/testa2_"$b"b_place_"$site"_1.conf
        echo "name = reg1" >> placements/testa2_"$b"b_place_"$site"_1.conf
        echo "comp0 = type${type1}, SLICE_X${site}Y120" >> placements/testa2_"$b"b_place_"$site"_1.conf

    done

    for site in "${slices_right_0[@]}"
    do
        echo "[reg0]" > placements/testa2_"$b"b_place_"$site"_0.conf
        echo "name = reg0" >> placements/testa2_"$b"b_place_"$site"_0.conf
        echo "comp0 = type${type0}, SLICE_X56Y120" >> placements/testa2_"$b"b_place_"$site"_0.conf
        echo "[reg1]" >> placements/testa2_"$b"b_place_"$site"_0.conf
        echo "name = reg1" >> placements/testa2_"$b"b_place_"$site"_0.conf
        echo "comp0 = type${type0}, SLICE_X${site}Y120" >> placements/testa2_"$b"b_place_"$site"_0.conf

    done

    for site in "${slices_right_1[@]}"
    do
        echo "[reg0]" > placements/testa2_"$b"b_place_"$site"_1.conf
        echo "name = reg0" >> placements/testa2_"$b"b_place_"$site"_1.conf
        echo "comp0 = type${type1}, SLICE_X57Y120" >> placements/testa2_"$b"b_place_"$site"_1.conf
        echo "[reg1]" >> placements/testa2_"$b"b_place_"$site"_1.conf
        echo "name = reg1" >> placements/testa2_"$b"b_place_"$site"_1.conf
        echo "comp0 = type${type1}, SLICE_X${site}Y120" >> placements/testa2_"$b"b_place_"$site"_1.conf

    done
done


