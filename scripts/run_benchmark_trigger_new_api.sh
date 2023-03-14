#!/bin/bash

set -eo pipefail
PATTERN='[-a-zA-Z0-9_]*='

for i in "$@"
do
    case $i in
        --framework=*)
            framework=`echo $i | sed "s/${PATTERN}//"`;;
        --model=*)
            model=`echo $i | sed "s/${PATTERN}//"`;;
        --model_src_dir=*)
            model_src_dir=`echo $i | sed "s/${PATTERN}//"`;;
        --dataset_location=*)
            dataset_location=`echo $i | sed "s/${PATTERN}//"`;;
        --input_model=*)
            input_model=`echo $i | sed "s/${PATTERN}//"`;;
        --precision=*)
            precision=`echo $i | sed "s/${PATTERN}//"`;;
        --mode=*)
            mode=`echo $i | sed "s/${PATTERN}//"`;;
        --batch_size=*)
            batch_size=`echo $i | sed "s/${PATTERN}//"`;;
        --conda_env_name=*)
            conda_env_name=`echo $i | sed "s/${PATTERN}//"`;;
        --conda_env_mode=*)
            conda_env_mode=`echo $i | sed "s/${PATTERN}//"`;;
        --multi_instance=*)
            multi_instance=`echo $i | sed "s/${PATTERN}//"`;;
        --log_level=*)
            log_level=`echo $i | sed "s/${PATTERN}//"`;;
        --itex_mode=*)
             itex_mode=`echo $i | sed "s/${PATTERN}//"`;;
        --main_script=*)
             main_script=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

# Run Benchmark
main() {
    echo -e "\n[VAL INFO] Run INC new API benchmark..."
    echo -e "\n[VAL INFO] Setting environment..."
    source ${WORKSPACE}/lpot-validation/scripts/env_setup.sh --framework=${framework} --model=${model} \
        --conda_env_name=${conda_env_name} --conda_env_mode=${conda_env_mode} --log_level=${log_level} \
        --itex_mode=${itex_mode}
    set_environment

    # set gcc for ace machine
    if [ -f /opt/rh/devtoolset-9/enable ]; then
        source /opt/rh/devtoolset-9/enable
    fi

    if [ -d ${model_src_dir} ]; then
        cd ${model_src_dir}
        echo -e "\n[VAL INFO] Working in $(pwd)..."
    else
        echo "[ERROR] model_src_dir \"${model_src_dir}\" not exists."
        exit 1
    fi

    echo -e "\n[VAL INFO] Getting git information..."
    echo "$(git remote -v)"
    echo "$(git branch)"
    echo "$(git show | head -5)"

    echo -e "\n[VAL INFO] Getting topology..."
    get_topology

    echo -e "\n[VAL INFO] Getting input model..."
    origin_model=${input_model}
    get_input_model

    echo -e "\n[VAL INFO] Setting run benchmark parameters..."
    if [ "$mode" == "throughput" ] || [ "$mode" == "latency" ]; then
        mode="performance"
    fi
    parameters="--input_model=${input_model} --dataset_location=${dataset_location} --mode=${mode} --batch_size=${batch_size}"
    if [ ${framework} == "pytorch" ]; then
        parameters="${parameters} --topology=${topology}"
        if [ ${precision} == "int8" ]; then
            parameters="${parameters} --int8=true"
        fi
    fi
    if [ ${framework} == "tensorflow" ] && [ ${model} == "bert_base_mrpc" ]; then
        parameters="${parameters} --init_checkpoint=${origin_model}"
    fi

    echo -e "\n[VAL INFO] Start run function..."
    case ${mode} in
        accuracy)
            run_accuracy;;
        performance)
            run_benchmark;;
        *)
          echo "MODE ${mode} not recognized."; exit 1;;
    esac
}

function run_accuracy {
    echo -e "\n[VAL INFO] Run accuracy cmd..."
    echo "bash run_benchmark.sh ${parameters}"
    echo -e "\n[VAL INFO] Running accuracy..."
    bash run_benchmark.sh ${parameters}
}

