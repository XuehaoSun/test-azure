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
    framework='pytorch'
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
    dataset=/tf_dataset/pytorch/ImageNet/raw
    if [ "${model}" = "resnet18" ] || [ "${model}" = "resnet50" ] || [ "${model}" = "resnet101" ];then
        cmd=" python main.py \
            -a ${model} \
            --pretrained \
            --data ${dataset}"
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
    run_cmd="${cmd} -t"
    printf "${run_cmd}" |tee -a ${excute_cmd_file}
    timeout 3600 bash ${excute_cmd_file}

    # run fp32 benchmark
    run_cmd="${cmd} --fp32_benchmark"
    eval "${run_cmd}"

}

main "$@"
