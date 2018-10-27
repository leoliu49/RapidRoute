#!/bin/bash


declare -a dist=( 1 4 16 64 )

rm -rf placements/
mkdir placements

for sep in "${dist[@]}"
do
    p=$(($sep+120))
    echo "[reg0]" > placements/testa1_2b_place_N"$sep"_0.conf
    echo "name = reg0" >> placements/testa1_2b_place_N"$sep"_0.conf
    echo "comp0 = type2, SLICE_X56Y120" >> placements/testa1_2b_place_N"$sep"_0.conf
    echo "[reg1]" >> placements/testa1_2b_place_N"$sep"_0.conf
    echo "name = reg1" >> placements/testa1_2b_place_N"$sep"_0.conf
    echo "comp0 = type2, SLICE_X56Y"$p >> placements/testa1_2b_place_N"$sep"_0.conf

    echo "[reg0]" > placements/testa1_2b_place_N"$sep"_1.conf
    echo "name = reg0" >> placements/testa1_2b_place_N"$sep"_1.conf
    echo "comp0 = type3, SLICE_X57Y120" >> placements/testa1_2b_place_N"$sep"_1.conf
    echo "[reg1]" >> placements/testa1_2b_place_N"$sep"_1.conf
    echo "name = reg1" >> placements/testa1_2b_place_N"$sep"_1.conf
    echo "comp0 = type3, SLICE_X57Y"$p >> placements/testa1_2b_place_N"$sep"_1.conf

    pep=$((120-$sep))
    echo "[reg0]" > placements/testa1_2b_place_S"$sep"_0.conf
    echo "name = reg0" >> placements/testa1_2b_place_S"$sep"_0.conf
    echo "comp0 = type2, SLICE_X56Y120" >> placements/testa1_2b_place_S"$sep"_0.conf
    echo "[reg1]" >> placements/testa1_2b_place_S"$sep"_0.conf
    echo "name = reg1" >> placements/testa1_2b_place_S"$sep"_0.conf
    echo "comp0 = type2, SLICE_X56Y"$pep >> placements/testa1_2b_place_S"$sep"_0.conf

    echo "[reg0]" > placements/testa1_2b_place_S"$sep"_1.conf
    echo "name = reg0" >> placements/testa1_2b_place_S"$sep"_1.conf
    echo "comp0 = type3, SLICE_X57Y120" >> placements/testa1_2b_place_S"$sep"_1.conf
    echo "[reg1]" >> placements/testa1_2b_place_S"$sep"_1.conf
    echo "name = reg1" >> placements/testa1_2b_place_S"$sep"_1.conf
    echo "comp0 = type3, SLICE_X57Y"$pep >> placements/testa1_2b_place_S"$sep"_1.conf

done

for sep in "${dist[@]}"
do
    p=$(($sep+120))
    echo "[reg0]" > placements/testa1_4b_place_N"$sep"_0.conf
    echo "name = reg0" >> placements/testa1_4b_place_N"$sep"_0.conf
    echo "comp0 = type0, SLICE_X56Y120" >> placements/testa1_4b_place_N"$sep"_0.conf
    echo "[reg1]" >> placements/testa1_4b_place_N"$sep"_0.conf
    echo "name = reg1" >> placements/testa1_4b_place_N"$sep"_0.conf
    echo "comp0 = type0, SLICE_X56Y"$p >> placements/testa1_4b_place_N"$sep"_0.conf

    echo "[reg0]" > placements/testa1_4b_place_N"$sep"_1.conf
    echo "name = reg0" >> placements/testa1_4b_place_N"$sep"_1.conf
    echo "comp0 = type1, SLICE_X57Y120" >> placements/testa1_4b_place_N"$sep"_1.conf
    echo "[reg1]" >> placements/testa1_4b_place_N"$sep"_1.conf
    echo "name = reg1" >> placements/testa1_4b_place_N"$sep"_1.conf
    echo "comp0 = type1, SLICE_X57Y"$p >> placements/testa1_4b_place_N"$sep"_1.conf

    pep=$((120-$sep))
    echo "[reg0]" > placements/testa1_4b_place_S"$sep"_0.conf
    echo "name = reg0" >> placements/testa1_4b_place_S"$sep"_0.conf
    echo "comp0 = type0, SLICE_X56Y120" >> placements/testa1_4b_place_S"$sep"_0.conf
    echo "[reg1]" >> placements/testa1_4b_place_S"$sep"_0.conf
    echo "name = reg1" >> placements/testa1_4b_place_S"$sep"_0.conf
    echo "comp0 = type0, SLICE_X56Y"$pep >> placements/testa1_4b_place_S"$sep"_0.conf

    echo "[reg0]" > placements/testa1_4b_place_S"$sep"_1.conf
    echo "name = reg0" >> placements/testa1_4b_place_S"$sep"_1.conf
    echo "comp0 = type1, SLICE_X57Y120" >> placements/testa1_4b_place_S"$sep"_1.conf
    echo "[reg1]" >> placements/testa1_4b_place_S"$sep"_1.conf
    echo "name = reg1" >> placements/testa1_4b_place_S"$sep"_1.conf
    echo "comp0 = type1, SLICE_X57Y"$pep >> placements/testa1_4b_place_S"$sep"_1.conf

