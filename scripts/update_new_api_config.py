import argparse
import re


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--main_script", type=str, required=True, help="Path to main_script.")
    parser.add_argument("--strategy", type=str, required=False, help="Strategy to update.")
    parser.add_argument("--strategy-token", type=str, required=False, help="Token for sigopt strategy.")
    parser.add_argument("--batch-size", type=int, required=False, help="Benchmark batch size.")
    parser.add_argument("--iteration", type=int, required=False, help="Benchmark iteration")
    parser.add_argument("--cores_per_instance", type=int, required=False, help="Benchmark cores_per_instance")
    parser.add_argument("--num_of_instance", type=int, required=False, help="Benchmark num_of_instance")
    parser.add_argument("--max_trials", type=int, required=False, help="Limit for tuning trials.")
    parser.add_argument("--criterion_rule", type=str, required=False, help="Update for tuning accuracy_criterion.")
    parser.add_argument("--criterion_data", type=float, required=False, help="Update for tuning accuracy_criterion.")
    parser.add_argument("--algorithm", type=str, required=False, help="Algorithm for quantization.")
    parser.add_argument("--sampling_size", type=str, required=False, help="Sampling size for calibration.")
    parser.add_argument("--timeout", type=int, required=False, help="Tuning timeout.")
    parser.add_argument("--dtype", type=str, required=False, help="Quantize model precision type.")
    parser.add_argument("--backend", type=str, required=False, help="Framework backend.")
    parser.add_argument("--is_gpu", type=str, required=False, help="Device setting.")
    return parser.parse_args()


args = parse_args()

def update_config():
    stack_dict = {}
    with open(args.main_script, "r") as f:
        a = f.readlines()
    with open(args.main_script, "w") as f:
        for line in a:
            result = parse_line(line, stack_dict)
            f.write(result)

    if (args.strategy and args.strategy != "basic") or (args.max_trials and args.max_trials != 100):
        update_default_config()


def update_default_config():
    import neural_compressor
    import os
    config_path = os.path.dirname(neural_compressor.__file__) + "/config.py"
    print("Update default config in neural_compressor/config.py, in case some config not in main scripts")
    with open(config_path, "r") as f:
        b = f.readlines()
    with open(config_path, "w") as f:
        for line in b:
            if args.strategy and args.strategy != "basic" and re.search(r"strategy=\"basic\",", line):
                line = re.sub(r"strategy=\"basic\",", f"strategy=\"{args.strategy}\",", line)
                print("Update default strategy, current updated line is: ")
                print(line)
                print("------------------------------------------")
            if args.max_trials and args.max_trials != 100 and re.search(r"max_trials=100,", line):
                line = re.sub(r"max_trials=100,", f"max_trials={args.max_trials},", line)
                print("Update default max_trials, current updated line is: ")
                print(line)
                print("------------------------------------------")
            f.write(line)


def parse_line(line: str, stack_dict: dict):

    if args.iteration or args.cores_per_instance or args.num_of_instance:
        if re.search(r"BenchmarkConfig\(", line) or stack_dict.get("BenchmarkConfig"):
            stack_dict.setdefault("BenchmarkConfig", [])
            update_stack(line, stack_dict["BenchmarkConfig"])
            pre_iteration = re.search(r"iteration=(\d+)", line)
            if pre_iteration:
                print("previous ", pre_iteration.group(), ", current iteration=", args.iteration)
                line = re.sub(r"iteration=(\d+)", f"iteration={args.iteration}", line)
                print("Updated line of iteration --> ", line)
            pre_cores_per_instance = re.search(r"cores_per_instance=(\d+)", line)
            if pre_cores_per_instance:
                print("previous ", pre_cores_per_instance.group(),
                      ", current cores_per_instance=", args.cores_per_instance)
                line = re.sub(r"cores_per_instance=(\d+)", f"cores_per_instance={args.cores_per_instance}", line)
                print("Updated line of cores_per_instance --> ", line)
            pre_num_of_instance = re.search(r"num_of_instance=(\d+)", line)
            if pre_num_of_instance:
                print("previous ", pre_num_of_instance.group(),
                      ", current num_of_instance=", args.num_of_instance)
                line = re.sub(r"num_of_instance=(\d+)", f"num_of_instance={args.num_of_instance}", line)
                print("Updated line of num_of_instance --> ", line)

    if args.strategy or args.max_trials:
        if re.search(r"TuningCriterion\(", line) or stack_dict.get("TuningCriterion"):
            stack_dict.setdefault("TuningCriterion", [])
            update_stack(line, stack_dict["TuningCriterion"])
            pre_strategy = re.search(r"strategy=\"(\w+)\"", line)
            if pre_strategy:
                print("strategy ", pre_strategy.group(), ", current strategy= ", args.strategy)
                line = re.sub(r"strategy=\"\w+\"", f"strategy=\"{args.strategy}\"", line)
                print("Updated line of strategy --> ", line)
            pre_max_trials = re.search(r"max_trials=(\d+)", line)
            if pre_max_trials:
                print("previous ", pre_max_trials.group(), ", current max_trials=", args.max_trials)
                line = re.sub(r"max_trials=(\d+)", f"max_trials={args.max_trials}", line)
                print("Updated line of max_trials --> ", line)

    return line


def update_stack(line: str, stack: list):
    for i in line:
        stack.append("(") if i == "(" else 0
        stack.pop() if i == ")" else 0


if __name__ == "__main__":
    update_config()
