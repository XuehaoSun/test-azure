import argparse
import datetime
import os
import platform
import subprocess
import sys

import psutil
import utils.consts as consts
from utils.utils import (copy_files, execute_command, get_executable, get_size,
                         install_requirements)

parser = argparse.ArgumentParser(allow_abbrev=False)
parser.add_argument("--framework", type=str, required=True)
parser.add_argument("--model", type=str, required=True)
parser.add_argument("--model_src_dir", type=str, required=True)
parser.add_argument("--dataset_location", type=str, required=True)
parser.add_argument("--input_model", type=str, required=True)
parser.add_argument("--strategy", type=str, required=True)
parser.add_argument("--max_trials", type=int, required=True)
parser.add_argument("--cpu", type=str, required=True)
parser.add_argument("--strategy_token", type=str, required=False)
parser.add_argument("--main_script", type=str, required=False)

args = parser.parse_args()

print(args)

dataset_location = os.path.normpath(args.dataset_location).replace("\\", "\\\\")


def main():

    operating_system = platform.system()

    # Temporary change for helloworld_keras
    if args.model == "helloworld_keras":
        args.model_src_dir=os.path.join("C:\Jenkins\workspace\intel-lpot-validation-windows", "lpot-models", "examples", "helloworld")

    if not os.path.isdir(args.model_src_dir):
        raise Exception(f"[ERROR] model_src_dir \"{args.model_src_dir}\" not exists.")

    excluded_requirements = consts.EXCLUDED_REQUIREMENTS
    if args.framework == "onnxrt":
        excluded_requirements.remove("torch")
    install_requirements(requirements_file=os.path.join(args.model_src_dir, "requirements.txt"),
                         exclude=excluded_requirements)

    # Temporary change for helloworld_keras
    if args.model == "helloworld_keras":
        subprocess.run(args=["python", "train.py"], cwd=args.model_src_dir, check=True)
        subprocess.run(
            args=["python", "test.py"],
            cwd=os.path.join(args.model_src_dir, "tf2.x"),
            check=True,
        )
        sys.exit()

    topology = get_topology()

    print("[VAL INFO] Checking topology...")
    print("[VAL INFO] Framework: ", args.framework)
    print("[VAL INFO] Model: ", args.model)
    print("[VAL INFO] Topology: ", topology)

    q_model=os.path.join("C:\Jenkins\workspace\intel-lpot-validation-windows", f"{args.framework}-{args.model}-tune")
    if args.framework == "tensorflow" and "keras" not in args.model_src_dir:
        q_model = f"{q_model}.pb"
    if args.framework == "mxnet":
        os.makedirs(q_model)
        q_model = os.path.join(q_model, args.model)
    if args.framework == "onnxrt":
        q_model = f"{q_model}.onnx"
    if args.framework == "tensorflow" and "oob_models" in args.model_src_dir:
        pass
        parameters = get_oob_models_parameters(topology, args.input_model, q_model)
    else:
        parameters = get_tuning_parameters(args.framework, args.model, topology, q_model, operating_system)

    print("\nRun_tuning parameters... ")
    print(parameters)
    total_memory = psutil.virtual_memory().total

    cmd = get_executable("tuning")
    if args.framework == "pytorch" and args.model == "distilbert_base_MRPC":
        cmd = ["python", "-u", "run_glue.py"]
    if args.framework == "onnxrt" and args.model == "bert_base_MRPC":
        cmd = ["python", "bert_base.py"]
    if args.framework == "tensorflow" and "oob_models" in args.model_src_dir:
        cmd = ["python", "tf_benchmark.py"]
    cmd.extend(parameters)
    cmd = [str(i) for i in cmd]
    print("Executing command: '" + " ".join(cmd) + "' in '" + args.model_src_dir + "' directory.")

    start_time = datetime.datetime.now()

    system = platform.system().lower()
    log_file = os.path.join("C:\Jenkins\workspace\intel-lpot-validation-windows",
                            f"{args.framework}-{args.model}-{system}-{args.cpu}-tune.log")

    execute_command(
        args=cmd,
        cwd=args.model_src_dir,
        shell=True,
        file=log_file,
    )

    stop_time = datetime.datetime.now()
    duration = (stop_time-start_time).total_seconds()

    fp32_model_size, int8_model_size = collect_model_size(args.input_model, q_model)

    tuning_info = [
        f"Tuning strategy: {args.strategy}",
        f"Tuning time spend: {int(duration)}s",
        f"The input model size is: {fp32_model_size}",
        f"The output model size is: {int8_model_size}",
        f"Total resident size (kbytes): {total_memory // 1024}",
    ]

    with open(log_file, "a") as log:
        for info in tuning_info:
            print(info)
            log.write(info + "\n")

    # copy tuning result to tmp dir
    tmp_dir = "/tmp"
    if operating_system == "Windows":
        tmp_dir = os.environ["Temp"]

    save_path = os.path.join(
        tmp_dir, f"{args.framework}-{args.model}-tune-{int(datetime.datetime.now().timestamp())}")

    hostname = platform.node()
    print(f"HOSTNAME IS {hostname}")
    print(f"!!!tune model save path is {save_path} !!!")
    os.makedirs(save_path)
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
        fp32_model_size = get_size(fp32_model, add_unit=True)
        int8_model_size = get_size(os.path.dirname(int8_model), add_unit=True)
    elif args.framework == "pytorch":
        fp32_model_size = "None"
        int8_model_size = "None"
    elif args.framework == "onnxrt":
        fp32_model_size = get_size(fp32_model, add_unit=True)
        int8_model_size = get_size(int8_model, add_unit=True)
    else:
        fp32_model_size = "0M"
        int8_model_size = "0M"

    return fp32_model_size, int8_model_size


