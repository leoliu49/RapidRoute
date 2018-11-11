#!/bin/bash


declare -a dist=( 1 2 4 8 )

rm -rf placements/
mkdir placements

for sep in "${dist[@]}"
do
    p=$(($sep+120))
    p1=$((2*$sep+120))
    p2=$((3*$sep+120))
    echo "[reg0]" > placements/testb1_4b_place_N"$sep"_0.conf
    echo "name = reg0" >> placements/testb1_4b_place_N"$sep"_0.conf
    echo "comp0 = type4, SLICE_X56Y120" >> placements/testb1_4b_place_N"$sep"_0.conf
    echo "[reg1]" >> placements/testb1_4b_place_N"$sep"_0.conf
    echo "name = reg1" >> placements/testb1_4b_place_N"$sep"_0.conf
    echo "comp0 = type4, SLICE_X56Y"$p >> placements/testb1_4b_place_N"$sep"_0.conf
    echo "[reg2]" >> placements/testb1_4b_place_N"$sep"_0.conf
    echo "name = reg2" >> placements/testb1_4b_place_N"$sep"_0.conf
    echo "comp0 = type4, SLICE_X56Y"$p1 >> placements/testb1_4b_place_N"$sep"_0.conf
    echo "[reg3]" >> placements/testb1_4b_place_N"$sep"_0.conf
    echo "name = reg3" >> placements/testb1_4b_place_N"$sep"_0.conf
    echo "comp0 = type4, SLICE_X56Y"$p2 >> placements/testb1_4b_place_N"$sep"_0.conf

    echo "[reg0]" > placements/testb1_4b_place_N"$sep"_1.conf
    echo "name = reg0" >> placements/testb1_4b_place_N"$sep"_1.conf
    echo "comp0 = type3, SLICE_X57Y120" >> placements/testb1_4b_place_N"$sep"_1.conf
    echo "[reg1]" >> placements/testb1_4b_place_N"$sep"_1.conf
    echo "name = reg1" >> placements/testb1_4b_place_N"$sep"_1.conf
    echo "comp0 = type3, SLICE_X57Y"$p >> placements/testb1_4b_place_N"$sep"_1.conf
    echo "[reg2]" >> placements/testb1_4b_place_N"$sep"_1.conf
    echo "name = reg2" >> placements/testb1_4b_place_N"$sep"_1.conf
    echo "comp0 = type3, SLICE_X57Y"$p1 >> placements/testb1_4b_place_N"$sep"_1.conf
    echo "[reg3]" >> placements/testb1_4b_place_N"$sep"_1.conf
    echo "name = reg3" >> placements/testb1_4b_place_N"$sep"_1.conf
    echo "comp0 = type3, SLICE_X57Y"$p2 >> placements/testb1_4b_place_N"$sep"_1.conf

    pep=$((120-$sep))
    pep1=$((120-2*$sep))
    pep2=$((120-3*$sep))
    echo "[reg0]" > placements/testb1_4b_place_S"$sep"_0.conf
    echo "name = reg0" >> placements/testb1_4b_place_S"$sep"_0.conf
    echo "comp0 = type4, SLICE_X56Y120" >> placements/testb1_4b_place_S"$sep"_0.conf
    echo "[reg1]" >> placements/testb1_4b_place_S"$sep"_0.conf
    echo "name = reg1" >> placements/testb1_4b_place_S"$sep"_0.conf
    echo "comp0 = type4, SLICE_X56Y"$pep >> placements/testb1_4b_place_S"$sep"_0.conf
    echo "[reg2]" >> placements/testb1_4b_place_S"$sep"_0.conf
    echo "name = reg2" >> placements/testb1_4b_place_S"$sep"_0.conf
    echo "comp0 = type4, SLICE_X56Y"$pep1 >> placements/testb1_4b_place_S"$sep"_0.conf
    echo "[reg3]" >> placements/testb1_4b_place_S"$sep"_0.conf
    echo "name = reg3" >> placements/testb1_4b_place_S"$sep"_0.conf
    echo "comp0 = type4, SLICE_X56Y"$pep2 >> placements/testb1_4b_place_S"$sep"_0.conf

    echo "[reg0]" > placements/testb1_4b_place_S"$sep"_1.conf
    echo "name = reg0" >> placements/testb1_4b_place_S"$sep"_1.conf
    echo "comp0 = type3, SLICE_X57Y120" >> placements/testb1_4b_place_S"$sep"_1.conf
    echo "[reg1]" >> placements/testb1_4b_place_S"$sep"_1.conf
    echo "name = reg1" >> placements/testb1_4b_place_S"$sep"_1.conf
    echo "comp0 = type3, SLICE_X57Y"$pep >> placements/testb1_4b_place_S"$sep"_1.conf
    echo "[reg2]" >> placements/testb1_4b_place_S"$sep"_1.conf
    echo "name = reg2" >> placements/testb1_4b_place_S"$sep"_1.conf
    echo "comp0 = type3, SLICE_X57Y"$pep1 >> placements/testb1_4b_place_S"$sep"_1.conf
    echo "[reg3]" >> placements/testb1_4b_place_S"$sep"_1.conf
    echo "name = reg3" >> placements/testb1_4b_place_S"$sep"_1.conf
    echo "comp0 = type3, SLICE_X57Y"$pep2 >> placements/testb1_4b_place_S"$sep"_1.conf

