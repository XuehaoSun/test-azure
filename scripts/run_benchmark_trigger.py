import argparse
import os
import platform
import shutil
from numpy import append

import psutil

import utils.consts as consts
from update_yaml_config import update_yaml_config
from utils.multi_instance import execute_multi_instance
from utils.utils import (execute_command, get_executable,
                         get_number_of_sockets, insert_line,
                         install_requirements, replace_line)

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
parser.add_argument("--new_benchmark", type=str, required=True)
parser.add_argument("--multi_instance", action="store_true")

args = parser.parse_args()

print(args)

operating_system = platform.system()

# Run Benchmark
def main():

    if not os.path.isdir(args.model_src_dir):
        raise Exception(
            f"[ERROR] model_src_dir \"{args.model_src_dir}\" not exists.")

    excluded_requirements = consts.EXCLUDED_REQUIREMENTS
    if args.framework == "onnxrt":
        excluded_requirements.remove("torch")
    install_requirements(requirements_file=os.path.join(args.model_src_dir, "requirements.txt"),
                         exclude=excluded_requirements)

    print("\nCopy yaml for benchmark...")
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
    topology=args.model
    if args.model == "resnet50v1":
        topology = "resnet50_v1"

    if args.model.endswith("_qat"):
        topology = args.model[:-4]

    if args.model.endswith("_gpu"):
        topology = args.model[:-4]

    if args.model.endswith("_fx"):
        topology = args.model[:-3]

    input_model = args.input_model
    # pytorch int8 still use fp32 input_model
    if args.precision == "int8" and args.framework != "pytorch":
        input_model = q_model

    print("\nStart run function...")
    if args.mode == "accuracy":
        run_accuracy(input_model=input_model, topology=topology, yaml_path=yaml_path)
    else:
        run_benchmark(input_model=input_model, topology=topology, yaml_path=yaml_path)


def run_accuracy(input_model, topology, yaml_path):

    if args.new_benchmark == "true":
        update_yaml(yaml_path, args.mode, args.batch_size)

    iters = -1
    parameters = get_benchmark_parameters(
        args.framework,
        input_model,
        args.model,
        topology,
        iters,
        args.batch_size,
        args.precision,
        args.mode,
        args.dataset_location,
        operating_system
    )

    cmd = get_executable("benchmark")
    if args.framework == "pytorch" and args.model == "distilbert_base_MRPC":
        cmd = ["python", "-u", "run_glue_tune.py"]
    cmd.extend(parameters)

    system = platform.system().lower()
    log_file = os.path.join(
        os.environ["WORKSPACE"],
        f"{args.framework}-{args.model}-{args.precision}-{args.mode}-{system}-{args.cpu}.log")

    execute_command(args=cmd,
                    cwd=args.model_src_dir,
                    shell=True,
                    file=log_file)


def run_benchmark(input_model, topology, yaml_path):
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

    ncores_per_instance = ncores_per_socket
    iters = 100

    batch_size = args.batch_size

    if args.multi_instance:
        ncores_per_instance = 4
        iters = 500
    
    # walk around for pytorch yolov3 model, failed in load 194 iteration.
    if args.framework == "pytorch" and args.model == "yolo_v3":
        iters = 150

    # custom iteration
    if args.model in latency_high_500:
        iters = 100
    if args.model in latency_high_1000:
        iters = 80

    # Disable fp32 optimization for oob models on TF1.15UP1
    if args.framework == "tensorflow" and topology in ["RetinaNet50", "ssd_resnet50_v1_fpn_coco"]:
        import tensorflow as tf
        tensorflow_version=tf.VERSION

        if args.precision == "fp32" and tensorflow_version == "1.15.0up1":
            insert_line(file_path=os.path.join(args.model_src_dir, get_executable("benchmark")),
                        line_pattern="models_need_disable_optimize=(",
                        string=topology)

    env_vars = {
        "OMP_NUM_THREADS": str(ncores_per_instance),
        "LOGLEVEL": "DEBUG"
    }

    mode="performance"
    if args.framework == "tensorflow" and "oob_models" in args.model_src_dir:
        parameters = get_oob_models_parameters(topology, args.input_model, yaml_path, iters)
    else:
        parameters = get_benchmark_parameters(
            args.framework,
            input_model,
            args.model,
            topology,
            iters,
            args.batch_size,
            args.precision,
            mode,
            args.dataset_location,
            operating_system,
        )

    cmd = get_executable("benchmark")
    if args.framework == "pytorch" and args.model == "distilbert_base_MRPC":
        cmd = ["python", "-u", "run_glue_tune.py"]
    if args.framework == "tensorflow" and "oob_models" in args.model_src_dir:
        cmd = ["python", "tf_benchmark.py"]
    cmd.extend(parameters)

    system = platform.system().lower()
    log_prefix=os.path.join(
        os.environ["WORKSPACE"],
        f"{args.framework}-{args.model}-{args.precision}-{args.mode}-{system}-{args.cpu}")
    
    num_sockets = 1  # Use only one socket
    num_instances = (ncores_per_socket * num_sockets) // ncores_per_instance
    if args.new_benchmark == "true":
        update_yaml(yaml_path, args.mode, batch_size, iters, ncores_per_instance, num_instances)

    print(f"Execute command: {cmd}")
    if args.multi_instance and args.new_benchmark == "false":
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


