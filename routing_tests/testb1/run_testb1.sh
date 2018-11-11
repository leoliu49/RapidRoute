#!/bin/bash


declare -a bw=( "4" "6" "8" )

declare -a dist=( 1 2 4 8 )

cd ../../
rm -f routing_tests/testb1/results.txt

cp src/main/resources/reg_bank/reg_1d_2b_0.dcp src/main/resources/components/type0.dcp
cp src/main/resources/reg_bank/reg_1d_2b_1.dcp src/main/resources/components/type1.dcp
cp src/main/resources/reg_bank/reg_1d_4b_0.dcp src/main/resources/components/type2.dcp
cp src/main/resources/reg_bank/reg_1d_4b_1.dcp src/main/resources/components/type3.dcp
cp src/main/resources/reg_bank/reg_1d_6b_0.dcp src/main/resources/components/type4.dcp
cp src/main/resources/reg_bank/reg_1d_6b_1.dcp src/main/resources/components/type5.dcp
cp src/main/resources/reg_bank/reg_1d_8b_0.dcp src/main/resources/components/type6.dcp
cp src/main/resources/reg_bank/reg_1d_8b_1.dcp src/main/resources/components/type7.dcp

cp routing_tests/testb1/components.conf src/main/resources/register_components.conf

for bit in "${bw[@]}"
do
    for sep in "${dist[@]}"
    do
        cp routing_tests/testb1/placements/testb1_"$bit"b_place_N"$sep"_0.conf src/main/resources/placements.conf
        cp routing_tests/testb1/routes/testb1_"$bit"b_route.conf src/main/resources/routes.conf

        echo -e "\n\n$bit N $sep 0"
        date >> routing_tests/testb1/results.txt
        time java CustomDesign --out ring_"$bit"b_N"$sep"_0 >> routing_tests/testb1/results.txt
        date >> routing_tests/testb1/results.txt

        cp routing_tests/testb1/placements/testb1_"$bit"b_place_N"$sep"_1.conf src/main/resources/placements.conf
        cp routing_tests/testb1/routes/testb1_"$bit"b_route.conf src/main/resources/routes.conf

        echo -e "\n\n$bit N $sep 1"
        date >> routing_tests/testb1/results.txt
        time java CustomDesign --out ring_"$bit"b_N"$sep"_1 >> routing_tests/testb1/results.txt
        date >> routing_tests/testb1/results.txt

        cp routing_tests/testb1/placements/testb1_"$bit"b_place_S"$sep"_0.conf src/main/resources/placements.conf
        cp routing_tests/testb1/routes/testb1_"$bit"b_route.conf src/main/resources/routes.conf

        echo -e "\n\n$bit S $sep 0"
        date >> routing_tests/testb1/results.txt
        time java CustomDesign --out ring_"$bit"b_S"$sep"_0 >> routing_tests/testb1/results.txt
        date >> routing_tests/testb1/results.txt


        cp routing_tests/testb1/placements/testb1_"$bit"b_place_S"$sep"_1.conf src/main/resources/placements.conf
        cp routing_tests/testb1/routes/testb1_"$bit"b_route.conf src/main/resources/routes.conf

        echo -e "\n\n$bit S $sep 1"
        date >> routing_tests/testb1/results.txt
        time java CustomDesign --out ring_"$bit"b_S"$sep"_1 >> routing_tests/testb1/results.txt
        date >> routing_tests/testb1/results.txt


    done
done
