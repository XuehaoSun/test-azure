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

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--framework", type=str, required=True)
    parser.add_argument("--model", type=str, required=True)
    parser.add_argument("--datatype", choices=["fp32", "int8"], required=True)
    parser.add_argument("--mode", choices=["latency"], required=True, help="Currently works only for latency.")
    parser.add_argument("--logs-dir", type=str, required=True)
    parser.add_argument("--start_skip", type=int, required=False, default=0)
    parser.add_argument("--end_skip", type=int, required=False, default=0)
    parser.add_argument("--s-to-ms", action="store_true")
    args = parser.parse_args()

    logs_pattern = os.path.join(args.logs_dir, f"{args.framework}_{args.model}_{args.datatype}_{args.mode}*")

    files = glob.glob(logs_pattern)
    files.sort()

    if len(files) <= 0:
        raise Exception("Not found log files.")

    results = []
    for file in files:
        partial_results = []
        with open(file, "r") as log_file:
            regex = r"Iteration\s*(\d+):\s*(\d+.\d+)\s*sec"
            for line in log_file:
                line = line.strip()
                search = re.search(regex, line)
                if search:
                    partial_results.append(Iteration({
                        "iteration": int(search.group(1)),
                        "value": float(search.group(2))
                    }))

        partial_results.sort(key=lambda x: x.iteration)
        if args.start_skip + args.end_skip >= len(partial_results):
            raise Exception(f"Not enough iterations after skipping!\
        User want to skip {args.start_skip + args.end_skip} iterations with {len(partial_results)} iters in total")

        if args.end_skip == 0:
            partial_results = partial_results[args.start_skip:]
        else:
            partial_results = partial_results[args.start_skip:-args.end_skip]

        average = mean(list(map(lambda x: x.value, partial_results)))

        results.append(average)

    total_average = mean(results)

    if args.s_to_ms:
        total_average *= 1000

    print(total_average)

if __name__ == "__main__":
    main()