done

for sep in "${dist[@]}"
do
    p=$(($sep+120))
    echo "[reg0]" > placements/testa1_6b_place_N"$sep"_0.conf
    echo "name = reg0" >> placements/testa1_6b_place_N"$sep"_0.conf
    echo "comp0 = type0, SLICE_X56Y120" >> placements/testa1_6b_place_N"$sep"_0.conf
    echo "comp1 = type3, SLICE_X57Y120" >> placements/testa1_6b_place_N"$sep"_0.conf
    echo "[reg1]" >> placements/testa1_6b_place_N"$sep"_0.conf
    echo "name = reg1" >> placements/testa1_6b_place_N"$sep"_0.conf
    echo "comp0 = type0, SLICE_X56Y"$p >> placements/testa1_6b_place_N"$sep"_0.conf
    echo "comp1 = type3, SLICE_X57Y"$p >> placements/testa1_6b_place_N"$sep"_0.conf

    echo "[reg0]" > placements/testa1_6b_place_N"$sep"_1.conf
    echo "name = reg0" >> placements/testa1_6b_place_N"$sep"_1.conf
    echo "comp0 = type1, SLICE_X57Y120" >> placements/testa1_6b_place_N"$sep"_1.conf
    echo "comp1 = type2, SLICE_X56Y120" >> placements/testa1_6b_place_N"$sep"_1.conf
    echo "[reg1]" >> placements/testa1_6b_place_N"$sep"_1.conf
    echo "name = reg1" >> placements/testa1_6b_place_N"$sep"_1.conf
    echo "comp0 = type1, SLICE_X57Y"$p >> placements/testa1_6b_place_N"$sep"_1.conf
    echo "comp1 = type2, SLICE_X56Y"$p >> placements/testa1_6b_place_N"$sep"_1.conf

    pep=$((120-$sep))
    echo "[reg0]" > placements/testa1_6b_place_S"$sep"_0.conf
    echo "name = reg0" >> placements/testa1_6b_place_S"$sep"_0.conf
    echo "comp0 = type0, SLICE_X56Y120" >> placements/testa1_6b_place_S"$sep"_0.conf
    echo "comp1 = type3, SLICE_X57Y120" >> placements/testa1_6b_place_S"$sep"_0.conf
    echo "[reg1]" >> placements/testa1_6b_place_S"$sep"_0.conf
    echo "name = reg1" >> placements/testa1_6b_place_S"$sep"_0.conf
    echo "comp0 = type0, SLICE_X56Y"$pep >> placements/testa1_6b_place_S"$sep"_0.conf
    echo "comp1 = type3, SLICE_X57Y"$pep >> placements/testa1_6b_place_S"$sep"_0.conf

    echo "[reg0]" > placements/testa1_6b_place_S"$sep"_1.conf
    echo "name = reg0" >> placements/testa1_6b_place_S"$sep"_1.conf
    echo "comp0 = type1, SLICE_X57Y120" >> placements/testa1_6b_place_S"$sep"_1.conf
    echo "comp1 = type2, SLICE_X56Y120" >> placements/testa1_6b_place_S"$sep"_1.conf
    echo "[reg1]" >> placements/testa1_6b_place_S"$sep"_1.conf
    echo "name = reg1" >> placements/testa1_6b_place_S"$sep"_1.conf
    echo "comp0 = type1, SLICE_X57Y"$pep >> placements/testa1_6b_place_S"$sep"_1.conf
    echo "comp1 = type2, SLICE_X56Y"$pep >> placements/testa1_6b_place_S"$sep"_1.conf

