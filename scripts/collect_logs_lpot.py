import argparse
import os
import platform
import re
from utils.result import Result, Measurement
import glob
from get_stable_iteration import get_average_latency
import ast
import json

parser = argparse.ArgumentParser(allow_abbrev = False)
parser.add_argument("--framework", type=str, required=True)
parser.add_argument("--python_version", type=str, default="")
parser.add_argument("--model", type=str, required=True)
parser.add_argument("--mr", action="store_true")
parser.add_argument("--perf_steps", action="store_true")
parser.add_argument("--logs_dir", type=str, default=".")
parser.add_argument("--output_dir", type=str, default=".")
parser.add_argument("--required", type=ast.literal_eval)
parser.add_argument("--logs_prefix_url", type=str, default="")
parser.add_argument("--job_url", type=str, default="")
args = parser.parse_args()

print(args)

os_name = str(platform.system()).lower()
cpu_name = os.environ.get("CPU_NAME", "unknown").lower()

result = Result()
result.framework = args.framework
result.version = "N/A"
result.python = args.python_version
result.model = args.model
result.os = os_name
result.platform = cpu_name

def main():
    update_framework_version(result)
    tuning_log = os.path.join(args.logs_dir, f"{args.framework}-{args.model}-{os_name}-{cpu_name}-tune.log")
    read_tuning_log(tuning_log)

    logs_pattern = os.path.join(args.logs_dir, f"{args.framework}-{args.model}-*.log")
    log_files = glob.glob(logs_pattern)
    configs = get_config_tree(log_files)
    for precision, modes in configs.items():
        for mode in modes:
            read_perf_logs(precision, mode)

    print(json.dumps(result.serialize()))
    result.save_summary(args.output_dir)

def get_config_tree(log_files):
    configs = {}
    for log_file in log_files:
        if "tune.log" in log_file:
            continue
        params = os.path.basename(log_file).replace(f"{args.framework}-{args.model}-", "").split("-")
        precision = params[0]
        mode = params[1]
        if precision not in configs.keys():
            configs.update({precision: []})
        if mode not in configs.get(precision):
            configs.get(precision).append(mode)

    # Add required configs
    if args.required:
        for required in args.required:
            precision = required.get("precision")
            mode = required.get("mode")
            if required.get("precision") not in configs.keys():
                configs.update({precision: []})
            if mode not in configs.get(precision):
                configs.get(precision).append(mode)


    return configs

def read_tuning_log(tuning_file):
    result.tuning.log = args.logs_prefix_url + os.path.basename(tuning_file)
    with open(tuning_file, "r") as f:
        for line in f:
            parse_tuning_line(line)
    if args.mr:
        # Read accuracy from tuning
        result.benchmarks.append(Measurement({
            "mode": "accuracy",
            "precision": "fp32",
            "value": result.tuning.baseline_acc,
            "log": result.tuning.log
        }))
        result.benchmarks.append(Measurement({
            "mode": "accuracy",
            "precision": "int8",
            "value": result.tuning.tuned_acc,
            "log": result.tuning.log
        }))


def parse_tuning_line(line):
    tuning_strategy = re.search(r"Tuning strategy:\s+([A-Za-z]+)", line)
    if tuning_strategy and tuning_strategy.group(1):
        result.tuning.strategy = tuning_strategy.group(1)

    baseline_acc = re.search(r"FP32 baseline is:\s+\[accuracy:\s(\d+(\.\d+)?), duration \(seconds\):\s*(\d+(\.\d+)?)\]", line)
    if baseline_acc and baseline_acc.group(1):
        result.tuning.baseline_acc = float(baseline_acc.group(1))

    tuned_acc = re.search(r"Best tune result is:\s+\[accuracy:\s(\d+(\.\d+)?), duration \(seconds\):\s(\d+(\.\d+)?)\]", line)
    if tuned_acc and tuned_acc.group(1):
        result.tuning.tuned_acc = float(tuned_acc.group(1))

    tune_trial = re.search(r"Tune \d*\s*result is:", line)
    if tune_trial:
        result.tuning.trials += 1

    tune_time = re.search(r"Tuning time spend:\s+(\d+(\.\d+)?)s", line)
    if tune_time and tune_time.group(1):
        result.tuning.time = int(tune_time.group(1))

    fp32_model_size = re.search(r"The input model size is:\s+(\d+(\.\d+)?)", line)
    if fp32_model_size and fp32_model_size.group(1):
        result.tuning.fp32_model_size = int(fp32_model_size.group(1))

    int8_model_size = re.search(r"The output model size is:\s+(\d+(\.\d+)?)", line)
    if int8_model_size and int8_model_size.group(1):
        result.tuning.int8_model_size = int(int8_model_size.group(1))

    total_mem_size = re.search(r"Total resident size\D*([0-9]+)", line)
    if total_mem_size and total_mem_size.group(1):
        result.tuning.total_mem_size = float(total_mem_size.group(1))

    max_mem_size = re.search(r"Maximum resident set size\D*([0-9]+)", line)
    if max_mem_size and max_mem_size.group(1):
        result.tuning.max_mem_size = float(max_mem_size.group(1))