def update_yaml(yaml_path, mode, batch_size=None, iters=None, ncores_per_instance=4, num_of_instance=1):
    import yaml
    with open(yaml_path, 'r') as f:
        content = f.read()
        lpot_config = yaml.safe_load(content)
        if mode == "accuracy":
            try:
                if not lpot_config.get('evaluation') or not lpot_config['evaluation'].get('accuracy'):
                    raise AttributeError
                if lpot_config['evaluation'].get('performance'):
                    lpot_config['evaluation'].pop('performance')
                if lpot_config['evaluation']['accuracy'].get('dataloader', None):
                    lpot_config['evaluation']['accuracy']['dataloader']['batch_size'] = batch_size
                if lpot_config['evaluation']['accuracy'].get('configs', None):
                    lpot_config['evaluation']['accuracy'].pop('configs')
            except AttributeError:
                print("[ WARNING ] Could not update accuracy config.")
        else:
            if not lpot_config.get('evaluation') or not lpot_config['evaluation'].get('performance'):
                raise AttributeError
            if lpot_config['evaluation'].get('accuracy'):
                lpot_config['evaluation'].pop('accuracy')
            if lpot_config['evaluation']['performance'].get('dataloader', None):
                lpot_config['evaluation']['performance']['dataloader']['batch_size'] = batch_size
            lpot_config['evaluation']['performance']['iteration'] = iters
            if not lpot_config['evaluation']['performance'].get('configs'):
                raise AttributeError
            lpot_config['evaluation']['performance']['configs']['cores_per_instance'] = int(ncores_per_instance)
            lpot_config['evaluation']['performance']['configs']['num_of_instance'] = int(num_of_instance)
        # dump config
        updated_yaml = yaml.dump(
            data=lpot_config,
            indent=4,
            default_style=None,
            sort_keys=False
        )
    with open(yaml_path, "w") as yaml_config:
        yaml_config.write(updated_yaml)
    print("\nPrint updated yaml... ")
    with open(yaml_path, "r") as yaml_file:
        yaml_context = yaml_file.read()
        print(yaml_context)


def get_benchmark_parameters(framework, q_model, model, topology, iters, batch_size, precision, mode, dataset_location, system):
    os_map = {
        "Windows": get_windows_parameters,
        "Linux": get_linux_parameters,
    }
    parameter_parser=os_map.get(system, None)
    if parameter_parser is None:
        raise Exception(f"Could not found parameter parser for {system} OS.")

    return parameter_parser(framework, q_model, model, topology, iters, batch_size, precision, mode, dataset_location)


