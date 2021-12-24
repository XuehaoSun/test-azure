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
        pytorch_version="1.9.0+cpu"  # Set pytorch 1.8.0+cpu as default
    fi

    if [[ -z ${torchvision_version} ]]; then
        torchvision_version="0.10.0+cpu"  # Set torchvision 0.9.0+cpu as default
    fi

    if [[ -z ${python_version} ]]; then
        python_version=3.7  # Set python 3.7 as default
    fi

    conda_env_name=pytorch_qat_during_prune-py${python_version}

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

    pip install pytorch-ignite
    pip install torch==${pytorch_version} -f https://download.pytorch.org/whl/torch_stable.html
    pip install torchvision==${torchvision_version} -f https://download.pytorch.org/whl/torch_stable.html

    if [ ! -d ${WORKSPACE}/lpot-models ]; then
        echo "\"lpot-model\" not found. Exiting..."
        exit 1
    fi
    cd ${WORKSPACE}/lpot-models || return
}

function lpot_install {
    echo "Checking lpot..."
    python -V
    c_lpot=$(pip list | grep -c 'neural-compressor') || true  # Prevent from exiting when 'lpot' not found
    if [ ${c_lpot} != 0 ]; then
        pip uninstall neural-compressor -y
        pip list
    fi
    pip install ${WORKSPACE}/neural_compressor*.whl

    # re-install pycocotools resolve the issue with numpy
    echo "re-install pycocotools resolve the issue with numpy..."
    pip uninstall pycocotools -y
    pip install --no-cache-dir pycocotools

    pip list
}

main
