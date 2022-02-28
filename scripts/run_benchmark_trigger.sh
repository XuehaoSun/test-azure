#!/bin/bash

set -eo pipefail
set -x
PATTERN='[-a-zA-Z0-9_]*='
output_path=${WORKSPACE}

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
        --yaml=*)
            yaml=`echo $i | sed "s/${PATTERN}//"`;;
        --profiling=*)
            profiling=`echo $i | sed "s/${PATTERN}//"`;;
        --os=*)
            os=`echo $i | sed "s/${PATTERN}//"`;;
        --cpu=*)
            cpu=`echo $i | sed "s/${PATTERN}//"`;;
        --output_path=*)
            output_path=`echo $i | sed "s/${PATTERN}//"`;;
        --multi_instance=*)
            multi_instance=`echo $i | sed "s/${PATTERN}//"`;;
        --log_level=*)
            log_level=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done


if [ ! -d ${output_path} ]; then
    mkdir -p ${output_path}
fi

# Run Benchmark
main() {

    # Import common functions
    source ${WORKSPACE}/lpot-validation/scripts/env_setup.sh --framework=${framework} --model=${model} \
        --conda_env_name=${conda_env_name} --conda_env_mode=${conda_env_mode} --log_level=${log_level}

    echo -e "\nSetting environment..."
    set_environment

    if [ -d ${model_src_dir} ]; then
        cd ${model_src_dir}
        echo -e "\nWorking in $(pwd)..."
        if [[ "${framework}" == "pytorch" ]] && [[ "${model}" == *"3dunet"* ]]; then
            dataset_location=${dataset_location}/preprocessed_data
        fi
    else
        echo "[ERROR] model_src_dir \"${model_src_dir}\" not exists."
        exit 1
    fi

    echo -e "\nSet a modified yaml..."
    echo "${yaml}"
    cp "${yaml}" "benchmark.yaml"
    yaml="benchmark.yaml"
    echo "${yaml}"

    echo -e "\nGetting git information..."
    echo "$(git remote -v)"
    echo "$(git branch)"
    echo "$(git show | head -5)"

    q_model=${WORKSPACE}/${framework}-${model}-tune
    if [ ${framework} == "tensorflow" ]; then
        q_model="${q_model}.pb"
    elif [ ${framework} == "mxnet" ] && [[ ${model_src_dir} == *"object_detection"* ]]; then
        q_model="${q_model}/${model}"
    elif [ ${framework} == "onnxrt" ]; then
        q_model="${q_model}.onnx"
    fi

    # ------ WORKAROUND FOR MXNET RESNET50V1 -----
    topology=${model}
    if [ "${model}" == "resnet50v1" ]; then
        topology="resnet50_v1"
    fi

    if [[ "${model}" == *"_qat" ]]; then
        topology="${model%_qat} "
    fi

    if [[ "${model}" == *"_buildin_qat" ]]; then
        topology="${model%_buildin_qat} "
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

    if [ "${framework}" == "pytorch" ]; then
        if [ "${model}" == "ssd_resnet34_fx" ] || [ "${model}" == "ssd_resnet34_qat_fx" ]; then
            topology="ssd-resnet34"
        fi
    fi
    
    if [[ "${framework}" == "onnxrt" ]] && [[ "${model}" == "gpt2_lm_head_wikitext_model_zoo" ]]; then
        topology="gpt2_lm_wikitext2"
    fi

    if [[ "${framework}" == "pytorch" ]] && [[ "${model}" == "bert_base_MRPC_qat" ]]; then
        topology="bert-base-cased"
    fi

    # pytorch int8 still use fp32 input_model
    if [ ${precision} == "int8" ] && [ ${framework} != "pytorch" ]; then
        input_model=${q_model}
    fi

    # set parameters for benchmark
    parameters="--topology=${topology} --dataset_location=${dataset_location} --input_model=${input_model}"

    # add flag for pytorch int8
    if [ ${framework} == "pytorch" ] && [ ${precision} == "int8" ]; then
        parameters="${parameters} --int8=true"
    fi

    if [ ${framework} == "pytorch" ]; then
        if [ ${model} == "ssd_resnet34_fx" ] || [ ${model} == "bert_base_MRPC_qat" ]; then
            parameters="${parameters} --config=./saved_results"
        fi
    fi

    # remove duplicate install for pytorch resnest
    if [ ${framework} == "pytorch" ] && [ ${model} == "resnest50" ]; then
        sed -i '/python setup.py install/d' run_benchmark.sh
    fi

    echo -e "\nStart run function..."
    case ${mode} in
        accuracy)
            run_accuracy;;
        throughput)
            run_benchmark;;
        latency)
            run_benchmark;;
        *)
          echo "MODE ${mode} not recognized."; exit 1;;
    esac
}

