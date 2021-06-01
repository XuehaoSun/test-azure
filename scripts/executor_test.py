import argparse
import os
import platform
from scripts.executors.factory import ExecutorFactory
from scripts.utils.utils import str2bool

parser = argparse.ArgumentParser(allow_abbrev=False)
parser.add_argument("--framework", type=str, required=True)
parser.add_argument("--model", type=str, required=True)
parser.add_argument("--model_src_dir", type=str, required=True)
parser.add_argument("--dataset_location", type=str, required=True)
parser.add_argument("--input_model", type=str, required=True)
parser.add_argument("--yaml", type=str, required=True)
parser.add_argument("--strategy", type=str, required=True)
parser.add_argument("--max_trials", type=int, default=None)
parser.add_argument("--cpu", type=str, default="")
parser.add_argument("--operating_system", type=str, default="")
parser.add_argument("--new_benchmark", type=str2bool, required=True)
parser.add_argument("--mode", choices=["tuning"], default="tuning")
parser.add_argument("--output_dir", type=str, default=".") 

args = parser.parse_args()

if not args.cpu:
        args.cpu = os.environ.get("CPU_NAME", "unknown").lower()

if not args.operating_system:
    args.operating_system = str(platform.system()).lower()

print(args)


executor = ExecutorFactory.get_executor(args)
print("Command for specified configuration is: ")
print(executor.cmd)
