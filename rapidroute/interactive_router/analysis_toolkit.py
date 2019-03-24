from com.uwaterloo.watcag import InteractiveRouter as _interactive_router_api

def find_detours(path, max_deviations=1):
    if max_deviations > 1:
        print("TODO")
        return None

    detours = []

    for i in range(len(path) - 1):
        node = path[i]
        next_node = path[i + 1]
        for det in _interactive_router_api.getNodeFanOut(node)[0]:
            if next_node in _interactive_router_api.getNodeFanOut(det)[0]:
                detours.append(list(path))
                detours[-1].insert(i + 1, det)
    return detours
