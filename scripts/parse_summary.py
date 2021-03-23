import argparse
import json
import os

from utils.result import Measurement, Result


def parse_args():
    parser = argparse.ArgumentParser(allow_abbrev = False)
    parser.add_argument("--summary-file", type=str, required=True)
    parser.add_argument("--output-name", type=str, required=True)
    return parser.parse_args()

def parse_summary(summary_file: str, output_name: str):
    results = []
    if os.path.isfile(summary_file):
        with open(summary_file, "r") as f:
            for line in f:
                if line.startswith("OS;Platform;Framework;"):  # Skip header
                    continue
                result = parse_result(line)
                append_result(result, results)

    with open(output_name, "w") as f:
        json.dump([result.serialize() for result in results], f, indent=4)

def parse_result(result_line: str):
    result = Result()
    data = result_line.strip().split(";")
    result.os = data[0]
    result.platform = data[1]
    result.framework = data[2]
    result.model = data[4]

    try:
        batch_size = int(data[7])
    except:
        batch_size = None

    try:
        value = float(data[8])
    except:
        value = None

    result.benchmarks.append(Measurement({
        "batch_size": batch_size,
        "mode": data[6].lower(),
        "precision": data[3].lower(),
        "value": value,
        "log": data[9]
    }))

    return result

def append_result(result, results):
    """Append result to results list."""
    for res in results:
        if all((
            res.platform == result.platform,
            res.os == result.os,
            res.framework == result.framework,
            res.model == result.model,
        )):
            res.benchmarks.extend(result.benchmarks)
            return
    results.append(result)

if __name__ == "__main__":
    args = parse_args()
    parse_summary(
        summary_file=args.summary_file,
        output_name=args.output_name,
    )
