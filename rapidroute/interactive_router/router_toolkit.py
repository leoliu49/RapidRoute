from com.uwaterloo.watcag import InteractiveRouter as _interactive_router_api

from .analysis_toolkit import *

def source():
    _interactive_router_api.printSrc()

def get_source():
    return _interactive_router_api.getSrcName()

def sink():
    _interactive_router_api.printSnk()

def get_sink():
    return _interactive_router_api.getSnkName()

def net():
    _interactive_router_api.printCurrentNet()

def latest_node():
    _interactive_router_api.printLatestNode()

def get_latest_node():
    return _interactive_router_api.getLatestNodeName()

def latest_path():
    _interactive_router_api.printLatestInterconnectPath()

def get_latest_path():
    return _interactive_router_api.getLatestInterconnectPath()

def route_template():
    _interactive_router_api.printCurrentRouteTemplate()

def current_route():
    _interactive_router_api.printCurrentRoute()

def get_all_nodes():
    return _interactive_router_api.getAllNodesInRoute()

def fan_out(node=None):
    if node is not None:
        _interactive_router_api.printNodeFanOut(node)
    else:
        _interactive_router_api.printNodeFanOut("")

def get_fan_out(node=None):
    if node is not None:
        return _interactive_router_api.getNodeFanOut(node)
    else:
        return _interactive_router_api.getNodeFanOut("")

def add_bounce(node):
    _interactive_router_api.addBounceNode(node)

def add_wire(node):
    _interactive_router_api.addWireNode(node)

def roll_back_one():
    _interactive_router_api.rollBackOneNode()

def roll_back_path():
    _interactive_router_api.rollBackInterconnectPath()

def roll_back_to_node(node):
    _interactive_router_api.rollBackToNode(node)

def auto_route_to_node(node):
    _interactive_router_api.autoRouteToNode(node)

