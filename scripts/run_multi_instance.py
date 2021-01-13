import argparse
import subprocess
from threading import Lock, Thread
from typing import Any, List
from utils.utils import get_number_of_sockets
from utils.multi_instance import execute_multi_instance
import platform
import os

import psutil

LOCK = Lock()

parser = argparse.ArgumentParser()
parser.add_argument("--cmd", type=str, required=True, help="Commandline for execution")
parser.add_argument("--instances", type=int, required=True, help="Number of instances.")
parser.add_argument("--sockets", type=int, required=True, help="Number of sockets.")
parser.add_argument("--shell", type=bool, required=False, default=False, help="Use shell for command evaluation.")
parser.add_argument("--output-prefix", type=str, required=True, help="Base name for output logs.")
parser.add_argument("--use-ht", action="store_true", help="Use both physical and logical cores.")
args = parser.parse_args()

num_cores = psutil.cpu_count(logical=False)
num_sockets = get_number_of_sockets()

if args.sockets > num_sockets:
    raise Exception(f"Not enough sockets. This platform has {num_sockets} socket(s).")

cores_per_socket = num_cores // num_sockets
available_cores = cores_per_socket * args.sockets
cores_per_instance = available_cores // args.instances

if num_cores % args.instances != 0:
    raise Exception("Could not divide cores per instance.")

if args.instances % args.sockets != 0:
    raise Exception("Could not divide instances per sockets.")

print(f"Using {cores_per_instance} cores per instance.")



if __name__ == "__main__":
    execute_multi_instance(args.cmd, args.output_prefix, args.use_ht, args.shell)


