#!/bin/bash -x

set -eo pipefail

function main {
    export PATH=${HOME}/miniconda3/bin/:$PATH

    create_conda_env
    lpot_install

    # Run Pytorch Prune test
    cd ${WORKSPACE}/lpot-models/examples/pytorch/eager/image_recognition/imagenet/cpu/qat_during_prune

    # Update prune_conf.yaml
    sed -i "/\/path\/to\/imagenet\/train/s|root:.*|root: \/tf_dataset\/pytorch\/ImageNet\/raw\/train|g" prune_conf.yaml
    sed -i "/\/path\/to\/imagenet\/val/s|root:.*|root: \/tf_dataset\/pytorch\/ImageNet\/raw\/val|g" prune_conf.yaml

    # Update qat_conf.yaml
    sed -i "/\/path\/to\/imagenet\/train/s|root:.*|root: \/tf_dataset\/pytorch\/ImageNet\/raw\/train|g" qat_conf.yaml

    python main.py -t -a resnet50 --pretrained /tf_dataset/pytorch/ImageNet/raw/ 2>&1 | tee ${WORKSPACE}/pytorch_qat_during_prune.log

}

function create_conda_env {
    if [[ -z ${pytorch_version} ]]; then
        pytorch_version="1.8.0+cpu"  # Set pytorch 1.8.0+cpu as default
    fi

    if [[ -z ${torchvision_version} ]]; then
        torchvision_version="0.9.0+cpu"  # Set torchvision 0.9.0+cpu as default
    fi

    if [[ -z ${python_version} ]]; then
        python_version=3.6  # Set python 3.6 as default
    fi

    conda_env_name=pytorch_qat_during_prune-py${python_version}

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
