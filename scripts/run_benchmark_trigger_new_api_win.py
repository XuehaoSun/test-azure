import argparse
import os
import platform

import psutil
import utils.consts as consts
from utils.utils import (execute_command, get_executable,
                         get_number_of_sockets, insert_line,
                         install_requirements)

parser = argparse.ArgumentParser(allow_abbrev=False)
parser.add_argument("--framework", type=str, required=True)
parser.add_argument("--model", type=str, required=True)
parser.add_argument("--model_src_dir", type=str, required=True)
parser.add_argument("--dataset_location", type=str, required=True)
parser.add_argument("--input_model", type=str, required=True)
parser.add_argument("--precision", type=str, choices=consts.SUPPORTED_DATATYPES, required=True)
parser.add_argument("--mode", type=str, choices=consts.SUPPORTED_MODES, required=True)
parser.add_argument("--batch_size", type=int, required=True)
parser.add_argument("--cpu", type=str, required=True)
parser.add_argument("--main_script", type=str, required=True)
parser.add_argument("--multi_instance", action="store_true")

args = parser.parse_args()

print(args)

operating_system = platform.system()


# Run Benchmark
def main():

    if not os.path.isdir(args.model_src_dir):
        raise Exception(f"[ERROR] model_src_dir \"{args.model_src_dir}\" not exists.")

    excluded_requirements = consts.EXCLUDED_REQUIREMENTS
    if args.framework == "onnxrt":
        excluded_requirements.remove("torch")
    install_requirements(
        requirements_file=os.path.join(args.model_src_dir, "requirements.txt"),
        exclude=excluded_requirements,
    )

    topology = get_topology()
    q_model = get_q_model()
    input_model = os.path.join("C:\\Jenkins\\workspace\\intel-lpot-validation-windows", q_model)
    input_model = input_model.replace("/", "\\")

    print("[VAL INFO] Checking parameters...")
    print("[VAL INFO] Framework: ", args.framework)
    print("[VAL INFO] Model: ", args.model)
    print("[VAL INFO] Topology: ", topology)
    print("[VAL INFO] Input Model: ", input_model)
    print("\nStart run function...")

    if args.mode == "accuracy":
        run_accuracy(input_model=input_model, topology=topology)
    else:
        run_benchmark(input_model=input_model, topology=topology)


def run_accuracy(input_model, topology):

    iters = 500
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
        operating_system,
    )

    cmd = get_executable("benchmark")
    if args.framework == "pytorch" and args.model == "distilbert_base_MRPC":
        cmd = ["python", "-u", "run_glue_tune.py"]
    cmd.extend(parameters)

    system = platform.system().lower()
    log_file = os.path.join(
        "C:\\Jenkins\\workspace\\intel-lpot-validation-windows\\",
        f"{args.framework}-{args.model}-{args.precision}-{args.mode}-{system}-{args.cpu}.log",
    )

    execute_command(
        args=cmd,
        cwd=args.model_src_dir,
        shell=True,
        file=log_file,
    )


def run_benchmark(input_model, topology):
    # define a low iteration list to save time
    # if latency ~ 500 ms , then set iter = 200. if latency ~ 1000 ms, then set iter = 100
    latency_high_500 ,latency_high_1000 = get_latency_high()

    # get cpu information for multi-instance
    total_cores = psutil.cpu_count(logical=False)
    total_sockets = get_number_of_sockets()
    ncores_per_socket = total_cores // total_sockets

    ncores_per_instance = ncores_per_socket
    iters = 100

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
    if args.framework == "tensorflow" and topology in {"RetinaNet50", "ssd_resnet50_v1_fpn_coco"}:
        import tensorflow as tf
        tensorflow_version = tf.VERSION

        if args.precision == "fp32" and tensorflow_version == "1.15.0up1":
            insert_line(
                file_path=os.path.join(args.model_src_dir, get_executable("benchmark")),
                line_pattern="models_need_disable_optimize=(",
                string=topology,
            )

    mode="performance"
    if args.framework == "tensorflow" and "oob_models" in args.model_src_dir:
        parameters = get_oob_models_parameters(topology, args.input_model, iters)
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
    if args.framework == "pytorch":
        cmd = ["python", "-u", "run_glue_tune.py"]
    if args.framework == "tensorflow" and "oob_models" in args.model_src_dir:
        cmd = ["python", "tf_benchmark.py"]
    cmd.extend(parameters)

    system = platform.system().lower()
    log_prefix = os.path.join(
        "C:\\Jenkins\\workspace\\intel-lpot-validation-windows\\",
        f"{args.framework}-{args.model}-{args.precision}-{args.mode}-{system}-{args.cpu}")

    num_sockets = 1  # Use only one socket
    num_instances = (ncores_per_socket * num_sockets) // ncores_per_instance

    # update main_script
    execute_command(
        [
            "python",
            "C:\\Jenkins\\workspace\\intel-lpot-validation-windows\\lpot-validation\\scripts\\update_new_api_config.py",
            f"--main_script={args.main_script}",
            f"--iteration={iters}",
            f"--cores_per_instance={ncores_per_instance}",
            f"--num_of_instance={num_instances}",
        ],
        cwd=args.model_src_dir,
        shell=True,
    )

    print(f"[VAL INFO] Execute command: {cmd}")

    execute_command(args=cmd, cwd=args.model_src_dir, shell=True, file=f"{log_prefix}.log")


