from rapidroute.device_toolkit import *

if __name__ == "__main__":
    init(10)

    run_params = dict()

    run_params["2x2"] = (["11", "87"], ["20", "220"])
    run_params["3x3"] = (["14", "58", "106"], ["20", "160", "280"])
    run_params["4x4"] = (["17", "35", "53", "71"], ["20", "100", "180", "260"])
    run_params["5x5"] = (["14", "34", "58", "82", "106"], ["20", "80", "160", "220", "280"])
    run_params["6x6"] = (["11", "25", "39", "53", "70", "87"],
        ["20", "60", "100", "140", "180", "220"])

    for size, placements in run_params.items():
        new_design("register_pair_test", "xcku115-flva1517-3-e")
        load_template("src/main/resources/default-templates/dcps-xcku115-flva1517-3-e")

        xs = placements[0]
        ys = placements[1]
        for x in xs:
            for y in ys:
                add_register("reg_" + x + "_" + y,
                    [create_component("west_8b", "SLICE_X" + x + "Y" + y)])

        place_design()
        for x in xs:
            for i in range(-1, len(ys) - 1):
                add_connection("reg_" + x + "_" + ys[i], "reg_" + x + "_" + ys[i + 1], (0, 3), (0, 3))
        for y in ys:
            for i in range(-1, len(xs) - 1):
                add_connection("reg_" + xs[i] + "_" + y, "reg_" + xs[i + 1] + "_" + y, (4, 7), (4, 7))

        net_synthesis()
        route_design()

