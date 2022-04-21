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
import utils.consts as consts
from utils.multi_instance import execute_multi_instance

import psutil

from update_yaml_config import update_yaml_config
from utils.utils import install_requirements, get_executable, execute_command, get_number_of_sockets, replace_line, insert_line, update_yaml as update_yaml_tuning

parser = argparse.ArgumentParser(allow_abbrev = False)
parser.add_argument("--framework", type=str, required=True)
parser.add_argument("--model", type=str, required=True)
parser.add_argument("--model_src_dir", type=str, required=True)
parser.add_argument("--dataset_location", type=str, required=True)
parser.add_argument("--input_model", type=str, required=True)
parser.add_argument("--precision", type=str,
                    choices=consts.SUPPORTED_DATATYPES, required=True)
parser.add_argument("--mode", type=str,
                    choices=consts.SUPPORTED_MODES, required=True)
parser.add_argument("--batch_size", type=int, required=True)
parser.add_argument("--yaml", type=str, required=True)
parser.add_argument("--cpu", type=str, required=True)
parser.add_argument("--multi_instance", action="store_true")

args = parser.parse_args()

print(args)

excluded_requirements = [
    "ilit",
    "tensorflow",
    "torch",
    "mxnet",
    "onnx",
    "ort_nightly",
]

operating_system = platform.system()

# Run Benchmark
def main():

    if not os.path.isdir(args.model_src_dir):
        raise Exception(
            f"[ERROR] model_src_dir \"{args.model_src_dir}\" not exists.")

    install_requirements(requirements_file=os.path.join(args.model_src_dir, "requirements.txt"),
                         exclude=consts.EXCLUDED_REQUIREMENTS)

    print("\nSet a modified yaml...")
    yaml_path = os.path.join(args.model_src_dir, args.yaml)
    benchmark_yaml_path = os.path.join(args.model_src_dir, "benchmark.yaml")
    print(f"{yaml_path}")
    shutil.copyfile(yaml_path, benchmark_yaml_path)
    yaml_path = benchmark_yaml_path
    print(f"{yaml_path}")

    q_model = os.path.join(
        os.environ["WORKSPACE"], f"{args.framework}-{args.model}-tune")

    if args.framework == "tensorflow":
        q_model = f"{q_model}.pb"
    if args.framework == "mxnet" and "object_detection" in args.model_src_dir:
        q_model = os.path.join(q_model, args.model)
    if args.framework == "onnxrt":
        q_model = f"{q_model}.onnx"

    # ------ WORKAROUND FOR MXNET RESNET50V1 -----
    topology = args.model
    if args.model == "resnet50v1":
        topology = "resnet50_v1"

    if args.model.endswith("_qat"):
        topology = f"{args.model}_qat"

    input_model = args.input_model
    # pytorch int8 still use fp32 input_model
    if args.precision == "int8" and args.framework != "pytorch":
        input_model = q_model

    parameters = get_benchmark_parameters(
        args.framework, topology, input_model, operating_system)

    print("\nStart run function...")
    if args.mode == "accuracy":
        run_accuracy(parameters, yaml_path=yaml_path)
    else:
        run_benchmark(parameters, mode=args.mode, topology=topology, yaml_path=yaml_path)


def run_accuracy(parameters, yaml_path):
    yaml_update_parameters = {
        "yaml_path": yaml_path,
        "batch_size": args.batch_size
    }

    if args.framework == "tensorflow":
        if any(args.model_src_dir in model_dir for model_dir in ["image_recognition/tensorflow_models/quantization/ptq", "object_detection/tensorflow_models/quantization/ptq"]):
            iters = -1
            yaml_update_parameters.update({"iters": iters})

        parameters = [
            f"--config=benchmark.yaml",
            f"--input_model={args.input_model}"
        ]

    update_yaml(**yaml_update_parameters)

    cmd = get_executable("benchmark")
    cmd.extend(parameters)

    system = platform.system().lower()
    log_file = os.path.join(
        os.environ["WORKSPACE"],
        f"{args.framework}-{args.model}-{args.precision}-{args.mode}-{system}-{args.cpu}.log")

    execute_command(args=cmd,
                    cwd=args.model_src_dir,
                    shell=True,
                    file=log_file)


