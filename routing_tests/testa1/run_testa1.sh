#!/bin/bash


declare -a bw=( "2" "4" "6" "8" )

declare -a dist=( 1 4 16 64 )

rm -f routing_tests/testa1/results.txt
cd ../../

cp src/main/reg_bank/reg_1d_4b_0.dcp src/main/resources/components/type0.dcp
cp src/main/reg_bank/reg_1d_4b_1.dcp src/main/resources/components/type1.dcp
cp src/main/reg_bank/reg_1d_2b_0.dcp src/main/resources/components/type2.dcp
cp src/main/reg_bank/reg_1d_2b_1.dcp src/main/resources/components/type3.dcp

cp routing_tests/testa1/components.conf src/main/resources/register_components.conf

for bit in "${bw[@]}"
do
    for sep in "${dist[@]}"
    do
        cp routing_tests/testa1/placements/testa1_"$bit"b_place_N"$sep"_0.conf src/main/resources/placements.conf
        cp routing_tests/testa1/routes/testa1_"$bit"b_route.conf src/main/resources/routes.conf

        date >> routing_tests/testa1/results.txt
        time java CustomDesign --out regpair_"$bit"b_N"$sep"_0
        date >> routing_tests/testa1/results.txt

        cp routing_tests/testa1/placements/testa1_"$bit"b_place_N"$sep"_1.conf src/main/resources/placements.conf
        cp routing_tests/testa1/routes/testa1_"$bit"b_route.conf src/main/resources/routes.conf

        date >> routing_tests/testa1/results.txt
        time java CustomDesign --out regpair_"$bit"b_N"$sep"_1
        date >> routing_tests/testa1/results.txt

        cp routing_tests/testa1/placements/testa1_"$bit"b_place_S"$sep"_0.conf src/main/resources/placements.conf
        cp routing_tests/testa1/routes/testa1_"$bit"b_route.conf src/main/resources/routes.conf

        date >> routing_tests/testa1/results.txt
        time java CustomDesign --out regpair_"$bit"b_S"$sep"_0
        date >> routing_tests/testa1/results.txt


        cp routing_tests/testa1/placements/testa1_"$bit"b_place_S"$sep"_1.conf src/main/resources/placements.conf
        cp routing_tests/testa1/routes/testa1_"$bit"b_route.conf src/main/resources/routes.conf

        date >> routing_tests/testa1/results.txt
        time java CustomDesign --out regpair_"$bit"b_S"$sep"_1
        date >> routing_tests/testa1/results.txt


    done
done