def read_perf_logs(precision, mode):
    logs_pattern = os.path.join(
        args.logs_dir,
        f"{args.framework}-{args.model}-{precision}-{mode}-{os_name}-{cpu_name}*",
    )
    log_files = glob.glob(logs_pattern)
    if len(log_files) == 0:
        result.benchmarks.append(Measurement({
            "mode": mode,
            "precision": precision,
            "log": args.job_url,
        }))
        return
    partials = []
    num_instances = None
    for log in log_files:
        partial = {}
        with open(log, "r") as f:
            for line in f:
                parse_result = parse_perf_line(mode, line)
                partial = update_partial(partial, parse_result)
        if args.mr and args.perf_steps:
            partial.update({"latency": get_average_latency(log, 200, 200, s_to_ms=True)})

        partial, num_instances = normalize_partial(partial)
        partials.append(partial)

    measurement = Measurement()
    measurement.batch_size = partials[0].get("batch_size")
    measurement.instances = num_instances if num_instances else len(partials)
    measurement.mode = mode
    measurement.precision = precision
    measurement.log = args.logs_prefix_url + os.path.basename(log_files[0])

    if mode == "latency" and "latency" in partials[0].keys():
        latency = [item.get("latency") for item in partials]
        measurement.value = round(sum(latency)/len(latency), 4)
    if mode == "throughput" and "throughput" in partials[0].keys():
        measurement.value = round(sum([item.get("throughput") for item in partials]), 4)
    if mode == "accuracy" and "accuracy" in partials[0].keys():
        measurement.value = partials[0].get("accuracy")
    
    result.benchmarks.append(measurement)

def parse_perf_line(mode: str, line: str) -> dict:
    perf_data = {}
    batch_size = re.search(r"Batch size = ([0-9]+)", line)
    if batch_size and batch_size.group(1):
        perf_data.update({"batch_size": int(batch_size.group(1))})

    throughput = re.search(r"Throughput:\s+(\d+(\.\d+)?)", line)
    if throughput and throughput.group(1):
        perf_data.update({"throughput": float(throughput.group(1))})

    latency = re.search(r"Latency:\s+(\d+(\.\d+)?)", line)
    if latency and latency.group(1):
        perf_data.update({"latency": float(latency.group(1))})

    accuracy_patterns = [
        r"Accuracy:\s+(\d+(\.\d+)?)",
        r"Accuracy is\s+(\d+(\.\d+)?)"
    ]
    for acc_pattern in accuracy_patterns:
        accuracy = re.search(acc_pattern, line)
        if accuracy and accuracy.group(1):
            perf_data.update({"accuracy": float(accuracy.group(1))})
            break

    return perf_data


def update_partial(partial: dict, parsed_result: dict):
    for key, value in parsed_result.items():
        partial_value = partial.get(key)
        if not partial_value:
            partial.update({key: [value]})
        elif isinstance(partial_value, list):
            partial[key].append(value)
        else:
            partial.update({key: [partial_value, value]})
    return partial

def normalize_partial(partial: dict) -> dict:
    num_instances = None
    for key, value in partial.items():
        if isinstance(value, list):
            partial.update({key: summarize_values(key, value)})
            if isinstance(value, list) and len(value) > 1:
                num_instances = len(value)
    return partial, num_instances


def summarize_values(key: str, value: list):
    if key == "latency":
        return round(sum(value)/len(value), 4)
    if key == "throughput":
        return round(sum(value), 4)
    if key in ["accuracy", "batch_size"]:
        return value[0]


def update_framework_version(result: Result) -> None:
    fw_modules = {
        "tensorflow": "tensorflow",
        "onnxrt": "onnxruntime",
        "mxnet": "mxnet",
        "pytorch": "torch"
    }
    fw_module_name = fw_modules.get(result.framework, None)
    if fw_module_name is None:
        return
    import importlib
    fw_module = importlib.import_module(fw_module_name)
    result.version = fw_module.__version__

if __name__ == "__main__":
    main()
