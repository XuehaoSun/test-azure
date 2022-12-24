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
    parser.add_argument("--device", type=str, required=False, help="Device setting.")
    return parser.parse_args()


args = parse_args()
stack_dict = {}


def update_example_config():
    with open(args.main_script, "r") as f:
        a = f.readlines()
    with open(args.main_script, "w") as f:
        for line in a:
            result = parse_line(line)
            f.write(result)


def parse_line(line: str):
    if args.iteration or args.cores_per_instance or args.num_of_instance:
        line = check_config(line, "BenchmarkConfig")
    if args.strategy or args.max_trials:
        line = check_config(line, "TuningCriterion")
    if args.device or args.backend:
        line = check_config(line, "PostTrainingQuantConfig")
    return line


CONFIG_DICT = {
    "BenchmarkConfig": {
        "iteration":
            [f"{args.iteration}",
             r"iteration=(\d+)",
             f"iteration={args.iteration}"],
        "cores_per_instance":
            [f"{args.cores_per_instance}",
             r"cores_per_instance=(\d+)",
             f"cores_per_instance={args.cores_per_instance}"],
        "num_of_instance":
            [f"{args.num_of_instance}",
             r"num_of_instance=(\d+)",
             f"num_of_instance={args.num_of_instance}"]
    },
    "TuningCriterion": {
        "strategy":
            [f"{args.strategy}",
             r"strategy=\"(\w+)\"",
             f"strategy=\"{args.strategy}\""],
        "max_trials":
            [f"{args.max_trials}",
             r"max_trials=(\d+)",
             f"max_trials={args.max_trials}"]
    },
    "PostTrainingQuantConfig": {
        "device":
            [f"{args.device}",
             r"device=\"(\w+)\"",
             f"device=\"{args.device}\""],
        "backend":
            [f"{args.backend}",
             r"backend=\"(\w+)\"",
             f"backend=\"{args.backend}\""]
    }

}


def check_config(line: str, config_name: str):
    config_search_item = f"{config_name}" + r"\("
    if re.search(config_search_item, line) or stack_dict.get(config_name):
        stack_dict.setdefault(config_name, [])
        update_stack(line, stack_dict[config_name])
        for key, value in CONFIG_DICT[config_name].items():
            line = check_param(line, value)
    return line


def check_param(line: str, value: list):
    p_status = value[0]
    p_search_item = value[1]
    p_replace_item = value[2]
    search_result = re.search(p_search_item, line)
    if p_status and search_result:
        print("previous ", search_result.group(), ", current ", p_replace_item)
        line = re.sub(p_search_item, p_replace_item, line)
        print("Updated line --> ", line)
    return line


def update_stack(line: str, stack: list):
    for i in line:
        stack.append("(") if i == "(" else 0
        stack.pop() if i == ")" else 0


def update_default_config():
    import neural_compressor
    import os
    config_path = os.path.dirname(neural_compressor.__file__) + "/config.py"
    print("Update default config in neural_compressor/config.py, in case some config not in main scripts")
    with open(config_path, "r") as f:
        b = f.readlines()
    with open(config_path, "w") as f:
        for line in b:
            if args.strategy and args.strategy != "basic":
                line = replace_default_config(line, r"strategy=\"basic\",", f"strategy=\"{args.strategy}\",")
            if args.max_trials and args.max_trials != 100:
                line = replace_default_config(line, r"max_trials=100,", f"max_trials={args.max_trials},")
            if args.device and args.device != "cpu":
                line = replace_default_config(line, r"device=\"cpu\",", f"device=\"{args.device}\",")
            if args.backend and args.backend != "default":
                line = replace_default_config(line, r"backend=\"default\",", f"backend=\"{args.backend}\",")
            f.write(line)


def replace_default_config(line: str, s_pre: str, s_cur: str):
    if re.search(s_pre, line):
        line = re.sub(s_pre, s_cur, line)
        print("Current updated line is: ")
        print(line)
        print("------------------------------------------")
    return line


if __name__ == "__main__":

    if args.main_script:
        update_example_config()
    if (args.strategy and args.strategy != "basic") or (args.max_trials and args.max_trials != 100) \
            or (args.device and args.device != "cpu") or (args.backend and args.backend != "default"):
        update_default_config()
