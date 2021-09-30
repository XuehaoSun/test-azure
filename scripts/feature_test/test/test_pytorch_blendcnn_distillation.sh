#!/bin/bash
set -xe

# example
cd ${WORKSPACE}/lpot-models/

# proxy
export http_proxy='http://child-prc.intel.com:913'
export https_proxy='http://child-prc.intel.com:913'

function main {
    # conda env
    create_conda_env
    # execution
    blendcnn_distilling_log="${WORKSPACE}/blendcnn-distilling-test.log"
    distilling 2>&1 |tee ${blendcnn_distilling_log}
}

function create_conda_env {
    conda_env_name="blendcnn-distilling-test"

    if [ -f "${HOME}/miniconda3/etc/profile.d/conda.sh" ]; then
        . "${HOME}/miniconda3/etc/profile.d/conda.sh"
    else
        export PATH="${HOME}/miniconda3/bin:$PATH"
    fi
    conda remove --all -y -n ${conda_env_name}
    conda create python=3.6 -y -n ${conda_env_name}
    conda activate ${conda_env_name}
    pip install -U pip

    # pip install -r requirements.txt
    pip install fire tqdm tensorflow==2.6.0
    pip install torch==1.6.0+cpu -f https://download.pytorch.org/whl/torch_stable.html

    # install lpot
    pip install -r requirements.txt
    git submodule update --init --recursive
    pip install cmake
    cmake_path=$(which cmake)
    ln -s ${cmake_path} ${cmake_path}3 || true
    python setup.py install

}

function distilling {
    cd examples/pytorch/eager/blendcnn/distillation
    # model and MRPC
    dataset_dir="/tf_dataset2/datasets/blendcnn-distilling"
    rsync -avz ${dataset_dir}/ ./

    # fine-tune the pretrained BERT-Base model
    mkdir -p models/bert/mrpc
    python finetune.py config/finetune/mrpc/train.json

    # distilling the BlendCNN
    mkdir -p models/blendcnn/
    python distill.py --loss_weights 0.1 0.9
}

main
