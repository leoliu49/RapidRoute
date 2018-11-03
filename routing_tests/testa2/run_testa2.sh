#!/bin/bash

base_tile=37

declare -a slices_left_0=( 55 52 40 4 )
declare -a tiles_left_0=( 36 34 26 3 )

declare -a slices_left_1=( 54 51 41 2 )
declare -a tiles_left_1=( 35 33 26 1 )

declare -a slices_right_0=( 58 61 74 98 )
declare -a tiles_right_0=( 38 40 48 64 )

declare -a slices_right_1=( 59 63 73 107 )
declare -a tiles_right_1=( 38 41 47 71 )

declare -a bw=( 2 4 6 8 )

declare -a index=( 0 1 2 3 )

cd ../../
rm -f routing_tests/testa2/results.txt

cp src/main/resources/reg_bank/reg_1d_2b_0.dcp src/main/resources/components/type0.dcp
cp src/main/resources/reg_bank/reg_1d_2b_1.dcp src/main/resources/components/type1.dcp
cp src/main/resources/reg_bank/reg_1d_4b_0.dcp src/main/resources/components/type2.dcp
cp src/main/resources/reg_bank/reg_1d_4b_1.dcp src/main/resources/components/type3.dcp
cp src/main/resources/reg_bank/reg_1d_6b_0.dcp src/main/resources/components/type4.dcp
cp src/main/resources/reg_bank/reg_1d_6b_1.dcp src/main/resources/components/type5.dcp
cp src/main/resources/reg_bank/reg_1d_8b_0.dcp src/main/resources/components/type6.dcp
cp src/main/resources/reg_bank/reg_1d_8b_1.dcp src/main/resources/components/type7.dcp

cp routing_tests/testa2/components.conf src/main/resources/register_components.conf

for bit in "${bw[@]}"
do
    for i in "${index[@]}"
    do
        sep=$(( $base_tile - ${tiles_left_0[i]} ))
        echo -e "\n\n$bit W $sep 0"
        cp routing_tests/testa2/placements/testa2_"$bit"b_place_"${slices_left_0[i]}"_0.conf src/main/resources/placements.conf
        cp routing_tests/testa2/routes/testa2_"$bit"b_route.conf src/main/resources/routes.conf
        date >> routing_tests/testa2/results.txt
        time java CustomDesign --out regpair_"$bit"b_W"$sep"_0 >> routing_tests/testa2/results.txt
        date >> routing_tests/testa2/results.txt

        sep=$(( $base_tile - ${tiles_left_1[i]} ))
        echo -e "\n\n$bit W $sep 1"
        cp routing_tests/testa2/placements/testa2_"$bit"b_place_"${slices_left_1[i]}"_1.conf src/main/resources/placements.conf
        cp routing_tests/testa2/routes/testa2_"$bit"b_route.conf src/main/resources/routes.conf
        date >> routing_tests/testa2/results.txt
        time java CustomDesign --out regpair_"$bit"b_W"$sep"_1 >> routing_tests/testa2/results.txt
        date >> routing_tests/testa2/results.txt

        sep=$(( ${tiles_right_0[i]} - $base_tile ))
        echo -e "\n\n$bit E $sep 0"
        cp routing_tests/testa2/placements/testa2_"$bit"b_place_"${slices_right_0[i]}"_0.conf src/main/resources/placements.conf
        cp routing_tests/testa2/routes/testa2_"$bit"b_route.conf src/main/resources/routes.conf
        date >> routing_tests/testa2/results.txt
        time java CustomDesign --out regpair_"$bit"b_E"$sep"_0 >> routing_tests/testa2/results.txt
        date >> routing_tests/testa2/results.txt

        sep=$(( ${tiles_right_1[i]} - $base_tile ))
        echo -e "\n\n$bit E $sep 1"
        cp routing_tests/testa2/placements/testa2_"$bit"b_place_"${slices_right_1[i]}"_1.conf src/main/resources/placements.conf
        cp routing_tests/testa2/routes/testa2_"$bit"b_route.conf src/main/resources/routes.conf
        date >> routing_tests/testa2/results.txt
        time java CustomDesign --out regpair_"$bit"b_E"$sep"_1 >> routing_tests/testa2/results.txt
        date >> routing_tests/testa2/results.txt

    done
done
