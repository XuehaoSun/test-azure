#!/bin/bash
set -x

function main {
    init_params "$@"
    set_environment

    if [ "${model}" = "resnet50v1" ] || [ "${model}" = "inceptionv3" ] || [ "${model}" = "mobilenet1.0" ] || [ "${model}" = "mobilenetv2_1.0" ] || [ "${model}" = "resnet18_v1" ] || [ "${model}" = "squeezenet1.0" ]; then
      model_src_dir=${WORKSPACE}/ilit-models/examples/${framework}/cnn
      init_cnn_cmd
    elif [ "${model}" = "SSD-Mobilenet1.0" ] || [ "${model}" = "SSD-ResNet50_v1" ]; then
      model_src_dir=${WORKSPACE}/ilit-models/examples/${framework}/object_detection
      init_obj_cmd
    elif [ "${model}" = "bert-MRPC" ] || [ "${model}" = "bert-QA" ]; then
      model_src_dir=${WORKSPACE}/ilit-models/examples/${framework}/bert
      init_bert_cmd
    fi

    if [ "${model_src_dir}" != "" ];then
        cd ${model_src_dir}
    fi
    git remote -v
    git branch
    git show |head -5

    generate_core
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
    yaml=${model_src_dir}/rn50.yaml
    dataset_dir=/tf_dataset/mxnet
    cmd="python imagenet_inference.py \
        --symbol-file=${dataset_dir}/${model}/${model}-symbol.json\
        --param-file=${dataset_dir}/${model}/${model}-0000.params\
        --rgb-mean=123.68,116.779,103.939 \
        --rgb-std=58.393,57.12,57.375 \
        --batch-size=64 \
        --num-skipped-batches=50 \
        --num-inference-batches=200 \
        --ctx=cpu \
        --dataset=${dataset_dir}/val_256_q90.rec "

    if [ ${model} == 'inceptionv3' ]; then
        cmd="${cmd} --image-shape 3,299,299"
    fi
}

function init_obj_cmd {
    yaml=${model_src_dir}/ssd.yaml
    if [ "${model}" = "SSD-Mobilenet1.0" ]; then
        network="mobilenet1.0"

    elif [ "${model}" = "SSD-ResNet50_v1" ]; then
        network="resnet50_v1"
    fi
    dataset_dir=/tf_dataset/dataset/coco_dataset/raw-data
    cmd="python eval_ssd.py \
      --network=${network} \
      --data-shape=512 \
      --batch-size=18 \
      --dataset coco \
      --data_location ${dataset_dir}"

}

function init_bert_cmd() {
    yaml=${model_src_dir}/bert.yaml
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


# environment
function set_environment {
    export KMP_BLOCKTIME=1
    export KMP_AFFINITY=granularity=fine,verbose,compact,1,0
    export OMP_NUM_THREADS=28

    export PATH=${HOME}/miniconda3/bin/:$PATH
    source activate ${conda_env_name}
    export PYTHONPATH=${PYTHONPATH}:${WORKSPACE}/ilit-models/
    python -V
}

# run
function generate_core {

    # get strategy
    count=$(grep -c 'strategy: ' ${yaml})
    if [ ${count} = 0 ]; then
      strategy='basic'
    else
      strategy=$(grep 'strategy: ' ${yaml} | awk -F 'strategy: ' '{print$2}')
    fi
    echo "Tuning strategy: ${strategy}"

    # run tuning
    run_cmd="numactl -l -C 0-27,56-83 ${cmd} --tune"
    eval "${run_cmd}"

    if [ "${model}" = "resnet50v1" ] || [ "${model}" = "inceptionv3" ] || [ "${model}" = "mobilenet1.0" ] || [ "${model}" = "mobilenetv2_1.0" ] || [ "${model}" = "resnet18_v1" ] || [ "${model}" = "squeezenet1.0" ]; then
      # run benchmark
      run_cmd="numactl -l -C 0-27,56-83 ${cmd} --benchmark"
      eval "${run_cmd}"
    fi

    # run fp32 benchmark
    run_cmd="numactl -l -C 0-27,56-83 ${cmd} --fp32_benchmark"
    eval "${run_cmd}"
}

main "$@"
