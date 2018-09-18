from rapidroute.device_toolkit import *

if __name__ == "__main__":
    init(10)

    new_design("register_pair_test", "xcku115-flva1517-1-c")
    load_template("src/main/resources/default-templates/dcps-xcku115-flva1517-1-c")

    add_register("reg0", [create_component("west_8b", "SLICE_X71Y20")])
    add_register("reg1", [create_component("West_8b", "SLICE_X71Y40")])

    place_design()

    add_input_connection("reg0")
    add_connection("reg0", "reg1")
    add_output_connection("reg1")

    net_synthesis()
    route_design()

    write_checkpoint("register_pair_test.dcp");
