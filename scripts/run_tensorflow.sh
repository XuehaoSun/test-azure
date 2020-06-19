#!/bin/bash
set -x

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
    framework='tensorflow'
    model='resnet50'

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

    if [ "${model}" = "resnet50" ];then
        cmd="python main.py \
            --input_graph /tf_dataset/pre-trained-models/resnet50/fp32/freezed_resnet50.pb \
            --inputs input \
            --outputs predict \
            --data_location /tf_dataset/dataset/imagenet"

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

    excute_cmd_file="/tmp/${framework}-${model}-run-$(date +'%s').sh"
    rm -f ${excute_cmd_file}

    printf "${cmd}" |tee -a ${excute_cmd_file}

    sleep 1
    source ${excute_cmd_file}

}

main "$@"
