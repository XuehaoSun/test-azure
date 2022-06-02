#!/bin/bash
set -x

PATTERN='[-a-zA-Z0-9_]*='

for i in "$@"
do
    case $i in
        --python_version=*)
            python_version=`echo $i | sed "s/${PATTERN}//"`;;
    esac
done

function main {
    export PATH=${HOME}/miniconda3/bin/:$PATH
    create_conda_env 2.7.0

    cd ${WORKSPACE}/lpot-validation/examples/tensorflow || return
    graph_optimization_fp32 2>&1 | tee ${WORKSPACE}/graph_optimization_fp32.log
    graph_optimization_bf16 2>&1 | tee ${WORKSPACE}/graph_optimization_bf16.log
    graph_optimization_auto-mix 2>&1 | tee ${WORKSPACE}/graph_optimization_auto-mix.log
}

function graph_optimization_fp32 {
    numactl -m 0 -C "0-23" python main.py \
    --input-graph /tf_dataset/pre-trained-models/resnet50/fp32/freezed_resnet50.pb \
    --output-graph ${WORKSPACE}/graph_optimization_fp32.pb \
    --precision 'fp32' \
    --tune
}

function graph_optimization_bf16 {
    numactl -m 0 -C "0-23" python main.py \
    --input-graph /tf_dataset/pre-trained-models/resnet50/fp32/freezed_resnet50.pb \
    --output-graph ${WORKSPACE}/graph_optimization_bf16.pb \
    --precision 'bf16' \
    --tune
}

function graph_optimization_auto-mix {
    echo "Print graph_optimization_auto-mix config..."
    cat config.yaml
    numactl -m 0 -C "0-23" python main.py \
    --input-graph /tf_dataset/pre-trained-models/resnet50/fp32/freezed_resnet50.pb \
    --output-graph ${WORKSPACE}/graph_optimization_bf16.pb \
    --config ./config.yaml \
    --tune
}

function create_conda_env {
    tensorflow_version=$1
    conda_env_name=lpot-py${python_version}-graph_optimization

    conda_dir=$(dirname $(dirname $(which conda)))
    if [ -d ${conda_dir}/envs/${conda_env_name} ]; then
        rm -rf ${conda_dir}/envs/${conda_env_name}
    fi
    n=0
    until [ "$n" -ge 5 ]
    do
        conda create python=${python_version} -y -n ${conda_env_name}
        conda deactivate || source deactivate
        source activate ${conda_env_name} && break
        n=$((n+1))
        sleep 5
    done
    pip install intel-tensorflow==${tensorflow_version}

    if [[ ! $(pip list | grep intel_tensorflow) ]]; then
        py_version=$(echo ${python_version} | sed "s|\.||g")
        install_file=$(ls /home/tensorflow/tf_dataset/tensorflow/ | grep "intel_tensorflow-${tensorflow_version}.*cp${py_version}.*.whl" | head -1)
        [[ -f ${install_file} ]] && pip install ${install_file}
    fi

    pip install protobuf==3.20.1
    pip list

    lpot_install
}

function lpot_install {
    echo "Checking INC..."
    python -V
    pip install ${WORKSPACE}/neural_compressor*.whl
    pip list
}

main
