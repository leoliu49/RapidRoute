from com.uwaterloo.watcag import InteractiveRouter as _interactive_router_api

class InterconnectPathSolver:

    def __init__(self, routing_constraint, sink_wire_nodes, max_depth=8):
        self.keys = ["LOGIC_OUTS", "NN1", "NN2", "NN4", "NN5", "NN12", "NODE_IMUX", "BYPASS", "BOUNCE", "INT_NODE_SINGLE_DOUBLE", "INT_NODE_QUAD_LONG",
            "INODE", "INT_INT_SINGLE", "INT_NODE_GLOBAL"]

        self.base_constraint = routing_constraint
        self.constraint_stub = self.base_constraint[:self.base_constraint.index(sink_wire_nodes[0]) + 1]

        self.src = routing_constraint[0]
        self.snk = routing_constraint[-1]

        _interactive_router_api.rollBackToNode(sink_wire_nodes[1])
        self.sink_paths = _interactive_router_api.findInterconnectPaths(sink_wire_nodes[1], self.snk, max_depth)

        self.all_nodes = set()
        for node in self.base_constraint:
            self.all_nodes.add(node)
        for path in self.sink_paths:
            for node in path:
                self.all_nodes.add(node)

        self.all_nodes.remove(sink_wire_nodes[0])

        self.usages = {}
        for key in self.keys:
            self.usages[key] = 0
        for node in self.all_nodes:

            is_valid = False
            for key in self.keys:
                if key in node:
                    self.usages[key] = self.usages[key] + 1
                    is_valid = True
                    break
            if is_valid is False:
                print(node)

    def write_out(self, prefix):
        with open(prefix + "_nodes.txt", "w") as f:
            for node in self.all_nodes:
                f.write(node + "\n")
        with open(prefix + "_routes.txt", "w") as f:
            for path in self.sink_paths:
                constraint = list(self.constraint_stub)

                for node in path[1:]:
                    constraint.append(node)
                for node in constraint:
                    f.write(node + " ")
                f.write("\n")
