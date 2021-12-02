#!/bin/bash -x

set -eo pipefail

function main {
    export PATH=${HOME}/miniconda3/bin/:$PATH
    # pip config set global.index-url https://pypi.douban.com/simple/

    create_conda_env
    lpot_install

    # Run Pytorch Prune test
    cd ${WORKSPACE}/lpot-models/examples/pytorch/eager/image_recognition/imagenet/cpu/prune
    if [ -f "requirements.txt" ]; then
      pip install -r requirements.txt
      echo "pip list after install requirements..."
    fi
    pip list
    python main.py /tf_dataset/pytorch/ImageNet/raw --topology resnet18 --prune --config conf.yaml --pretrained --output-model model_final.pth --batch-size 256 --keep-batch-size --lr 0.001 --iteration 30 --epochs 3 2>&1 | tee ${WORKSPACE}/pytorch_prune_resnet.log

}

function create_conda_env {
    if [[ -z ${pytorch_version} ]]; then
        pytorch_version="1.5.0+cpu"  # Set pytorch 1.5.0+cpu as default
    fi

    if [[ -z ${torchvision_version} ]]; then
        torchvision_version="0.6.0+cpu"  # Set torchvision 0.6.0+cpu as default
    fi

    if [[ -z ${python_version} ]]; then
        python_version=3.7  # Set python 3.7 as default
    fi

    conda_env_name=pytorch_prune_resnet-py${python_version}

    if [ $(conda info -e | grep ${conda_env_name} | wc -l) == 0 ]; then
        conda create python=${python_version} -y -n ${conda_env_name}
    fi
    # make sure no more conda nested
    conda deactivate || source deactivate
    conda deactivate || source deactivate
    source activate ${conda_env_name}

    pip install pytorch-ignite
    pip install torch==${pytorch_version} -f https://download.pytorch.org/whl/torch_stable.html
    pip install torchvision==${torchvision_version} -f https://download.pytorch.org/whl/torch_stable.html
    pip install ruamel.yaml==0.17.4
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