function run_benchmark {
    # define a low iteration list to save time
    # if latency ~ 500 ms , then set iter = 100. if latency ~ 1000 ms, then set iter = 50
    latency_high_500=("ssd_resnet50_v1_fpn_coco" \
    "faster_rcnn_resnet101_ava_v2_1" "faster_rcnn_resnet101_coco" "SSD_ResNet50_V1_FPN_640x640_RetinaNet50" \
    "faster_rcnn_resnet101_kitti" "arttrack-coco-multi" "arttrack-mpii-single" "east_resnet_v1_50" \
    "mask_rcnn_resnet50_atrous_coco" "bert_large_SQuAD" "gpt_WikiText" "albert_base_MRPC" "bart_WNLI" \
    "longformer_MRPC" "ctrl_MRPC" "ssd_resnet34_fx")

    latency_high_1000=("cpm-person" "DeepLab" "efficientnet-b7_auto_aug" "i3d-flow" "i3d-rgb" "VNet" "icnet-camvid-ava-0001" \
    "icnet-camvid-ava-sparse-30-0001" "icnet-camvid-ava-sparse-60-0001" "dilation" \
    "faster_rcnn_inception_resnet_v2_atrous_coco" "faster_rcnn_nas_coco" "faster_rcnn_nas_lowproposals_coco" \
    "gmcnn-places2" "mask_rcnn_inception_resnet_v2_atrous_coco" "Transformer-LT" "mask_rcnn_resnet101_atrous_coco" \
    "person-vehicle-bike-detection-crossroad-yolov3-1024" "faster_rcnn_nas_coco_2018_01_28" "unet-3d-isensee_2017" "unet-3d-origin" "3dunet" \
    "t5_WMT_en_ro" "marianmt_WMT_en_ro" "pegasus_billsum" "dialogpt_wikitext" "transfo_xl_MRPC" "dlrm_fx")

    # get cpu information for multi-instance
    ncores_per_socket=${ncores_per_socket:=$( lscpu | grep 'Core(s) per socket' | cut -d: -f2 | xargs echo -n)}
    numactl --hardware
    ncores_per_instance=${ncores_per_socket}
    iters=500

    single_instance=("3dunet" "centernet_hg104" "GPT2" "dlrm" "dlrm_fx" "dlrm_ipex" "gpt_j_wikitext")
    if [[ " ${single_instance[@]} " =~ " ${model} " ]]; then
        multi_instance="false"
    fi

    if [ "${multi_instance}" == "true" ]; then
        ncores_per_instance=4
    fi
    num_of_instance=$((ncores_per_socket/ncores_per_instance))

    # walk around for pytorch yolov3 model, failed in load 194 iteration.
    if [ "${model}" == "yolo_v3" ] && [ "${framework}" == "pytorch" ]; then
        iters=150
    fi
    # custom iteration
    if [[ "${latency_high_500[@]}" =~ "${model}" ]]; then
        iters=100
    elif [[ "${latency_high_1000[@]}" =~ "${model}" ]]; then
        iters=50
    fi

    echo -e "\n[VAL INFO] Update benchmark config in main script..."
    export NUM_OF_INSTANCE=${num_of_instance}
    export CORES_PER_INSTANCE=${ncores_per_instance}
    python ${WORKSPACE}/lpot-validation/scripts/update_new_api_config.py --main_script=${main_script} --iteration=${iters} --cores_per_instance=${ncores_per_instance} --num_of_instance=${num_of_instance}

    echo -e "\n[VAL INFO] Pass parameter iters for b_func models benchmark..."
    parameters="${parameters} --iters=${iters}"

    echo -e "\n[VAL INFO] Run benchmark cmd..."
    echo "bash run_benchmark.sh ${parameters}"
    echo -e "\n[VAL INFO] Running benchmark..."
    bash run_benchmark.sh ${parameters}
}

function get_topology {
    topology=${model}
    if [[ "${model}" == *"_qat" ]]; then
        topology="${model%_qat} "
    fi
    if [[ "${model}" == *"_gpu" ]]; then
        topology="${model%_gpu}"
    fi
    if [[ "${model}" == *"_fx" ]]; then
        topology="${model%_fx}"
    fi
    if [[ "${model}" == *"_qat_fx" ]]; then
        topology="${model%_qat_fx}"
    fi
    if [[ "${model}" == *"-oob_fx" ]]; then
        topology="${model%-oob_fx}"
    fi
    if [[ "${framework}" == "onnxrt" ]] && [[ "${model}" == "gpt2_lm_head_wikitext_model_zoo" ]]; then
        topology="gpt2_lm_wikitext2"
    fi
    if [[ "${framework}" == "pytorch" ]] && [[ "${model}" == "bert_base_MRPC_qat" ]]; then
        topology="bert-base-cased"
    fi
    if [ "${framework}" == "pytorch" ]; then
        if [ "${model}" == "ssd_resnet34_fx" ] || [ "${model}" == "ssd_resnet34_qat_fx" ]; then
            topology="ssd-resnet34"
        fi
    fi

    echo "[VAL INFO] Checking topology..."
    echo "[VAL INFO] Framework: ${framework}"
    echo "[VAL INFO] Model: ${model}"
    echo "[VAL INFO] Topology: ${topology}"
}

function get_input_model {
    q_model=${WORKSPACE}/${framework}-${model}-tune
    if [ ${framework} == "tensorflow" ] && [[ ${model_src_dir} != *"keras"* ]] && [[ ${model} != *"keras"* ]];  then
        q_model="${q_model}.pb"
    elif [ ${framework} == "mxnet" ] && [[ ${model_src_dir} == *"object_detection"* ]]; then
        q_model="${q_model}/${model}"
    elif [ ${framework} == "onnxrt" ]; then
        q_model="${q_model}.onnx"
    fi

    # pytorch int8 still use fp32 input_model
    if [ ${precision} == "int8" ] && [ ${framework} != "pytorch" ]; then
        input_model=${q_model}
    fi
}

main