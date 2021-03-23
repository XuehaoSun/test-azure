import argparse
import glob
import os
import re
from typing import Any, Dict, List
from statistics import mean

class Iteration:
    """Interface for iteration entry"""
    def __init__(self, data: Dict[str, List[Any]]) -> None:
        """Initialize Iteration class."""
        fields = [
            "iteration",
            "value",
        ]

        for field in fields:
            if data.get(field) is None:
                raise Exception(f"Missing required field. Please add all required fields: {fields}")

        self.iteration: int = data.get("iteration")
        self.value: float = data.get("value")

def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--framework", type=str, required=True)
    parser.add_argument("--model", type=str, required=True)
    parser.add_argument("--os", type=str, required=True)
    parser.add_argument("--cpu", type=str, required=True)
    parser.add_argument("--datatype", choices=["fp32", "int8"], required=True)
    parser.add_argument("--mode", choices=["latency"], required=True, help="Currently works only for latency.")
    parser.add_argument("--logs-dir", type=str, required=True)
    parser.add_argument("--start_skip", type=int, required=False, default=0)
    parser.add_argument("--end_skip", type=int, required=False, default=0)
    parser.add_argument("--s-to-ms", action="store_true")
    return parser.parse_args()


def read_iterations(log_file):
    iterations: List[Iteration] = []
    with open(log_file, "r") as file:
            regex = r"Iteration\s*(\d+):\s*(\d+.\d+)\s*sec"
            for line in file:
                line = line.strip()
                search = re.search(regex, line)
                if search:
                    iterations.append(Iteration({
                        "iteration": int(search.group(1)),
                        "value": float(search.group(2))
                    }))
    return iterations


def trim_iterations(iterations, start_skip, end_skip):
    iterations.sort(key=lambda x: x.iteration)
    if start_skip + end_skip >= len(iterations):
        raise Exception(f"Not enough iterations after skipping!\
    User want to skip {start_skip + end_skip} iterations with {len(iterations)} iters in total")

    if end_skip == 0:
        return iterations[start_skip:]
    return iterations[start_skip:-end_skip]


def get_average_latency(file, start_skip, end_skip, s_to_ms):
    partial_results = read_iterations(file)
    partial_results = trim_iterations(partial_results, start_skip, end_skip)

    average = mean(list(map(lambda x: x.value, partial_results)))
    if s_to_ms:
        average *= 1000

    return average


def get_stable_latency(framework, model, datatype, mode, operating_system, cpu, logs_dir, start_skip, end_skip, s_to_ms: bool):
    logs_pattern = os.path.join(logs_dir, f"{framework}-{model}-{datatype}-{mode}-{operating_system}-{cpu}*")

    files = glob.glob(logs_pattern)
    files.sort()

    if len(files) <= 0:
        raise Exception("Not found log files.")

    results = []
    for file in files:
        average = get_average_latency(file, start_skip, end_skip, s_to_ms)
        results.append(average)

    total_average = mean(results)

    print(total_average)
    return total_average

if __name__ == "__main__":
    args = parse_args()
    get_stable_latency(
        framework=args.framework,
        model=args.model,
        datatype=args.datatype,
        mode=args.mode,
        operating_system=args.os,
        cpu=args.cpu,
        logs_dir=args.logs_dir,
        start_skip=args.start_skip,
        end_skip=args.end_skip,
        s_to_ms=args.s_to_ms,
    )

