import argparse
import re
from typing import Optional

import platform

system = platform.system()
try:
    import ruamel.yaml as yaml
except:
    import ruamel_yaml as yaml

from utils.utils import update_yaml_config

def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--yaml", type=str, required=True, help="Path to yaml config.")
    parser.add_argument("--strategy", type=str, required=False, help="Strategy to update.")
    parser.add_argument("--strategy-token", type=str, required=False, help="Token for sigopt strategy.")
    parser.add_argument("--mode", type=str, required=False, help="Benchmark mode.")
    parser.add_argument("--batch-size", type=int, required=False, help="Benchmark batch size.")
    parser.add_argument("--iteration", type=int, required=False, help="Benchmark iteration")
    parser.add_argument("--max-trials", type=int, required=False, help="Limit for tuning trials.")
    parser.add_argument("--algorithm", type=str, required=False, help="Algorithm for quantization.")
    parser.add_argument("--sampling_size", type=str, required=False, help="Sampling size for calibration.")
    parser.add_argument("--timeout", type=int, required=False, help="Tuning timeout.")
    parser.add_argument("--dtype", type=str, required=False, help="Quantize model precision type.")
    parser.add_argument("--tf_new_api", type=str, required=False, help="Set framework for tensorflow models.")
    return parser.parse_args()

if __name__ == "__main__":
    args = parse_args()
    update_yaml_config(
        yaml_file=args.yaml,
        strategy=args.strategy,
        mode=args.mode,
        batch_size=args.batch_size,
        iteration=args.iteration,
        max_trials=args.max_trials,
        algorithm=args.algorithm,
        timeout=args.timeout,
        strategy_token=args.strategy_token,
        sampling_size=args.sampling_size,
        dtype=args.dtype,
        tf_new_api=args.tf_new_api
    )
