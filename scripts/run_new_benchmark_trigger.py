"""New benchmark trigger."""

import argparse
import math
import os
import platform
import shutil
from typing import List

import psutil
import utils.consts as consts
from neural_compressor.ux.utils.workload.config import Config
from utils.utils import (
    execute_command,
    get_executable,
    get_number_of_sockets,
)

parser = argparse.ArgumentParser(allow_abbrev=False)
parser.add_argument("--framework", type=str, required=True)
parser.add_argument("--model", type=str, required=True)
parser.add_argument("--model_src_dir", type=str, required=True)
parser.add_argument("--input_model", type=str, required=True)
parser.add_argument("--precision", type=str, choices=consts.SUPPORTED_DATATYPES, required=True)
parser.add_argument("--mode", type=str, choices=consts.SUPPORTED_MODES, required=True)
parser.add_argument("--batch_size", type=int, required=True)
parser.add_argument("--multi_instance", action='store_true')
parser.add_argument("--yaml", type=str, required=True)
parser.add_argument("--cpu", type=str, required=True)
parser.add_argument("--output_path", type=str, default=os.environ.get("WORKSPACE"))
parser.add_argument("--dataset_location", type=str, required=False, help="Dataset location for ONNXRT LT models.")

args = parser.parse_args()
print(args)

if not os.path.exists(args.output_path):
    os.makedirs(args.output_path)

excluded_requirements = [
    "neural-compressor",
    "tensorflow",
    "torch",
    "mxnet",
    "onnx",
    "ort_nightly",
    "onnxruntime",
]

operating_system = platform.system()
yaml_record_file = os.path.join(args.output_path, "yaml_record.log")

def main():
    """Execute main function."""
    if not os.path.isdir(args.model_src_dir):
        raise Exception(f"[ERROR] model_src_dir \"{args.model_src_dir}\" not exists.")

    yaml_path = os.path.join(args.model_src_dir, args.yaml)
    benchmark_yaml_path = os.path.join(args.model_src_dir, "benchmark.yaml")
    shutil.copyfile(yaml_path, benchmark_yaml_path)
    yaml_path = benchmark_yaml_path

    with open(yaml_path, "r") as yaml_file:
        yaml_context = yaml_file.read()

    with open(yaml_record_file, 'a') as f:
        f.write("Origin yaml... \n")
        f.write(yaml_context)

    input_model = get_model_name(
        framework=args.framework,
        model=args.model,
        model_src_dir=args.model_src_dir,
        precision=args.precision,
        input_model=args.input_model,
    )

    parameters = get_benchmark_parameters(
        yaml_config=yaml_path,
        input_model=input_model,
        os=operating_system,
    )

    log_file = os.path.join(
        args.output_path,
        f"{args.framework}-{args.model}-{args.precision}-"
        f"{args.mode}-{operating_system.lower()}-{args.cpu}.log",
    )

    if args.mode == "accuracy":
        run_accuracy(
            parameters=parameters,
            yaml_path=yaml_path,
            log_file=log_file,
            input_model=input_model,

        )
    else:
        run_benchmark(
            parameters=parameters,
            yaml_path=yaml_path,
            log_file=log_file,
            mode=args.mode,
            input_model=input_model,
        )


