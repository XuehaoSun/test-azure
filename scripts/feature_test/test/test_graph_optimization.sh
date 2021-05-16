#!/bin/bash
set -x

function main {
    export PATH=${HOME}/miniconda3/bin/:$PATH
    create_conda_env 2.4.0

    cd ${WORKSPACE}/lpot-validation/examples/tensorflow || return
    graph_optimization_fp32 2>&1 | tee ${WORKSPACE}/graph_optimization_fp32.log
    graph_optimization_bf16 2>&1 | tee ${WORKSPACE}/graph_optimization_bf16.log
    graph_optimization_auto-mix 2>&1 | tee ${WORKSPACE}/graph_optimization_auto-mix.log
}

function graph_optimization_fp32 {
    python main.py \
    --input-graph /tf_dataset/pre-trained-models/resnet50/fp32/freezed_resnet50.pb \
    --output-graph ${WORKSPACE}/graph_optimization_fp32.pb \
    --precision 'fp32' \
    --tune
}

function graph_optimization_bf16 {
    python main.py \
    --input-graph /tf_dataset/pre-trained-models/resnet50/fp32/freezed_resnet50.pb \
    --output-graph ${WORKSPACE}/graph_optimization_bf16.pb \
    --precision 'bf16' \
    --tune
}

function graph_optimization_auto-mix {
    echo "Print graph_optimization_auto-mix config..."
    cat config.yaml
    python main.py \
    --input-graph /tf_dataset/pre-trained-models/resnet50/fp32/freezed_resnet50.pb \
    --output-graph ${WORKSPACE}/graph_optimization_bf16.pb \
    --config ./config.yaml \
    --tune
}

function create_conda_env {
    tensorflow_version=$1
    python_version=3.6
    conda_env_name=lpot-py${python_version}-graph_optimization

    if [ $(conda info -e | grep ${conda_env_name} | wc -l) == 0 ]; then
        conda create python=${python_version} -y -n ${conda_env_name}
    fi
    # make sure no more conda nested
    conda deactivate || source deactivate
    conda deactivate || source deactivate
    source activate ${conda_env_name}
    pip install intel-tensorflow==${tensorflow_version}
    pip list

    lpot_install
    pip list
}

function lpot_install {
    echo "Checking lpot..."
    python -V
    c_lpot=$(pip list | grep -c 'lpot') || true  # Prevent from exiting when 'lpot' not found
    if [ ${c_lpot} != 0 ]; then
        pip uninstall lpot -y
        pip list
    fi
    pip install ${WORKSPACE}/lpot*.whl
    pip list
}

main
