from rapidroute.device_toolkit import *

if __name__ == "__main__":
    init(10)

    x = "71"
    all_ys = [["20", "260"], ["20", "100", "180", "260"],
        ["20", "60", "100", "140", "180", "220"],
        ["20", "50", "80", "110", "140", "170", "200", "230"],
        ["20", "44", "68", "92", "116", "140", "164", "188", "212", "236"]]


    for ys in all_ys:
        new_design("register_pair_test", "xcku115-flva1517-3-e")
        load_template("src/main/resources/default-templates/dcps-xcku115-flva1517-3-e")

        for i in range(len(ys)):
            add_register("reg" + str(i), [create_component("west_8b", "SLICE_X" + x + "Y" + ys[i])])

        place_design()

        for i in range(0, len(ys) - 1):
            add_connection("reg" + str(i), "reg" + str(i + 1))
        add_connection("reg" + str(len(ys) - 1), "reg0")

        net_synthesis()
        route_design()

        write_checkpoint("rings_test_" + str(len(ys)) + ".dcp");
