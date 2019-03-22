import sys
from com.uwaterloo.watcag import CustomDesign as api


def init(num_jobs):
    api.init(num_jobs)

def new_design(design_name, part_name):
    api.newDesign(design_name, part_name)

def close_design():
    api.reset()

def load_template(template_dir="src/main/resources/default-templates/dcps-xcku5p-ffvb676-2-e"):
    api.loadModulesFromTemplate(template_dir)

def add_module(dcp_filepath, bitWidth, inPIPNames, outPIPNames):
    api.addModule(dcp_filepath, bitWidth, inPIPNames, outPIPNames)

def create_component(parent_dcp, site_name):
    return api.createNewComponent(parent_dcp, site_name)

def get_all_valid_placements(dcp_module):
    return api.getAllValidPlacements(dcp_module)

def add_register(name, components):
    api.addNewComplexRegister(name, components)

def add_input_connection(reg_name, bit_range=None):
    if bit_range is None:
        api.addNewInputConnection(reg_name)
    else:
        api.addNewInputConnection(reg_name, bit_range[0], bit_range[1])

def add_output_connection(reg_name, bit_range=None):
    if bit_range is None:
        api.addNewOutputConnection(reg_name)
    else:
        api.addNewOutputConnection(reg_name, bit_range[0], bit_range[1])

def add_connection(src_reg_name, snk_reg_name, src_bit_range=None, snk_bit_range=None):
    if src_bit_range is None and snk_bit_range is None:
        api.addNewRegisterConnection(src_reg_name, snk_reg_name)
    else:
        api.addNewRegisterConnection(src_reg_name, snk_reg_name, src_bit_range[0],
            src_bit_range[1], snk_bit_range[0], snk_bit_range[1])

def place_design():
    api.placeDesign()

def net_synthesis():
    api.netSynthesis()

def route_design():
    api.routeDesign()

def write_checkpoint(name):
    api.writeCheckpoint(name)