def get_tuning_parameters(framework: str, model: str, topology: str, q_model: str, os: str):
    os_map = {"Windows": get_windows_parameters}
    parameter_parser = os_map.get(os, None)
    if parameter_parser is None:
        raise Exception(f"Could not found parameter parser for {os} OS.")

    return parameter_parser(framework, model, topology, q_model)


def get_windows_parameters(framework: str, model: str, topology: str, q_model: str):
    parameters_map = {
        "tensorflow": {
            "resnet101_keras": [
                "--input_model", f"{args.input_model}",
                "--output_model", f"{q_model}",
                "--eval_data", f"{args.dataset_location}",
                "--calib_data", f"{args.dataset_location}",
                "--tune"
            ],
            "ssd_resnet50_v1": [
                "--input-graph", f"{args.input_model}",
                "--output_model", f"{q_model}",
                "--dataset_location", f"{args.dataset_location}",
                "--tune"
            ],
            "resnet50v1.0": [
                "--input-graph", f"{args.input_model}",
                "--output-graph", f"{q_model}",
                "--dataset_location", f"{args.dataset_location}",
                "--tune",
                "--batch_size", 1,
            ],
            "inception_v3": [
                "--input-graph", f"{args.input_model}",
                "--output-graph", f"{q_model}",
                "--dataset_location", f"{args.dataset_location}",
                "--tune",
            ],
            "mobilenetv1":[
                "--input-graph", f"{args.input_model}",
                "--output-graph", f"{q_model}",
                "--dataset_location", f"{args.dataset_location}",
                "--tune",
            ],
        },
        "onnxrt": {
            "duc": [
                "--model_path", f"{args.input_model}",
                "--output_model", f"{q_model}",
                "--data_path", f"{args.dataset_location}",
                "--tune"
            ],
            "hf_roberta-base_dynamic": [
                "--model_name_or_path", "Intel/roberta-base-mrpc",
                "--model_path", f"{args.input_model}",
                "--output_model", f"{q_model}",
                "--data_path", f"{args.dataset_location}",
                "--hidden_size", 768,
                "--task", "mrpc",
                "--tune"
            ],
            "bert_base_MRPC_static":[
                "--model_path", f"{args.input_model}",
                "--output_model", f"{q_model}",
                "--model_name_or_path", "bert-base-uncased",
                "--data_path", f"{args.dataset_location}",
                "--task", "mrpc",
                "--batch_size", 8,
                "--model_type", "bert",
                "--quant_format", "QOperator",
                "--tune"
            ],
            "resnet50_v1_5":[
                "--model_path", f"{args.input_model}",
                "--dataset_location ", f"{args.dataset_location}",
                "--label_path", f"{args.dataset_location}/../val.txt",
                "--output_model", f"{q_model}",
                "--tune"
            ],
        },
        "pytorch": {
            "resnet18_fx": [
                "--pretrained",
                "--arch", f"{topology}",
                "--batch-size", "30",
                "--tune",
                "--tuned_checkpoint", f"saved_results",
                f"{dataset_location}"
            ],
            "resnet18_ipex": [
                "--pretrained",
                "--arch", f"{topology}",
                "--batch-size", "30",
                "--ipex",
                "--tuned_checkpoint", f"saved_results",
                f"{dataset_location}"
            ],
            "efficientnet_b0_fx": [
                "--pretrained",
                "--arch", f"{topology}",
                "--batch-size", "30",
                "--tune",
                "--tuned_checkpoint", f"saved_results",
                f"{dataset_location}"
            ],
            "distilbert_base_MRPC": [
                "--model_name_or_path", f"{args.input_model}",
                "--task_name", "mrpc",
                "--do_eval",
                "--do_train",
                "--max_seq_length", 128,
                "--per_device_eval_batch_size", 16,
                "--no_cuda",
                "--output_dir", f"saved_results",
                "--tune",
                "--overwrite_output_dir",
            ],
            "resnet50": [
                "--pretrained",
                "--arch", f"{topology}",
                "--batch-size", "30",
                "--tune",
                "--tuned_checkpoint", f"saved_results",
                f"{dataset_location}"
            ],
        }
    }
    parameters = parameters_map.get(framework, {}).get(model, None)
    if parameters is None:
        raise NotImplementedError
    return parameters


def get_oob_models_parameters(topology, input_model, output_model, yaml_path):
    """Get tuning parameters for TF OOB models."""
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

    models_need_bs16 = {
        "icnet-camvid-ava-0001",
        "icnet-camvid-ava-sparse-30-0001",
        "icnet-camvid-ava-sparse-60-0001",
    }

    models_need_bs32 = {
        "adv_inception_v3",
        "ens3_adv_inception_v3",
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
        "--output_path", f"{output_model}",
        "--yaml", f"{yaml_path}",
        "--tune",
        "--num_warmup", "10",
        "-n", "500",
    ]

    if topology in models_need_name:
        print(f"{topology} need model name!")
        parameters.extend(["--model_name", f"{topology}"])

    if topology in models_need_disable_optimize:
        print(f"{topology} need to disable optimize_for_inference!")
        parameters.append("--disable_optimize")

    if topology in models_need_bs16:
        print(f"{topology} need to set bs = 16!")
        parameters.extend(["-b", "16"])

    if topology in models_need_bs32:
        print(f"{topology} need to set bs = 32!")
        parameters.extend(["-b", "32"])

    if topology in models_need_nc_graphdef:
        print(f"{topology} need neural_compressor graph_def!")
        parameters.append("--use_nc")

    return parameters


def get_topology():
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

    return topology

if __name__ == "__main__":
    main()
