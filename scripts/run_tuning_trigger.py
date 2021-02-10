import argparse
import datetime
import glob
import os
import platform
import re
import shutil
import subprocess
import sys
from typing import List

import psutil

import utils.consts as consts
from update_yaml_config import update_yaml_config
from utils.utils import (copy_files, execute_command, get_executable, get_size,
                         install_requirements, update_yaml)

parser = argparse.ArgumentParser(allow_abbrev = False)
parser.add_argument("--framework", type=str, required=True)
parser.add_argument("--model", type=str, required=True)
parser.add_argument("--model_src_dir", type=str, required=True)
parser.add_argument("--dataset_location", type=str, required=True)
parser.add_argument("--input_model", type=str, required=True)
parser.add_argument("--yaml", type=str, required=True)
parser.add_argument("--strategy", type=str, required=True)
parser.add_argument("--max_trials", type=int, required=True)
parser.add_argument("--cpu", type=str, required=True)


args = parser.parse_args()

print(args)


def main():

    operating_system = platform.system()

    # Temporary change for helloworld_keras
    if args.model == "helloworld_keras":
        args.model_src_dir=os.path.join(os.environ["WORKSPACE"], "lpot-models", "examples", "helloworld")

    if not os.path.isdir(args.model_src_dir):
        raise Exception(f"[ERROR] model_src_dir \"{args.model_src_dir}\" not exists.")
    
    install_requirements(requirements_file=os.path.join(args.model_src_dir, "requirements.txt"),
                         exclude=consts.EXCLUDED_REQUIREMENTS)

    # Temporary change for helloworld_keras
    if args.model == "helloworld_keras":
        subprocess.run(args=["python", "train.py"], cwd=args.model_src_dir, check=True)
        subprocess.run(
            args=["python", "test.py"],
            cwd=os.path.join(args.model_src_dir, "tf2.x"),
            check=True)
        sys.exit()

    # ------ WORKAROUND FOR MXNET RESNET50V1 -----
    topology=args.model
    if args.model == "resnet50v1":
        topology = "resnet50_v1"

    if args.model.endswith("_qat"):
        topology = f"{args.model}_qat"

    if args.model.endswith("_gpu"):
        topology = f"{args.model}_gpu"

    q_model=os.path.join(os.environ["WORKSPACE"], f"{args.framework}-{args.model}-tune")
    if args.framework == "tensorflow":
        q_model = f"{q_model}.pb"
    if args.framework == "mxnet":
        os.makedirs(q_model)
        q_model = os.path.join(q_model, topology)
    if args.framework == "onnxrt":
        q_model = f"{q_model}.onnx"

    parameters = get_tuning_parameters(args.framework, topology, q_model, operating_system)

    yaml_full_path = os.path.join(args.model_src_dir, args.yaml)
    update_yaml(
        yaml=yaml_full_path,
        framework=args.framework,
        dataset_location=args.dataset_location,
        strategy=args.strategy,
        max_trials=args.max_trials)

    print("\nPrint_updated_yaml... ")
    with open(yaml_full_path, 'r') as yaml_file:
        print(yaml_file.read())

    print("\nRun_tuning parameters... ")
    print(parameters)
    total_memory = psutil.virtual_memory().total

    cmd = get_executable("tuning")
    if args.framework == "onnxrt" and args.model == "bert_base_MRPC":
        cmd = ["python", "bert_base.py"]
    cmd.extend(parameters)
    print("Executing command: '" + " ".join(cmd) + "' in '" + args.model_src_dir + "' directory.")

    start_time = datetime.datetime.now()

    system = platform.system().lower()
    log_file = os.path.join(
        os.environ["WORKSPACE"],
        f"{args.framework}-{args.model}-{system}-{args.cpu}-tune.log")

    # Copy tuning config to allow collecting it as an artifact
    copy_files(yaml_full_path, os.path.join(os.environ["WORKSPACE"], "tuning_config.yaml"))

    execute_command(args=cmd,
                    cwd=args.model_src_dir,
                    shell=True,
                    file=log_file)

    stop_time = datetime.datetime.now()
    duration = (stop_time-start_time).total_seconds()

    fp32_model_size, int8_model_size = collect_model_size(args.input_model, q_model)

    tuning_info = [
        f"Tuning strategy: {args.strategy}",
        f"Tuning time spend: {int(duration)}s",
        f"The input model size is: {fp32_model_size}",
        f"The output model size is: {int8_model_size}",
        f"Total resident size (kbytes): {total_memory // 1024}"
    ]

    with open(log_file, "a") as log:
        for info in tuning_info:
            print(info)
            log.write(info + "\n")

    # copy tuning result to tmp dir
    tmp_dir = "/tmp"
    if operating_system == "Windows":
        tmp_dir = os.environ["Temp"]

    save_path=os.path.join(
        tmp_dir,
        f"{args.framework}-{args.model}-tune-{int(datetime.datetime.now().timestamp())}")

    hostname = platform.node()
    print(f"HOSTNAME IS {hostname}")
    print(f"!!!tune model save path is {save_path} !!!")
    os.makedirs(save_path)
    copy_files(yaml_full_path, save_path)  # Copy yaml config
    copy_files(f"{repr(q_model)}*", save_path)