def get_windows_parameters(framework: str, input_model: str, model: str, topology: str, iters: int, batch_size: int, precision: str, mode: str, dataset_location: str):
    parameters_map = {
        "tensorflow": [
            "--input-graph", f"{input_model}",
            "--config", "benchmark.yaml",
            "--mode", f"{mode}",
            "--benchmark",
        ],
        "onnxrt": [
            "--model_path", f"{input_model}",
            "--config", "benchmark.yaml",
            "--mode", f"{mode}",
            "--benchmark",
        ],
        "pytorch": {
            "resnet18_fx": [
                "--pretrained",
                "--arch", f"{topology}",
                "--batch-size", f"{batch_size}",
                "--tuned_checkpoint", "saved_results",
                ],
            "resnet50": [
                "--pretrained",
                "--arch", f"{topology}",
                "--batch-size", f"{batch_size}",
                "--tuned_checkpoint", "saved_results",
            ],
            "distilbert_base_MRPC": [
                "--model_name_or_path", "saved_results",
                "--task_name", "MRPC",
                "--do_eval",
                "--max_seq_length", "128",
                "--per_gpu_eval_batch_size", f"{batch_size}",
                "--no_cuda",
                "--output_dir", "output_results",
            ]
        }
    }
    if framework == "pytorch":
        parameters = parameters_map.get(framework, {}).get(model, None)
    else:
        parameters = parameters_map.get(framework, None)
    if parameters is None:
        raise NotImplementedError
    if framework == "pytorch":
        if precision == "int8":
            parameters.append("--int8")
        if mode == "accuracy":
            parameters.append("--accuracy_only")
        else:
            parameters.append("--benchmark")
            if model in ["resnet18_fx", "resnet50"]:
                parameters.extend(["--iter", f"{iters}"])
        if model in ["resnet18_fx", "resnet50"]:
            parameters.append(f"{dataset_location}")
    return parameters


def get_linux_parameters(framework: str, input_model: str, model: str, topology: str, iters: int, batch_size: int, precision: str, mode: str, dataset_location: str):
    parameters=[
        f"--topology={topology}",
        f"--dataset_location={args.dataset_location}",
        f"--input_model={input_model}"
    ]

    # add flag for pytorch int8
    if framework == "pytorch" and args.precision == "int8":
        parameters.append("--int8=true")
    if mode != "accuracy":
        parameters.extend([
            "--mode=benchmark",
            f"--batch_size={batch_size}",
            f"--iters={iters}"
        ])

    return parameters

def get_oob_models_parameters(topology, input_model, yaml_path, iters):
    """Get benchmark parameters for TF OOB models."""
    models_need_name = [
        "CRNN",
        "CapsuleNet",
        "CenterNet",
        "CharCNN",
        "Hierarchical_LSTM",
        "MANN",
        "MiniGo",
        "TextCNN",
        "TextRNN",
        "aipg-vdcnn",
        "arttrack-coco-multi",
        "arttrack-mpii-single",
        "context_rcnn_resnet101_snapshot_serenget",
        "deepspeech",
        "deepvariant_wgs",
        "dense_vnet_abdominal_ct",
        "east_resnet_v1_50",
        "efficientnet-b0",
        "efficientnet-b0_auto_aug",
        "efficientnet-b5",
        "efficientnet-b7_auto_aug",
        "facenet-20180408-102900",
        "handwritten-score-recognition-0003",
        "license-plate-recognition-barrier-0007",
        "optical_character_recognition-text_recognition-tf",
        "pose-ae-multiperson",
        "pose-ae-refinement",
        "resnet_v2_200",
        "show_and_tell",
        "text-recognition-0012",
        "vggvox",
        "wide_deep",
        "yolo-v3-tiny",
        "NeuMF",
        "PRNet",
        "DIEN_Deep-Interest-Evolution-Network",
    ]

    models_need_disable_optimize = [
        "CRNN",
        "efficientnet-b0",
        "efficientnet-b0_auto_aug",
        "efficientnet-b5",
        "efficientnet-b7_auto_aug",
        "vggvox",
    ]

    models_need_nc_graphdef = [
        "pose-ae-multiperson",
        "pose-ae-refinement",
        "centernet_hg104",
        "DETR",
        "Elmo",
        "Time_series_LSTM",
        "Unet",
        "WD",
        "ResNest101",
        "ResNest50",
        "ResNest50-3D",
        "adversarial_text",
        "Attention_OCR",
        "AttRec",
        "GPT2",
        "Parallel_WaveNet",
        "PNASNet-5",
        "VAE-CF",
        "DLRM",
        "Deep_Speech_2",
    ]

    parameters = [
        "--model_path", f"{input_model}",
        "--output_path", f"{output_model}",
        "--yaml", f"{yaml_path}",
        "--num_iter", f"{iters}",
        "--num_warmup", "10",
        "--benchmark",
    ]

    if topology in models_need_name:
        print(f"{topology} need model name!")
        parameters.extend(["--model_name", f"{topology}"])

    if topology in models_need_disable_optimize:
        print(f"{topology} need to disable optimize_for_inference!")
        parameters.append("--disable_optimize")

    if topology in models_need_nc_graphdef:
        print(f"{topology} need neural_compressor graph_def!")
        parameters.append("--use_nc")

    return parameters

if __name__ == "__main__":
    main()