def run_accuracy(parameters: List[str], yaml_path: str, log_file: str, input_model: str):
    """Run accuracy benchmark."""
    # Update yaml config
    lpot_config = Config()
    lpot_config.load(yaml_path)

    try:
        if lpot_config.evaluation.accuracy.dataloader:
            lpot_config.evaluation.accuracy.dataloader.batch_size = args.batch_size
        lpot_config.evaluation.accuracy.configs = None
        # walk around for anno_path yaml format issue.
        if ( lpot_config.evaluation.accuracy.metric.name == 'COCOmAP' ):
            if (lpot_config.evaluation.accuracy.metric.param == {}):
                lpot_config.evaluation.accuracy.metric = {'COCOmAP': {}}
    except AttributeError:
        print("[ WARNING ] Could not update accuracy config.")

    lpot_config.dump(yaml_path)
    print("\nPrint updated yaml... ")
    with open(yaml_path, "r") as yaml_file:
        yaml_context = yaml_file.read()
        print(yaml_context)
    with open(yaml_record_file, 'a') as f:
        f.write("\n\nAccuracy yaml... \n")
        f.write(yaml_context)


    # Set execution command
    parameters.append("--mode=accuracy")

    # Workaround for tensorflow bert_base_mrpc
    if args.framework == "tensorflow" and args.model == "bert_base_mrpc":
        parameters.extend([
            f"--dataset_location={args.dataset_location}",
            f"--init_checkpoint={args.input_model}",
            f"--batch_size={args.batch_size}"
        ])

    # Workaround for ONNXRT LT models
    if args.framework == "onnxrt" and args.model in ["bert_squad_model_zoo", "mobilebert_squad_mlperf"]:
            onnxrt_lt_mode = "accuracy" if args.mode == "accuracy" else "performance"
            parameters = [
                f"--config={yaml_path}",
                f"--input_model={input_model}",
                f"--mode={onnxrt_lt_mode}",
                f"--data_path={args.dataset_location}"
            ]

    # Workaround for ONNXRT googlenet-12,squeezenet,caffenet,alexnet
    if args.framework == "onnxrt" and args.model in ["googlenet-12", "squeezenet", "caffenet", "alexnet", "zfnet", "inception_v1", "alexnet_qdq", "caffenet_qdq", "googlenet-12_qdq", "zfnet_qdq", "inception_v1_qdq", "squeezenet_qdq"]:
        parameters.extend([
            f"--data_path={args.dataset_location}",
            f"--label_path={args.dataset_location}/../val.txt"
        ])
        
    if args.framework == "onnxrt" and args.model in [ "fcn_qdq", "fcn"]:
        parameters.extend([
            f"--data_path={args.dataset_location}",
            f"--label_path={args.dataset_location}/../annotations/instances_val2017.json"
        ])

    if args.framework == "onnxrt" and args.model in ["faster_rcnn", "mask_rcnn", "yolov3", "yolov4", "tiny_yolov3"]:
        parameters.extend([
            f"--data_path={args.dataset_location}"
        ])
    qdq_model_list = ["bert_squad_model_zoo_qdq", "mobilebert_squad_mlperf_qdq", "mask_rcnn_qdq", "ssd_mobilenet_v1-2_qdq", "faster_rcnn_qdq"]
    if args.framework == "onnxrt" and args.model in qdq_model_list:
        parameters.extend([
            f"--data_path={args.dataset_location}"
        ])

    if args.framework == "onnxrt" and args.model == "duc":
        parameters.extend([
            f"--data_path={args.dataset_location}",
            f"--label_path=/tf_dataset2/datasets/gtFine/val"
        ])
    # Workaround for engine
    if args.framework == "baremetal":
        tokenizer_dir=os.path.dirname(args.input_model)
        parameters.extend([
            f"--dataset_location={args.dataset_location}",
            f"--batch_size={args.batch_size}",
            f"--tokenizer_dir={tokenizer_dir}/test_tokenizer"
        ])

    cmd = get_executable("benchmark")
    cmd.extend(parameters)
    ###

    execute_command(args=cmd, cwd=args.model_src_dir, shell=True, file=log_file)