function run_accuracy {
    parameters="${parameters} --mode=accuracy --batch_size=${batch_size}"

    # general yaml for new config format
    iters=-1
    config_new_yaml

    if [ -f "run_benchmark.sh" ]; then
        run_cmd="bash run_benchmark.sh ${parameters}"
    else
        echo "Not found run_benchmark file."
        exit 1
    fi

    logFile=${output_path}/${framework}-${model}-${precision}-${mode}-${os}-${cpu}.log
    echo "ACCURACY BENCHMARK RUNCMD: $run_cmd " >& ${logFile}
    eval "${run_cmd}" >> ${logFile}
    status=$?
    echo "Benchmark process status: ${status}"
    if [ ${status} != 0 ]; then
        echo "Benchmark process returned non-zero exit code."
        exit 1
    fi
}

function run_benchmark {
    # define a low iteration list to save time
    # if latency ~ 500 ms , then set iter = 100. if latency ~ 1000 ms, then set iter = 80
    latency_high_500=("arttrack-coco-multi" "arttrack-mpii-single" "east_resnet_v1_50" \
    "DeepLab" "mask_rcnn_resnet50_atrous_coco" "bert_large_SQuAD" "gpt_WikiText" "albert_base_MRPC" "bart_WNLI" \
    "longformer_MRPC" "ctrl_MRPC" "ssd_resnet34_fx")

    latency_high_1000=("efficientnet-b7_auto_aug" "i3d-flow" "i3d-rgb" "VNet" "icnet-camvid-ava-0001" \
    "icnet-camvid-ava-sparse-30-0001" "icnet-camvid-ava-sparse-60-0001" "dilation" \
    "faster_rcnn_inception_resnet_v2_atrous_coco" "faster_rcnn_nas_coco" "faster_rcnn_nas_lowproposals_coco" \
    "gmcnn-places2" "mask_rcnn_inception_resnet_v2_atrous_coco" "Transformer-LT" "mask_rcnn_resnet101_atrous_coco" \
    "person-vehicle-bike-detection-crossroad-yolov3-1024" "unet-3d-isensee_2017" "unet-3d-origin" "3dunet" \
    "t5_WMT_en_ro" "marianmt_WMT_en_ro" "pegasus_billsum" "dialogpt_wikitext" "transfo_xl_MRPC" "")

    # get cpu information for multi-instance
    lscpu
    ncores_per_socket=${ncores_per_socket:=$( lscpu | grep 'Core(s) per socket' | cut -d: -f2 | xargs echo -n)}
    numactl --hardware
    ncores_per_instance=${ncores_per_socket}
    iters=100

    single_instance=("3dunet" "centernet_hg104")
    if [[ " ${single_instance[@]} " =~ " ${model} " ]]; then
        multi_instance="false"
    fi


    if [ "${multi_instance}" == "true" ]; then
        ncores_per_instance=4
        iters=500
    fi

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

    parameters="${parameters} --mode=benchmark --batch_size=${batch_size} --iters=${iters}"

    # Disable fp32 optimization for oob models on TF1.15UP1
    if [ "${topology}" == "RetinaNet50" ] || [ "${topology}" == "ssd_resnet50_v1_fpn_coco" ]; then
        tensorflow_version=$(python -c "import tensorflow as tf; print(tf.__version__)")
        if [ "${precision}" == "fp32" ] && [ "${tensorflow_version}" == "1.15.0up1" ]; then
            sed -i "/models_need_disable_optimize/a ${topology}" ${model_src_dir}/run_benchmark.sh
        fi
    fi

    # general yaml for new config format
    config_new_yaml

    if [ -f "run_benchmark.sh" ]; then
        run_cmd="bash run_benchmark.sh ${parameters}"
    else
        echo "Not found run_benchmark file."
        exit 1
    fi

    echo "PERFORMANCE BENCHMARK RUNCMD: $run_cmd "
    logFile=${output_path}/${framework}-${model}-${precision}-${mode}-${os}-${cpu}

    if [ "${profiling}" == "true" ]; then
        # enable timeline for oob model
        export RUN_PROFILING=1
    fi

    benchmark_pids=()

    export OMP_NUM_THREADS=${ncores_per_instance}
    if [ "${multi_instance}" == "false" ]; then
        echo "Executing single instance benchmark"
        ${run_cmd} 2>&1|tee ${logFile}.log &
        benchmark_pids+=($!)
    else
        echo "Executing multi instance benchmark"
        for((j=0;$j<${ncores_per_socket};j=$(($j + ${ncores_per_instance}))));
        do
        end_core_num=$((j + ncores_per_instance -1))
        if [ ${end_core_num} -ge ${ncores_per_socket} ]; then
            end_core_num=$((ncores_per_socket-1))
        fi
        numactl -m 0 -C "$j-$end_core_num" \
            ${run_cmd} 2>&1|tee ${logFile}-${ncores_per_socket}-${ncores_per_instance}-${j}.log &
            benchmark_pids+=($!)
        done
    fi

    status="SUCCESS"

    for pid in "${benchmark_pids[@]}"; do
        wait $pid
        exit_code=$?
        echo "Detected exit code: ${exit_code}"
        if [ ${exit_code} == 0 ]; then
            echo "Process ${pid} succeeded"
        else
            echo "Process ${pid} failed"
            status="FAILURE"
        fi
    done

    echo "Benchmark process status: ${status}"
    if [ ${status} == "FAILURE" ]; then
        echo "Benchmark process returned non-zero exit code."
        exit 1
    fi

    if [ "${profiling}" == "true" ] && [ "${precision}" == "fp32" ]; then
        # copy profiling to /tmp
        save_path=/tmp/timeline_json/${framework}-${model}/
        echo "HOSTNAME IS ${HOSTNAME}"
        echo "!!! ${model} timeline save path is ${save_path} !!!"
        rm -rf "${save_path}"
        mkdir -p "${save_path}"
        cp -r "${model_src_dir}/timeline_json/"* "${save_path}"
    fi

}

# update yaml file
function update_yaml_config {
    if [ ! -f ${yaml} ]; then
        echo "Not found yaml config at \"${yaml}\" location."
        exit 1
    fi

    update_yaml_params=" --batch-size ${batch_size} --iteration ${iters} --mode ${mode}"

    if [ "${update_yaml_params}" != "" ]; then
        python ${WORKSPACE}/lpot-validation/scripts/update_yaml_config.py --yaml=${yaml} ${update_yaml_params}
    fi
}

# general yaml for new config format
function config_new_yaml {

    if [ "${framework}" == "tensorflow" ]; then
        if [[ "${model_src_dir}" == *"image_recognition"* ]] || [[ "${model_src_dir}" == *"object_detection"* ]] || [[ "${model_src_dir}" == *"nlp/bert"* ]]; then
            update_yaml_config
            echo -e "\nPrint_updated_yaml... "
            cat ${yaml}
            parameters="--config=${yaml} --input_model=${input_model}"
        fi
    fi

}

main
