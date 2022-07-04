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
    cd ${WORKSPACE}/lpot-models/examples/pytorch/image_recognition/torchvision_models/pruning/magnitude/eager
    if [ -f "requirements.txt" ]; then
      pip install --no-cache-dir -r requirements.txt
      echo "pip list after install requirements..."
    fi
    pip install protobuf==3.20.1
    pip list
    python main.py /tf_dataset/pytorch/ImageNet/raw --topology resnet18 --prune --config conf.yaml --pretrained --output-model model_final.pth --batch-size 256 --keep-batch-size --lr 0.001 --iteration 30 --epochs 3 2>&1 | tee ${WORKSPACE}/pytorch_prune_resnet.log

}

function create_conda_env {
    if [[ -z ${pytorch_version} ]]; then
        pytorch_version="1.9.0+cpu"  # Set pytorch 1.5.0+cpu as default
    fi

    if [[ -z ${torchvision_version} ]]; then
        torchvision_version="0.10.0+cpu"  # Set torchvision 0.6.0+cpu as default
    fi

    conda_env_name=pytorch_prune_resnet-py${python_version}

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
    c_lpot=$(pip list | grep -c 'neural-compressor') || true  # Prevent from exiting when 'lpot' not found
    if [ ${c_lpot} != 0 ]; then
        pip uninstall neural-compressor-full -y
        pip list
    fi
    pip install ${WORKSPACE}/neural_compressor*.whl
    pip list
}

main
