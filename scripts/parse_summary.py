import argparse
import json
import os
import csv

from utils.result import Measurement, Result


def parse_args():
    parser = argparse.ArgumentParser(allow_abbrev = False)
    parser.add_argument("--summary-file", type=str, required=True)
    parser.add_argument("--output-name", type=str, required=True)
    return parser.parse_args()

def parse_summary(summary_file: str, output_name: str):
    results = []
    if os.path.isfile(summary_file):
        with open(summary_file, newline="") as summary_file:
            header = summary_file.readline().lower().strip().split(";")
            reader = csv.DictReader(summary_file, fieldnames=header, delimiter=";")
            for row in reader:
                result = parse_result(row)
                append_result(result, results)

    with open(output_name, "w") as f:
        json.dump([result.serialize() for result in results], f, indent=4)

def parse_result(data: dict):
    result = Result()
    result.os = data.get("os")
    result.platform = data.get("platform")
    result.framework = data.get("framework")
    result.model = data.get("model")

    try:
        batch_size = int(data.get("bs"))
    except:
        batch_size = None

    try:
        value = float(data.get("value"))
    except:
        value = None

    result.benchmarks.append(Measurement({
        "batch_size": batch_size,
        "mode": data.get("type", "n/a").lower(),
        "precision": data.get("precision", "n/a").lower(),
        "value": value,
        "log": data.get("url")
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