done

for sep in "${dist[@]}"
do
    p=$(($sep+120))
    p1=$((2*$sep+120))
    p2=$((3*$sep+120))
    echo "[reg0]" > placements/testb1_6b_place_N"$sep"_0.conf
    echo "name = reg0" >> placements/testb1_6b_place_N"$sep"_0.conf
    echo "comp0 = type4, SLICE_X56Y120" >> placements/testb1_6b_place_N"$sep"_0.conf
    echo "[reg1]" >> placements/testb1_6b_place_N"$sep"_0.conf
    echo "name = reg1" >> placements/testb1_6b_place_N"$sep"_0.conf
    echo "comp0 = type4, SLICE_X56Y"$p >> placements/testb1_6b_place_N"$sep"_0.conf
    echo "[reg2]" >> placements/testb1_6b_place_N"$sep"_0.conf
    echo "name = reg2" >> placements/testb1_6b_place_N"$sep"_0.conf
    echo "comp0 = type4, SLICE_X56Y"$p1 >> placements/testb1_6b_place_N"$sep"_0.conf
    echo "[reg3]" >> placements/testb1_6b_place_N"$sep"_0.conf
    echo "name = reg3" >> placements/testb1_6b_place_N"$sep"_0.conf
    echo "comp0 = type4, SLICE_X56Y"$p2 >> placements/testb1_6b_place_N"$sep"_0.conf

    echo "[reg0]" > placements/testb1_6b_place_N"$sep"_1.conf
    echo "name = reg0" >> placements/testb1_6b_place_N"$sep"_1.conf
    echo "comp0 = type5, SLICE_X57Y120" >> placements/testb1_6b_place_N"$sep"_1.conf
    echo "[reg1]" >> placements/testb1_6b_place_N"$sep"_1.conf
    echo "name = reg1" >> placements/testb1_6b_place_N"$sep"_1.conf
    echo "comp0 = type5, SLICE_X57Y"$p >> placements/testb1_6b_place_N"$sep"_1.conf
    echo "[reg2]" >> placements/testb1_6b_place_N"$sep"_1.conf
    echo "name = reg2" >> placements/testb1_6b_place_N"$sep"_1.conf
    echo "comp0 = type5, SLICE_X57Y"$p1 >> placements/testb1_6b_place_N"$sep"_1.conf
    echo "[reg3]" >> placements/testb1_6b_place_N"$sep"_1.conf
    echo "name = reg3" >> placements/testb1_6b_place_N"$sep"_1.conf
    echo "comp0 = type5, SLICE_X57Y"$p2 >> placements/testb1_6b_place_N"$sep"_1.conf

    pep=$((120-$sep))
    pep1=$((120-2*$sep))
    pep2=$((120-3*$sep))
    echo "[reg0]" > placements/testb1_6b_place_S"$sep"_0.conf
    echo "name = reg0" >> placements/testb1_6b_place_S"$sep"_0.conf
    echo "comp0 = type4, SLICE_X56Y120" >> placements/testb1_6b_place_S"$sep"_0.conf
    echo "[reg1]" >> placements/testb1_6b_place_S"$sep"_0.conf
    echo "name = reg1" >> placements/testb1_6b_place_S"$sep"_0.conf
    echo "comp0 = type4, SLICE_X56Y"$pep >> placements/testb1_6b_place_S"$sep"_0.conf
    echo "[reg2]" >> placements/testb1_6b_place_S"$sep"_0.conf
    echo "name = reg2" >> placements/testb1_6b_place_S"$sep"_0.conf
    echo "comp0 = type4, SLICE_X56Y"$pep1 >> placements/testb1_6b_place_S"$sep"_0.conf
    echo "[reg3]" >> placements/testb1_6b_place_S"$sep"_0.conf
    echo "name = reg3" >> placements/testb1_6b_place_S"$sep"_0.conf
    echo "comp0 = type4, SLICE_X56Y"$pep2 >> placements/testb1_6b_place_S"$sep"_0.conf

    echo "[reg0]" > placements/testb1_6b_place_S"$sep"_1.conf
    echo "name = reg0" >> placements/testb1_6b_place_S"$sep"_1.conf
    echo "comp0 = type5, SLICE_X57Y120" >> placements/testb1_6b_place_S"$sep"_1.conf
    echo "[reg1]" >> placements/testb1_6b_place_S"$sep"_1.conf
    echo "name = reg1" >> placements/testb1_6b_place_S"$sep"_1.conf
    echo "comp0 = type5, SLICE_X57Y"$pep >> placements/testb1_6b_place_S"$sep"_1.conf
    echo "[reg2]" >> placements/testb1_6b_place_S"$sep"_1.conf
    echo "name = reg2" >> placements/testb1_6b_place_S"$sep"_1.conf
    echo "comp0 = type5, SLICE_X57Y"$pep1 >> placements/testb1_6b_place_S"$sep"_1.conf
    echo "[reg3]" >> placements/testb1_6b_place_S"$sep"_1.conf
    echo "name = reg3" >> placements/testb1_6b_place_S"$sep"_1.conf
    echo "comp0 = type5, SLICE_X57Y"$pep2 >> placements/testb1_6b_place_S"$sep"_1.conf

