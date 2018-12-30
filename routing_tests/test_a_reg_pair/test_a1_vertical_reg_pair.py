#!jython
from rw_router.device_toolkit import *

if __name__ == "__main__":

    base_x = 56
    base_y = 80

    base_separation = 20
    sep_inc = 5
    iterations = 5


    for i in range(iterations):

        separation = base_separation + sep_inc * i;

        init(4)
        new_design("test_a1", "xcku5p-ffvb676-2-e")

        load_template("src/main/resources/default-templates/dcps-xcku5p-ffvb676-2-e")

        add_register("src_reg", [create_component("west_8b", "SLICE_X" + str(base_x) + "Y" + str(base_y))])
        add_register("snk_reg", [create_component("west_8b", "SLICE_X" + str(base_x) + "Y" + str(base_y + separation))])
        place_design()

        add_input_connection("src_reg")
        add_connection("src_reg", "snk_reg")
        add_output_connection("snk_reg")
        route_design()

        write_checkpoint("test_a1_N_" + str(separation) + "_west_8b.dcp")

        close_design()