done


for sep in "${dist[@]}"
do
    p=$(($sep+120))
    echo "[reg0]" > placements/testa1_8b_place_N"$sep"_0.conf
    echo "name = reg0" >> placements/testa1_8b_place_N"$sep"_0.conf
    echo "comp0 = type0, SLICE_X56Y120" >> placements/testa1_8b_place_N"$sep"_0.conf
    echo "comp1 = type1, SLICE_X57Y120" >> placements/testa1_8b_place_N"$sep"_0.conf
    echo "[reg1]" >> placements/testa1_8b_place_N"$sep"_0.conf
    echo "name = reg1" >> placements/testa1_8b_place_N"$sep"_0.conf
    echo "comp0 = type0, SLICE_X56Y"$p >> placements/testa1_8b_place_N"$sep"_0.conf
    echo "comp1 = type1, SLICE_X57Y"$p >> placements/testa1_8b_place_N"$sep"_0.conf

    echo "[reg0]" > placements/testa1_8b_place_N"$sep"_1.conf
    echo "name = reg0" >> placements/testa1_8b_place_N"$sep"_1.conf
    echo "comp0 = type1, SLICE_X57Y120" >> placements/testa1_8b_place_N"$sep"_1.conf
    echo "comp1 = type0, SLICE_X56Y120" >> placements/testa1_8b_place_N"$sep"_1.conf
    echo "[reg1]" >> placements/testa1_8b_place_N"$sep"_1.conf
    echo "name = reg1" >> placements/testa1_8b_place_N"$sep"_1.conf
    echo "comp0 = type1, SLICE_X57Y"$p >> placements/testa1_8b_place_N"$sep"_1.conf
    echo "comp1 = type0, SLICE_X56Y"$p >> placements/testa1_8b_place_N"$sep"_1.conf

    pep=$((120-$sep))
    echo "[reg0]" > placements/testa1_8b_place_S"$sep"_0.conf
    echo "name = reg0" >> placements/testa1_8b_place_S"$sep"_0.conf
    echo "comp0 = type0, SLICE_X56Y120" >> placements/testa1_8b_place_S"$sep"_0.conf
    echo "comp1 = type1, SLICE_X57Y120" >> placements/testa1_8b_place_S"$sep"_0.conf
    echo "[reg1]" >> placements/testa1_8b_place_S"$sep"_0.conf
    echo "name = reg1" >> placements/testa1_8b_place_S"$sep"_0.conf
    echo "comp0 = type0, SLICE_X56Y"$pep >> placements/testa1_8b_place_S"$sep"_0.conf
    echo "comp1 = type1, SLICE_X57Y"$pep >> placements/testa1_8b_place_S"$sep"_0.conf

    echo "[reg0]" > placements/testa1_8b_place_S"$sep"_1.conf
    echo "name = reg0" >> placements/testa1_8b_place_S"$sep"_1.conf
    echo "comp0 = type1, SLICE_X57Y120" >> placements/testa1_8b_place_S"$sep"_1.conf
    echo "comp1 = type0, SLICE_X56Y120" >> placements/testa1_8b_place_S"$sep"_1.conf
    echo "[reg1]" >> placements/testa1_8b_place_S"$sep"_1.conf
    echo "name = reg1" >> placements/testa1_8b_place_S"$sep"_1.conf
    echo "comp0 = type1, SLICE_X57Y"$pep >> placements/testa1_8b_place_S"$sep"_1.conf
    echo "comp1 = type0, SLICE_X56Y"$pep >> placements/testa1_8b_place_S"$sep"_1.conf

done


