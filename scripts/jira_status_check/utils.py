def parse_priority(priority: str):
    priority_map = {
        "P1": "P1-Stopper",
        "P2": "P2-High",
        "P3": "P3-Medium",
        "P4": "P4-Low"
    }
    mapped_priority = priority_map.get(priority, None)
    if mapped_priority is None:
        raise Exception(f"Priority {priority} not recognized. Use one of following: {priority_map.keys()}")
    return mapped_priority


def parse_priorities_string(priorities: str):
    mapped_priorities = []
    priorities = priorities.split(",")
    for priority in priorities:
        mapped_priority = parse_priority(priority)
        mapped_priorities.append(mapped_priority)
    return ",".join(mapped_priorities)
