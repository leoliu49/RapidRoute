#!/bin/bash


declare -a bw=( "2" "4" "6" "8" )

declare -a dist=( 1 4 16 64 )

rm -f routing_tests/testa1_results.txt
cd ../../

for bit in "${bw[@]}"
do
    for sep in "${dist[@]}"
    do
        cp routing_tests/testa1/components/testa1_"$bw"b_comp_0.conf src/main/resources/register_components.conf
        cp routing_tests/testa1/placements/testa1_"$bw"b_place_N"$sep"_0.conf src/main/resources/placements.conf
        cp routing_tests/testa1/routes/testa1_"$bw"b_route.conf src/main/resources/routes.conf

        date >> routing_tests/testa1_results.txt
        time java CustomDesign --out regpair_"$bw"b_N"$sep"_0
        date >> routing_tests/testa1_results.txt


        cp routing_tests/testa1/components/testa1_"$bw"b_comp_1.conf src/main/resources/register_components.conf
        cp routing_tests/testa1/placements/testa1_"$bw"b_place_N"$sep"_1.conf src/main/resources/placements.conf
        cp routing_tests/testa1/routes/testa1_"$bw"b_route.conf src/main/resources/routes.conf

        date >> routing_tests/testa1_results.txt
        time java CustomDesign --out regpair_"$bw"b_N"$sep"_1
        date >> routing_tests/testa1_results.txt

        cp routing_tests/testa1/components/testa1_"$bw"b_comp_0.conf src/main/resources/register_components.conf
        cp routing_tests/testa1/placements/testa1_"$bw"b_place_S"$sep"_0.conf src/main/resources/placements.conf
        cp routing_tests/testa1/routes/testa1_"$bw"b_route.conf src/main/resources/routes.conf

        date >> routing_tests/testa1_results.txt
        time java CustomDesign --out regpair_"$bw"b_S"$sep"_0
        date >> routing_tests/testa1_results.txt


        cp routing_tests/testa1/components/testa1_"$bw"b_comp_1.conf src/main/resources/register_components.conf
        cp routing_tests/testa1/placements/testa1_"$bw"b_place_S"$sep"_1.conf src/main/resources/placements.conf
        cp routing_tests/testa1/routes/testa1_"$bw"b_route.conf src/main/resources/routes.conf

        date >> routing_tests/testa1_results.txt
        time java CustomDesign --out regpair_"$bw"b_S"$sep"_1
        date >> routing_tests/testa1_results.txt


    done
done
