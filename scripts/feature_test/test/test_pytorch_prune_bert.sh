#!/bin/bash -x
set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='
for i in "$@"
do
    case $i in
        --python_version=*)
            python_version=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

function main {
    export PATH=${HOME}/miniconda3/bin/:$PATH
    # pip config set global.index-url https://pypi.douban.com/simple/

    create_conda_env
    lpot_install

    # old api example repo
    cd ${WORKSPACE}
    if [ ! -d "${WORKSPACE}/lpot-models/examples/pytorch/nlp/huggingface_models/text-classification/pruning/magnitude/eager" ]; then
        git clone -b old_api_examples ${lpot_url} old-lpot-models
        cd old-lpot-models
        git branch 
        mkdir -p ${WORKSPACE}/lpot-models/examples/pytorch/nlp/huggingface_models/text-classification/pruning/magnitude/eager
        cp -r ${WORKSPACE}/old-lpot-models/examples/pytorch/nlp/huggingface_models/text-classification/pruning/magnitude/eager/. ${WORKSPACE}/lpot-models/examples/pytorch/nlp/huggingface_models/text-classification/pruning/magnitude/eager
    fi

    # Run Pytorch Prune test
    cd ${WORKSPACE}/lpot-models/examples/pytorch/nlp/huggingface_models/text-classification/pruning/magnitude/eager
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

    pip list

    if [ ! -d ${WORKSPACE}/lpot-models ]; then
        echo "\"lpot-model\" not found. Exiting..."
        exit 1
    fi
    pip install protobuf==3.20.1
    cd ${WORKSPACE}/lpot-models || return
}

function lpot_install {
    echo "Checking lpot..."
    python -V
    c_lpot=$(pip list | grep -c 'neural-compressor') || true  # Prevent from exiting when 'lpot' not found
    if [ ${c_lpot} != 0 ]; then
        pip uninstall neural-compressor-full -y
        pip list
    fi
    pip install ${WORKSPACE}/neural_compressor*.whl
    pip list
}

main