def run_benchmark(parameters, mode, topology, yaml_path):
    # define a low iteration list to save time
    # if latency ~ 500 ms , then set iter = 200. if latency ~ 1000 ms, then set iter = 100
    latency_high_500 = [
        "arttrack-coco-multi",
        "arttrack-mpii-single",
        "DeepLab",
        "east_resnet_v1_50",
        "mask_rcnn_resnet50_atrous_coco"
    ]

    latency_high_1000 = [
        "dilation"
        "efficientnet-b7_auto_aug",
        "faster_rcnn_inception_resnet_v2_atrous_coco",
        "faster_rcnn_nas_coco",
        "faster_rcnn_nas_lowproposals_coco"
        "gmcnn-places2",
        "i3d-flow",
        "i3d-rgb",
        "icnet-camvid-ava-0001"
        "icnet-camvid-ava-sparse-30-0001",
        "icnet-camvid-ava-sparse-60-0001",
        "mask_rcnn_inception_resnet_v2_atrous_coco",
        "mask_rcnn_resnet101_atrous_coco"
        "person-vehicle-bike-detection-crossroad-yolov3-1024",
        "Transformer-LT",
        "unet-3d-isensee_2017",
        "unet-3d-origin"
        "VNet",
    ]

    # get cpu information for multi-instance
    total_cores = psutil.cpu_count(logical=False)
    total_sockets = get_number_of_sockets()
    ncores_per_socket = total_cores // total_sockets

    batch_size = args.batch_size

    if mode == "latency":
        ncores_per_instance = 4
        batch_size = 1
        iters = 500
        if args.model == "wide_deep_large_ds":
            batch_size = 100

        # walk around for pytorch yolov3 model, failed in load 194 iteration.
        if args.framework == "pytorch" and args.model == "yolo_v3":
            iters = 150

        # custom iteration
        if args.model in latency_high_500:
            iters = 200
        if args.model in latency_high_1000:
            iters = 100
    else:
        # Use whole socket per instance
        ncores_per_instance = ncores_per_socket
        iters = 100

    if operating_system == "Linux":
        parameters.extend([
            "--mode=benchmark",
            f"--batch_size={args.batch_size}",
            f"--iters={iters}"
        ])

    # Workaround for deeplab stability
    if "tf_oob_models" in args.model_src_dir:
        warmup_iters = 100

        replace_line(file_path=os.path.join(args.model_src_dir, get_executable("benchmark")),
                     regex=r'num_warmup \d+',
                     string=f"num_warmup {warmup_iters}")

    # Disable fp32 optimization for oob models on TF1.15UP1
    if args.framework == "tensorflow" and topology in ["RetinaNet50", "ssd_resnet50_v1_fpn_coco"]:
        import tensorflow as tf
        tensorflow_version=tf.VERSION

        if args.precision == "fp32" and tensorflow_version == "1.15.0up1":
            insert_line(file_path=os.path.join(args.model_src_dir, get_executable("benchmark")),
                        line_pattern="models_need_disable_optimize=(",
                        string=topology)

    if args.framework == "tensorflow":
        if any(args.model_src_dir in model_dir for model_dir in ["image_recognition/tensorflow_models/quantization/ptq", "object_detection/tensorflow_models/quantization/ptq"]):
            parameters=[
                f"--config=benchmark.yaml",
                f"--input_model={args.input_model}"
            ]

    env_vars = {
        "OMP_NUM_THREADS": str(ncores_per_instance),
        "LOGLEVEL": "DEBUG"
    }
    
    
    update_yaml(yaml_path, batch_size, iters)

    cmd = get_executable("benchmark")
    cmd.extend(parameters)

    system = platform.system().lower()
    log_prefix=os.path.join(
        os.environ["WORKSPACE"],
        f"{args.framework}-{args.model}-{args.precision}-{args.mode}-{system}-{args.cpu}")
    
    num_sockets = 1  # Use only one socket
    num_instances = (ncores_per_socket * num_sockets) // ncores_per_instance

    print(f"Execute command: {cmd}")
    if args.multi_instance:
        execute_multi_instance(cmd=cmd,
                            cwd=args.model_src_dir,
                            instances=num_instances,
                            sockets=num_sockets,
                            output_prefix=log_prefix,
                            use_ht=False,
                            shell=True,
                            env=env_vars)
    else:
        execute_command(args=cmd,
                        cwd=args.model_src_dir,
                        shell=True,
                        file=f"{log_prefix}.log")


def update_yaml(yaml_path, batch_size=None, iters=None):
    if not os.path.isfile(yaml_path):
        raise Exception(f"Not found yaml config at '{yaml_path}' location.")

    update_yaml_config(
        yaml_file=yaml_path,
        batch_size=batch_size,
        iteration=iters,
        mode=args.mode
    )
    print("\nPrint updated yaml... ")
    with open(yaml_path, "r") as yaml_file:
        print(yaml_file.read())


def get_benchmark_parameters(framework, topology, q_model, os):
    os_map={
        "Windows": get_windows_parameters,
        "Linux": get_linux_parameters,
    }
    parameter_parser=os_map.get(os, None)
    if parameter_parser is None:
        raise Exception(f"Could not found parameter parser for {os} OS.")

    return parameter_parser(framework, topology, q_model)


def get_windows_parameters(framework: str, topology: str, input_model: str):
     return [
        "--model_path", f"{input_model}",
        "--config", "benchmark.yaml",
        "--output_model", ".", # Main script requires passing "output_model" however it is not used in benchmark mode.
        "--benchmark"
    ]


def get_linux_parameters(framework: str, topology: str, input_model: str):
    parameters=[
        f"--topology={topology}",
        f"--dataset_location={args.dataset_location}",
        f"--input_model={input_model}"
    ]

    # add flag for pytorch int8
    if framework == "pytorch" and args.precision == "int8":
        parameters.append("--int8=true")

    return parameters


if __name__ == "__main__":
    main()