done

for sep in "${dist[@]}"
do
    p=$(($sep+120))
    p1=$((2*$sep+120))
    p2=$((3*$sep+120))
    echo "[reg0]" > placements/testb1_8b_place_N"$sep"_0.conf
    echo "name = reg0" >> placements/testb1_8b_place_N"$sep"_0.conf
    echo "comp0 = type6, SLICE_X56Y120" >> placements/testb1_8b_place_N"$sep"_0.conf
    echo "[reg1]" >> placements/testb1_8b_place_N"$sep"_0.conf
    echo "name = reg1" >> placements/testb1_8b_place_N"$sep"_0.conf
    echo "comp0 = type6, SLICE_X56Y"$p >> placements/testb1_8b_place_N"$sep"_0.conf
    echo "[reg2]" >> placements/testb1_8b_place_N"$sep"_0.conf
    echo "name = reg2" >> placements/testb1_8b_place_N"$sep"_0.conf
    echo "comp0 = type6, SLICE_X56Y"$p1 >> placements/testb1_8b_place_N"$sep"_0.conf
    echo "[reg3]" >> placements/testb1_8b_place_N"$sep"_0.conf
    echo "name = reg3" >> placements/testb1_8b_place_N"$sep"_0.conf
    echo "comp0 = type6, SLICE_X56Y"$p2 >> placements/testb1_8b_place_N"$sep"_0.conf

    echo "[reg0]" > placements/testb1_8b_place_N"$sep"_1.conf
    echo "name = reg0" >> placements/testb1_8b_place_N"$sep"_1.conf
    echo "comp0 = type7, SLICE_X57Y120" >> placements/testb1_8b_place_N"$sep"_1.conf
    echo "[reg1]" >> placements/testb1_8b_place_N"$sep"_1.conf
    echo "name = reg1" >> placements/testb1_8b_place_N"$sep"_1.conf
    echo "comp0 = type7, SLICE_X57Y"$p >> placements/testb1_8b_place_N"$sep"_1.conf
    echo "[reg2]" >> placements/testb1_8b_place_N"$sep"_1.conf
    echo "name = reg2" >> placements/testb1_8b_place_N"$sep"_1.conf
    echo "comp0 = type7, SLICE_X57Y"$p1 >> placements/testb1_8b_place_N"$sep"_1.conf
    echo "[reg3]" >> placements/testb1_8b_place_N"$sep"_1.conf
    echo "name = reg3" >> placements/testb1_8b_place_N"$sep"_1.conf
    echo "comp0 = type7, SLICE_X57Y"$p2 >> placements/testb1_8b_place_N"$sep"_1.conf

    pep=$((120-$sep))
    pep1=$((120-2*$sep))
    pep2=$((120-3*$sep))
    echo "[reg0]" > placements/testb1_8b_place_S"$sep"_0.conf
    echo "name = reg0" >> placements/testb1_8b_place_S"$sep"_0.conf
    echo "comp0 = type6, SLICE_X56Y120" >> placements/testb1_8b_place_S"$sep"_0.conf
    echo "[reg1]" >> placements/testb1_8b_place_S"$sep"_0.conf
    echo "name = reg1" >> placements/testb1_8b_place_S"$sep"_0.conf
    echo "comp0 = type6, SLICE_X56Y"$pep >> placements/testb1_8b_place_S"$sep"_0.conf
    echo "[reg2]" >> placements/testb1_8b_place_S"$sep"_0.conf
    echo "name = reg2" >> placements/testb1_8b_place_S"$sep"_0.conf
    echo "comp0 = type6, SLICE_X56Y"$pep1 >> placements/testb1_8b_place_S"$sep"_0.conf
    echo "[reg3]" >> placements/testb1_8b_place_S"$sep"_0.conf
    echo "name = reg3" >> placements/testb1_8b_place_S"$sep"_0.conf
    echo "comp0 = type6, SLICE_X56Y"$pep2 >> placements/testb1_8b_place_S"$sep"_0.conf

    echo "[reg0]" > placements/testb1_8b_place_S"$sep"_1.conf
    echo "name = reg0" >> placements/testb1_8b_place_S"$sep"_1.conf
    echo "comp0 = type7, SLICE_X57Y120" >> placements/testb1_8b_place_S"$sep"_1.conf
    echo "[reg1]" >> placements/testb1_8b_place_S"$sep"_1.conf
    echo "name = reg1" >> placements/testb1_8b_place_S"$sep"_1.conf
    echo "comp0 = type7, SLICE_X57Y"$pep >> placements/testb1_8b_place_S"$sep"_1.conf
    echo "[reg2]" >> placements/testb1_8b_place_S"$sep"_1.conf
    echo "name = reg2" >> placements/testb1_8b_place_S"$sep"_1.conf
    echo "comp0 = type7, SLICE_X57Y"$pep1 >> placements/testb1_8b_place_S"$sep"_1.conf
    echo "[reg3]" >> placements/testb1_8b_place_S"$sep"_1.conf
    echo "name = reg3" >> placements/testb1_8b_place_S"$sep"_1.conf
    echo "comp0 = type7, SLICE_X57Y"$pep2 >> placements/testb1_8b_place_S"$sep"_1.conf

done



