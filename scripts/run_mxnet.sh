#!/bin/bash

function main {
    init_params "$@"
    init_run_cmd
    set_environment
    model_src_dir=${WORKSPACE}/ilit-models/examples/${framework}/${model}/
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
    model='resnet50v1_5'
    mode='inference'
    precision='fp32'
    batch_size=128

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
function init_run_cmd{

    if [ "${model}" = "resnet50" ];then
        in_graph=/home/tensorflow/jenkins/mxnet/resnet50v1_5/model
        dataset=/data/dataset/val.rec
        cmd=" python offical_rn50.py \
              --symbol-file=${in_graph}/resnet50_v1b-symbol.json \
              --param-file=${in_graph}/resnet50_v1b-0000.params\
              --rgb-mean=123.68,116.779,103.939 \
              --rgb-std=58.393,57.12,57.375 \
              --batch-size=64 \
              --num-skipped-batches=50 \
              --num-inference-batches=200 \
              --ctx=cpu \
              --dataset=${data_location} "
    fi

}

# environment
function set_environment {
    # export KMP_BLOCKTIME=1
    export KMP_AFFINITY=granularity=fine,verbose,compact,1,0
    gcc -v
    # conda3 python3
    export PATH="${HOME}/tools/anaconda3/bin:$PATH"
    source activate ${conda_env_name}
    python -V
}

# run
function generate_core {

    excute_cmd_file="/tmp/${framework}-${model}-run-$(date +'%s').sh"
    rm -f ${excute_cmd_file}

    real_cores_per_instance=$(echo ${cpu_array[i]} |awk -F, '{print NF}')

    printf "${cmd}" |tee -a ${excute_cmd_file}

    sleep 1
    source ${excute_cmd_file}
}

main "$@"