def run_benchmark(parameters: List[str], yaml_path: str, log_file: str, mode: str, input_model: str):
    """Run performance benchmark."""
    # Get cpu information for multi-instance
    total_cores = psutil.cpu_count(logical=False)
    total_sockets = get_number_of_sockets()
    ncores_per_socket = total_cores / total_sockets

    num_sockets = 1  # Use only one socket
    num_benchmark_cores = ncores_per_socket * num_sockets

    batch_size = args.batch_size
    ncores_per_instance = ncores_per_socket
    iters = 100

    if args.multi_instance:
        ncores_per_instance = 4
        iters = 500

    env_vars = {
        "LOGLEVEL": "DEBUG",
    }

    # Update yaml config
    lpot_config = Config()
    lpot_config.load(yaml_path)

    try:
        if lpot_config.evaluation.performance.dataloader:
            lpot_config.evaluation.performance.dataloader.batch_size = batch_size
        lpot_config.evaluation.performance.iteration = iters

        lpot_config.evaluation.performance.configs.cores_per_instance = int(ncores_per_instance)
        lpot_config.evaluation.performance.configs.num_of_instance = int(num_benchmark_cores // ncores_per_instance)
        lpot_config.evaluation.performance.configs.intra_num_of_threads = None
        lpot_config.evaluation.performance.configs.inter_num_of_threads = None
        lpot_config.evaluation.performance.configs.kmp_blocktime = None
        
        print(lpot_config.evaluation.performance.configs.serialize())

        # walk around for anno_path yaml format issue.
        if ( lpot_config.evaluation.accuracy.metric.name == 'COCOmAP' ):
            if (lpot_config.evaluation.accuracy.metric.param == {}):
                lpot_config.evaluation.accuracy.metric = {'COCOmAP': {}}
    except:
        print("[ WARNING ] Could not update performance config.")

    lpot_config.dump(yaml_path)
    print("\nPrint updated yaml... ")
    with open(yaml_path, "r") as yaml_file:
        yaml_context = yaml_file.read()
        print(yaml_context)
    with open(yaml_record_file, 'a') as f:
        f.write("\n\nPerformance yaml... \n")
        f.write(yaml_context)

    # Set execution command
    parameters.append("--mode=performance")

    # Workaround for tensorflow bert_base_mrpc
    if args.framework == "tensorflow" and args.model == "bert_base_mrpc":
        parameters.extend([
            f"--dataset_location={args.dataset_location}",
            f"--init_checkpoint={args.input_model}",
            f"--batch_size={batch_size}"
        ])

    # Workaround for ONNXRT LT models
    if args.framework == "onnxrt" and args.model in ["bert_squad_model_zoo", "mobilebert_squad_mlperf"]:
        onnxrt_lt_mode = "accuracy" if args.mode == "accuracy" else "performance"
        parameters = [
            f"--config={yaml_path}",
            f"--input_model={input_model}",
            f"--mode={onnxrt_lt_mode}",
            f"--data_path={args.dataset_location}"
        ]

    # Workaround for ONNXRT googlenet-12,squeezenet,caffenet,alexnet
    if args.framework == "onnxrt" and args.model in ["googlenet-12", "squeezenet", "caffenet", "alexnet", "zfnet", "inception_v1", "alexnet_qdq", "caffenet_qdq", "googlenet-12_qdq", "zfnet_qdq", "inception_v1_qdq", "squeezenet_qdq"]:
        parameters.extend([
            f"--data_path={args.dataset_location}",
            f"--label_path={args.dataset_location}/../val.txt"
        ])

    if args.framework == "onnxrt" and args.model in [ "fcn_qdq", "fcn"]:
        parameters.extend([
            f"--data_path={args.dataset_location}",
            f"--label_path={args.dataset_location}/../annotations/instances_val2017.json"
        ])

    if args.framework == "onnxrt" and args.model in ["faster_rcnn", "mask_rcnn", "yolov3", "yolov4", "tiny_yolov3"]:
        parameters.extend([
            f"--data_path={args.dataset_location}"
        ])
        
    qdq_model_list = ["bert_squad_model_zoo_qdq", "mobilebert_squad_mlperf_qdq", "mask_rcnn_qdq", "ssd_mobilenet_v1-2_qdq", "faster_rcnn_qdq"]
    if args.framework == "onnxrt" and args.model in qdq_model_list:
        parameters.extend([
            f"--data_path={args.dataset_location}"
        ])

    if args.framework == "onnxrt" and args.model == "duc":
        parameters.extend([
            f"--data_path={args.dataset_location}",
            f"--label_path=/tf_dataset2/datasets/gtFine/val"
        ])

    # Workaround for engine
    if args.framework == "baremetal":
        tokenizer_dir=os.path.dirname(args.input_model)
        parameters.extend([
            f"--dataset_location={args.dataset_location}",
            f"--batch_size={batch_size}",
            f"--tokenizer_dir={tokenizer_dir}/test_tokenizer"
        ])

    cmd = get_executable("benchmark")
    cmd.extend(parameters)

    print(f"Execute command: {cmd}")
    execute_command(
        args=cmd,
        cwd=args.model_src_dir,
        file=log_file,
        shell=True,
        env=env_vars,
    )


def get_benchmark_parameters(yaml_config: str, input_model: str, os: str):
    """Get benchmark parameter for specified os."""
    os_map = {
        "Windows": get_windows_parameters,
        "Linux": get_linux_parameters,
    }
    parameter_parser = os_map.get(os, None)
    if parameter_parser is None:
        raise Exception(f"Could not found parameter parser for {os} OS.")

    return parameter_parser(yaml_config, input_model)


def get_windows_parameters(yaml_path: str, input_model: str):
    """Get benchmark parameters for Windows OS."""
    return [
        f"--input-graph={input_model}",
        f"--config={yaml_path}",
        "--benchmark",
    ]


def get_linux_parameters(yaml_path: str, input_model: str):
    """Get benchmark parameters for Linux OS."""
    return [
        f"--config={yaml_path}",
        f"--input_model={input_model}",
    ]

def get_model_name(framework: str, model: str, model_src_dir: str, precision: str, input_model: str) -> str:
    if precision == "fp32":
        return input_model
    int8_model = os.path.join(
        os.environ["WORKSPACE"],
        f"{framework}-{model}-tune",
    )
    if model_src_dir.endswith("keras"):
        return int8_model
    if framework == "tensorflow":
        return f"{int8_model}.pb"
    elif framework == "onnxrt":
        return f"{int8_model}.onnx"

    return int8_model
    

if __name__ == "__main__":
    main()
