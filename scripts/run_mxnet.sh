#!/bin/bash
set -x

function main {
    init_params "$@"
    init_run_cmd
    set_environment
    model_src_dir=${WORKSPACE}/ilit-models/examples/${framework}/resnet50
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
function init_run_cmd {
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

# environment
function set_environment {
    # export KMP_BLOCKTIME=1
    # export KMP_AFFINITY=granularity=fine,verbose,compact,1,0
    # gcc -v
    # conda3 python3
    export PATH=${HOME}/miniconda3/bin/:$PATH
    source activate ${conda_env_name}
    export PYTHONPATH=${PYTHONPATH}:${WORKSPACE}/ilit-models/
    python -V
}

# run
function generate_core {

    # run tunning
    excute_cmd_file="/tmp/${framework}-${model}-run-$(date +'%s').sh"
    rm -f ${excute_cmd_file}
    run_cmd="${cmd} --tune"
    printf "${run_cmd}" |tee -a ${excute_cmd_file}
    timeout 1800 bash ${excute_cmd_file}

    # run benchmark
    run_cmd="${cmd} --benchmark"
    eval "${run_cmd}"

    # run fp32 benchmark
    run_cmd="${cmd} --fp32_benchmark"
    source "${run_cmd}"
}

main "$@"