def get_benchmark_parameters(framework, q_model, model, topology, iters, batch_size, precision, mode, dataset_location, system):
    os_map = {
        "Windows": get_windows_parameters,
    }
    parameter_parser=os_map.get(system, None)
    if parameter_parser is None:
        raise Exception(f"Could not found parameter parser for {system} OS.")

    return parameter_parser(framework, q_model, model, topology, iters, batch_size, precision, mode, dataset_location)


def get_windows_parameters(framework: str, input_model: str, model: str, topology: str, iters: int, batch_size: int, precision: str, mode: str, dataset_location: str):
    parameters_map = {
        "tensorflow": [
            "--input-graph", f"{input_model}",
            "--dataset_location", f"{dataset_location}",
            "--mode", f"{mode}",
            "--benchmark",
        ],
        "onnxrt": [
            "--model_path", f"{input_model}",
            "--data_path", f"{dataset_location}",
            "--mode", f"{mode}",
            "--benchmark",
        ],
        "pytorch": {
            "resnet18_fx": [
                "--pretrained",
                "--arch", f"{topology}",
                "--batch-size", f"{batch_size}",
                "--tuned_checkpoint", "saved_results",
                "--iter", f"{iters}",
                "--performance",
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
        else:
            parameters.append("--benchmark")
            if model in ["resnet18_fx", "resnet50"]:
                parameters.extend(["--iter", f"{iters}"])
        if model in ["resnet18_fx", "resnet50"]:
            parameters.append(f"{dataset_location}")
    if framework == "onnxrt" and model == "hf_roberta-base_dynamic":
        parameters.append("--model_name_or_path Intel/roberta-base-mrpc")
        parameters.append("--task mrpc")
        parameters.append("--batch_size=1")
    return parameters


def get_oob_models_parameters(topology, input_model, iters):
    """Get benchmark parameters for TF OOB models."""
    models_need_name = {
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
    }

    models_need_disable_optimize = {
        "CRNN",
        "efficientnet-b0",
        "efficientnet-b0_auto_aug",
        "efficientnet-b5",
        "efficientnet-b7_auto_aug",
        "vggvox",
    }

    models_need_nc_graphdef = {
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
    }

    parameters = [
        "--model_path", f"{input_model}",
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


def get_topology():
    topology = args.model
    # ------ WORKAROUND FOR MXNET RESNET50V1 -----
    if args.model == "resnet50v1":
        topology = "resnet50_v1"

    if args.model.endswith("_qat"):
        topology = args.model[:-4]

    if args.model.endswith("_gpu"):
        topology = args.model[:-4]

    if args.model.endswith("_fx"):
        topology = args.model[:-3]

    if args.model.endswith("_qat_fx"):
        topology = args.model[:-7]

    if args.model.endswith("-oob_fx"):
        topology = args.model[:-7]

    if args.framework == "onnxrt" and args.model == "gpt2_lm_head_wikitext_model_zoo":
        topology = "gpt2_lm_wikitext2"

    if args.framework == "pytorch" and args.model == "bert_base_MRPC_qat":
        topology = "bert-base-cased"

    if args.framework == "pytorch" and args.model == "ssd_resnet34_fx":
        topology = "ssd-resnet34"

    if args.framework == "pytorch" and args.model == "ssd_resnet34_qat_fx":
        topology = "ssd-resnet34"

    return topology


def get_q_model():
    q_model = os.path.join(f"{args.framework}-{args.model}-tune")
    if args.framework == "tensorflow" and "keras" not in args.model_src_dir:
        q_model = f"{q_model}.pb"
    if args.framework == "mxnet":
        os.makedirs(q_model)
        q_model = os.path.join(q_model, args.model)
    if args.framework == "onnxrt":
        q_model = f"{q_model}.onnx"
    return q_model


def get_latency_high():
    latency_high_500 = {
        "arttrack-coco-multi",
        "arttrack-mpii-single",
        "DeepLab",
        "east_resnet_v1_50",
        "mask_rcnn_resnet50_atrous_coco",
    }

    latency_high_1000 = {
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
    }

    return latency_high_500, latency_high_1000


if __name__ == "__main__":
    main()
