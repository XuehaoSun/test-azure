import argparse
import json
import operator
import os
import platform

drops = []

def parse_args():
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument("--new_result", type=str, required=True)
    parser.add_argument("--reference_data", type=str, required=True)
    parser.add_argument("--framework", type=str, required=True)
    parser.add_argument("--model", type=str, required=True)
    parser.add_argument("--os", type=str, default=platform.system())
    parser.add_argument(
        "--platform",
        type=str,
        default=os.environ.get("CPU_NAME", "unknown").split("-")[0],
    )
    parser.add_argument("--threshold", type=float, default=0.05)
    parser.add_argument(
        "--precision",
        type=str,
        help="[Optional] Check only specified precision.",
    )
    parser.add_argument(
        "--mode",
        type=str,
        help="[Optional] Check only specified mode.",
    )

    return parser.parse_args()


def main():
    reference_result = get_result(args.reference_data)
    if reference_result is None:
        return

    new_result = get_result(args.new_result)
    if new_result is None:
        raise Exception("Could not found latest new data.")

    compare_result(new_result, reference_result)
    check_threshold(new_result, args.precision, args.mode)
    if drops:
        print(";".join(drops))


def compare_result(result, reference):
    """Find reference data for benchmark results and get difference."""
    for benchmark in result.get("benchmarks", []):
        ref_benchmark = find_benchmark_result(
            result=reference,
            mode=benchmark.get("mode"),
            precision=benchmark.get("precision"),
        )
        if ref_benchmark and isinstance(ref_benchmark, dict):
            ref_value = ref_benchmark.get("value")
        else:
            ref_value = None
        benchmark.update({"reference_value": ref_value})
        benchmark.update({"diff": get_diff(benchmark)})


def get_diff(benchmark):
    """Calculate the difference between new and reference results."""
    new_value = benchmark.get("value")
    reference_value = benchmark.get("reference_value")
    if new_value and reference_value:
        return (new_value - reference_value) / reference_value
    return None


def find_benchmark_result(result, mode, precision):
    """Find benchmark result for specified mode and precision."""
    for benchmark in result.get("benchmarks", []):
        benchmark_modes = [benchmark.get("mode")]
        if "performance" in benchmark_modes:
            benchmark_modes.append("throughput")
        benchmark_precision = benchmark.get("precision")
        if mode in benchmark_modes and benchmark_precision == precision:
            return benchmark
    return None


def check_threshold(result, precision, mode):
    """Check if diffs are below threshold. If not add mode and precision to rerun."""
    for benchmark in result.get("benchmarks", []):
        op = operator.gt
        modifier = 1  # Lower is better
        if benchmark.get("mode") in ["throughput", "performance"]:
            modifier = -1  # Higher is better
            op = operator.lt
        if precision is not None and benchmark.get("precision") != precision:
            continue  # Skip precision

        if mode is not None and benchmark.get("mode") != mode:
            continue  # Skip mode

        diff = benchmark.get("diff")
        if diff and op(
            diff, (args.threshold * modifier)
        ):
            drops.append(f"{benchmark.get('mode')},{benchmark.get('precision')}")


def get_result(file_path):
    """Read result from specified path."""
    data = []
    try:
        with open(file_path, "r") as f:
            data = json.load(f)
    except:
        return None

    if isinstance(data, list):
        for result in data:
            if check_result(result, args.framework, args.model, args.os, args.platform):
                return result
    else:
        if check_result(data, args.framework, args.model, args.os, args.platform):
            return data
    return None


def check_result(result, framework, model, os, platform):
    if (
        result.get("framework") == framework
        and result.get("model") == model
        and result.get("os") == os
        and result.get("platform") == platform
    ):
        return result


if __name__ == "__main__":
    args = parse_args()
    main()
