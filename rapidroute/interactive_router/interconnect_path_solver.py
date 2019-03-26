from com.uwaterloo.watcag import InteractiveRouter as _interactive_router_api

class InterconnectPathSolver:

    class Node:
        def __init__(self, name):
            self.name = name
            self.children = set()
            self.paths = dict()

    def __init__(self, src, snk, max_depth=8):
        self.src = src
        self.snk = snk

        self.all_paths = find_interconnect_paths(src, snk, max_depth)

        self.all_nodes = set()
        for path in self.all_paths:
            for node in path:
                self.all_nodes.add(node)

        self.paths_map = dict()
        for path in self.all_paths:
            if len(path) not in self.paths_map:
                self.paths_map[len(path)] = list()
            self.paths_map[len(path)].append(path)

        self.graph = {src : self.Node(src), snk : self.Node(snk)}
        for i in range(len(self.all_paths)):
            self.graph[src].paths[i] = 0
            path = self.all_paths[i]
            for j in range(1, len(path)):
                node = path[j]
                if node not in self.graph:
                    self.graph[node] = self.Node(node)
                self.graph[path[j - 1]].children.add(self.graph[node])
                self.graph[node].paths[i] = j

    def find_variations(self, orbit=1):
        variations = set()
        for i in range(len(self.all_paths)):
            for j in range(i + 1, len(self.all_paths)):
                variations.add((i, j))

        for i in range(len(self.all_paths)):
            path = self.all_paths[i]

            last_seen = dict()
            for p in range(len(self.all_paths)):
                if p != i:
                    last_seen[p] = (0, 0)

            for j in range(1, len(path)):
                node = self.graph[path[j]]
                for p, k in node.paths.items():
                    if p <= i:
                        continue
                    orb = max(k - last_seen[p][1], j - last_seen[p][0]) - 1
                    if orb > orbit:
                        variations.discard((min(i, p), max(i, p)))
                    last_seen[p] = (j, k)

        return variations

def find_interconnect_paths(src, snk, max_depth=8):
    return _interactive_router_api.findInterconnectPaths(src, snk, max_depth)
