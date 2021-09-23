#!/bin/bash -x

set -eo pipefail

function main {
    export PATH=${HOME}/miniconda3/bin/:$PATH
    # pip config set global.index-url https://pypi.douban.com/simple/

    create_conda_env
    lpot_install

    # Run TensorFlow Pruning test
    cd ${WORKSPACE}/lpot-models/examples/tensorflow/pruning
    pip install intel-tensorflow==2.5.0
    pip list
    python main.py   2>&1 | tee ${WORKSPACE}/tensorflow_prune.log

}

function create_conda_env {

    if [[ -z ${python_version} ]]; then
        python_version=3.6  # Set python 3.6 as default
    fi

    conda_env_name=tensorflow_prune-py${python_version}

    if [ $(conda info -e | grep ${conda_env_name} | wc -l) == 0 ]; then
        conda create python=${python_version} -y -n ${conda_env_name}
    fi
    # make sure no more conda nested
    conda deactivate || source deactivate
    conda deactivate || source deactivate
    source activate ${conda_env_name}

    pip list

    if [ ! -d ${WORKSPACE}/lpot-models ]; then
        echo "\"lpot-model\" not found. Exiting..."
        exit 1
    fi
    cd ${WORKSPACE}/lpot-models || return
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
