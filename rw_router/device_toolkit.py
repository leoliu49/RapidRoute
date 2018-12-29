import sys
from com.uwaterloo.watcag import CustomDesign as api

__design = None
__design_name = None
__part_name = None

def init(design_name, part_name, num_jobs):
    api.init(design_name, part_name, num_jobs)

def load_template(template_dir="src/main/resources/default-templates/dcps-xcku5p-ffvb676-2-e"):
    api.loadModulesFromTemplate(template_dir)

def add_module(dcp_filepath, bitWidth, inPIPNames, outPIPNames):
    api.addModule(dcp_filepath, bitWidth, inPIPNames, outPIPNames)

def create_component(parent_dcp, site_name):
    return api.createNewComponent(parent_dcp, site_name)

def add_register(name, components):
    api.addNewComplexRegister(name, components)

def add_input_connection(reg_name, bit_range):
    api.addNewInputConnection(reg_name, dest_bit_range[0], dest_bit_range[1])

def add_output_connection(reg_name, bit_range):
    api.addNewOutputConnection(reg_name, src_bit_range[0], src_bit_range[1])

def add_connection(src_reg_name, snk_reg_name, src_bit_range=None, snk_bit_range=None):
    if src_bit_range is None and snk_bit_range is None:
        api.addNewRegisterConnection(src_reg_name, snk_reg_name)
    else:
        api.addNewRegisterConnection(src_reg_name, snk_reg_name, src_bit_range[0],
            src_bit_range[1], snk_bit_range[0], snk_bit_range[1])

def place_design():
    api.placeDesign()

def route_design():
    api.routeDesign()

def write_checkpoint(name):
    api.writeCheckpoint(name)


if __name__ == "__main__":
    __design_name = sys.argv[1]
    __part_name = sys.argv[2]
    num_jobs = int(sys.argv[3])

    init(__design_name, __part_name, num_jobs)


