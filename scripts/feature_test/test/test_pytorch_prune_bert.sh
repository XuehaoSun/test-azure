#!/bin/bash -x

set -eo pipefail

function main {
    export PATH=${HOME}/miniconda3/bin/:$PATH
    # pip config set global.index-url https://pypi.douban.com/simple/

    create_conda_env
    lpot_install

    # Run Pytorch Prune test
    cd ${WORKSPACE}/lpot-models/examples/pytorch/eager/language_translation/prune
    n=0
    until [ "$n" -ge 5 ]
    do
        python -m pip install -r requirements.txt && break
        n=$((n+1))
        sleep 5
    done
    pip list
    bash run_pruning.sh --topology=distilbert_SST-2 --data_dir=/tf_dataset/pytorch/glue_data --output_model=./model_prune --config=./conf.yaml   2>&1 | tee ${WORKSPACE}/pytorch_prune_bert.log

}

function create_conda_env {

    if [[ -z ${python_version} ]]; then
        python_version=3.7  # Set python 3.7 as default
    fi

    conda_env_name=pytorch_prune_bert-py${python_version}

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
    c_lpot=$(pip list | grep -c 'neural_compressor') || true  # Prevent from exiting when 'lpot' not found
    if [ ${c_lpot} != 0 ]; then
        pip uninstall neural_compressor -y
        pip list
    fi
    pip install ${WORKSPACE}/neural_compressor*.whl
    pip list
}

main
