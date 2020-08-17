#!/bin/bash

function main {
    init_params "$@"
    
    # Import common functions
    source ${WORKSPACE}/ilit-validation/scripts/common_functions.sh --framework=${framework} --model=${model} --tuning_strategy="" --conda_env_name=${conda_env_name}

    echo -e "\nSetting environment..."
    set_environment
    
    # Get model source dir and model path
    echo -e "\nGetting benchmark variables..."
    get_benchmark_envs 

    if [ -d ${benchmark_dir} ]; then
        cd ${benchmark_dir}
        echo -e "\nWorking in $(pwd)..."
    else
        echo "[ERROR] benchmark_dir \"${benchmark_dir}\" not exists."
        exit 1
    fi
  
    echo -e "\nGetting git information..."
    echo "$(git remote -v)"
    echo "$(git branch)"
    echo "$(git show | head -5)"

    precision_list=(fp32 int8)
    mode_list=(throughput)  # Temporarily removed latency for MR test
    for precision in "${precision_list[@]}"
    do
        for mode in "${mode_list[@]}"
        do
            run_benchmark ${precision} ${mode}
        done
    done
}

# init params
function init_params {
    framework='mxnet'
    model='resnet50v1'

    for var in "$@"
    do 
        case $var in
            --framework=*)
                framework=$(echo $var |cut -f2 -d=)
            ;;
            --model=*)
                model=$(echo $var |cut -f2 -d=)
            ;;
            --conda_env_name=*)
                conda_env_name=$(echo $var |cut -f2 -d=)
            ;;
            *)
                echo "Error: No such parameter: ${var}"
                exit 1
            ;;
        esac
    done
}

# init_run_cmd
function init_cnn_cmd {
    cmd="python imagenet_inference.py \
        --symbol-file=${input_model}-symbol.json\
        --param-file=${input_model}-0000.params\
        --rgb-mean=123.68,116.779,103.939 \
        --rgb-std=58.393,57.12,57.375 \
        --num-skipped-batches=50 \
        --num-inference-batches=200 \
        --ctx=cpu \
        --dataset=${dataset_location}"
    batch_size=64

    if [ ${model} == 'inceptionv3' ]; then
        cmd="${cmd} --image-shape 3,299,299"
    fi
}

function init_obj_cmd {
    if [ "${model}" = "SSD-Mobilenet1.0" ]; then
        network="mobilenet1.0"

    elif [ "${model}" = "SSD-ResNet50_v1" ]; then
        network="resnet50_v1"
    fi
    dataset_dir=/tf_dataset/dataset/coco_dataset/raw-data
    cmd="python eval_ssd.py \
        --network=${network} \
        --data-shape=512 \
        --dataset coco \
        --data_location ${dataset_dir}"

    batch_size=18
}

function init_bert_cmd() {
    param_path=/tf_dataset/mxnet/bert
    if [ "${model}" = "bert-MRPC" ]; then
      cmd="python finetune_classifier.py \
        --task_name MRPC \
        --only_inference \
        --model_parameters ${param_path}/output_dir/model_bert_MRPC_4.params"
    fi

    if [ "${model}" = "bert-QA" ]; then
      cmd="python finetune_squad.py \
        --model_parameters ${param_path}/output_dir/net.params \
        --round_to 128 \
        --test_batch_size 128 \
        --only_predict"
    fi
}


# run
function run_benchmark {
    precision=$1
    mode=$2

    # ------ WORKAROUND FOR MXNET RESNET50V1 -----
    topology=${model}
    if [ "${model}" == "resnet50v1" ]; then
        topology="resnet50_v1"
    fi

    input_model=${model_base_path}/${topology}

    
    if [ $precision == 'int8' ]; then
      input_model=${WORKSPACE}/${framework}-${model}-tune
    fi

    case "${model_type}" in
      cnn) init_cnn_cmd;;
      obj) init_obj_cmd;;
      bert) init_bert_cmd;;
      *) echo "Model ${model} is not supported."; exit 1;;
    esac

    if [ $mode == 'latency' ]; then
      export OMP_NUM_THREADS=4
      pre_cmd="numactl -l -C 0-3,56-59"
      mode_cmd="--batch-size 1"
    else
      export OMP_NUM_THREADS=28
      pre_cmd="numactl -l -C 0-27,56-83"
      mode_cmd="--batch-size ${batch_size}"
    fi

    # run benchmark
    logFile=${WORKSPACE}/${framework}_${model}_${precision}_${mode}_benchmark.log

    run_cmd="${pre_cmd} ${cmd} --fp32_benchmark"
    
    if [ "${model_type}" == "cnn" ] || [ "${model_type}" == "obj" ]; then
        run_cmd="${run_cmd} ${mode_cmd}"
    fi
    echo "RUNCMD: ${run_cmd} " > ${logFile}
    eval "${run_cmd}" >> ${logFile}
}

main "$@"
