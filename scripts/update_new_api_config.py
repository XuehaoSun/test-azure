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
    parser.add_argument("--max-trials", type=int, required=False, help="Limit for tuning trials.")
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
    with open(args.main_script, "r") as f:
        a = f.readlines()
    with open(args.main_script, "w") as f:
        stack = []
        for line in a:
            result = parse_line(line, stack)
            f.write(result)


def parse_line(line: str, stack: list):
    if args.iteration or args.cores_per_instance or args.num_of_instance:
        config = re.search(r"BenchmarkConfig\(", line)
        if config or stack:
            update_stack(line, stack)
            pre_iteration = re.search(r"iteration=\d+", line)
            if pre_iteration:
                print("previous ", pre_iteration.group(), ", current iteration=", args.iteration)
                line = re.sub(r"iteration=(\d+)", f"iteration={args.iteration}", line)
            pre_cores_per_instance = re.search(r"cores_per_instance=\d+", line)
            if pre_cores_per_instance:
                print("previous ", pre_cores_per_instance.group(),
                      ", current cores_per_instance=", args.cores_per_instance)
                line = re.sub(r"cores_per_instance=(\d+)", f"cores_per_instance={args.cores_per_instance}", line)
            pre_num_of_instance = re.search(r"num_of_instance=\d+", line)
            if pre_num_of_instance:
                print("previous ", pre_num_of_instance.group(),
                      ", current num_of_instance=", args.num_of_instance)
                line = re.sub(r"num_of_instance=(\d+)", f"num_of_instance={args.num_of_instance}", line)

    return line


def update_stack(line, stack):
    for i in line:
        stack.append("(") if i == "(" else 0
        stack.pop() if i == ")" else 0


if __name__ == "__main__":
    update_config()