def collect_model_size(fp32_model: str, int8_model: str):
    if args.framework == "tensorflow":
        if args.model == "style_transfer":
            fp32_model_size = get_size(f"{args.input_model}.meta", add_unit = True)
        else:
            fp32_model_size = get_size(fp32_model, add_unit = True)
        int8_model_size = get_size(int8_model, add_unit = True)
        
        from count_quantize_op import count_quantize_op
        count_quantize_op(fp32_model, int8_model)

    elif args.framework == "mxnet":
        fp32_model_size = get_size(fp32_model, add_unit = True)
        int8_model_size = get_size(os.path.dirname(int8_model), add_unit = True)
    elif args.framework == "pytorch":
        fp32_model_size="None"
        int8_model_size="None"
    elif args.framework == "onnxrt":
        fp32_model_size = get_size(fp32_model, add_unit = True)
        int8_model_size = get_size(int8_model, add_unit = True)
    else:
        fp32_model_size="0M"
        int8_model_size="0M"

    return fp32_model_size, int8_model_size


def get_tuning_parameters(framework: str, topology: str, q_model :str, os: str):
    os_map = {
        "Windows": get_windows_parameters,
        "Linux": get_linux_parameters,
    }
    parameter_parser = os_map.get(os, None)
    if parameter_parser is None:
        raise Exception(f"Could not found parameter parser for {os} OS.")

    return parameter_parser(framework, topology, q_model)


def get_windows_parameters(framework: str, topology: str, q_model: str):
    parameters_map = {
        "tensorflow": {
            "resnet50v1.0": [
                "--input-graph", f"{args.input_model}",
                "--output-graph", f"{q_model}",
                "--config", f"{args.yaml}",
                "--tune"

            ],
            "inception_v3": [
                 "--input-graph", f"{args.input_model}",
                "--output-graph", f"{q_model}",
                "--config", f"{args.yaml}",
                "--tune"
            ],
            "mobilenetv1": [
                 "--input-graph", f"{args.input_model}",
                "--output-graph", f"{q_model}",
                "--config", f"{args.yaml}",
                "--tune"
            ]
        },
        "onnxrt": {
            "resnet50_v1_5": [
                "--model_path", f"{args.input_model}",
                "--config", f"{args.yaml}",
                "--output_model", f"{q_model}",
                "--tune"
            ],
            "bert_base_MRPC": [
                "--model_path", f"{args.input_model}"
                "--data_dir", f"{args.dataset_location}",
                "--task_name", "mrpc",
                "--input_dir", "bert-base-uncased",
                "--config" f"{args.yaml}",
                "--output_model", f"{q_model}"
                "--tune"
            ]
        }
    }
    parameters = parameters_map.get(framework, {}).get(topology, None)
    if parameters is None:
        raise NotImplementedError
    return parameters


def get_linux_parameters(framework: str, topology: str, q_model: str):
    if framework == "onnxrt":
        return [
            f"--config={args.yaml}",
            f"--input_model={args.input_model}",
            f"--output_model={q_model}"
        ]

    parameters = [
        f"--topology={topology}",
        f"--dataset_location={args.dataset_location}",
        f"--input_model={args.input_model}"
    ]

    if args.framework in ["tensorflow", "mxnet"]:
        parameters.append(f"--output_model={q_model}")

    # new config with yaml
    if args.framework == "tensorflow":
      if any(args.model_src_dir in model_dir for model_dir in ["image_recognition", "object_detection"]):
        parameters.extend([
            f"--config={args.yaml}",
            f"--input_model={args.input_model}",
            f"--output_model=${q_model}"
        ])
    
    return parameters


if __name__ == "__main__":
    main()
