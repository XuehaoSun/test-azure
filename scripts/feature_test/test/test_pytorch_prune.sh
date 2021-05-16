#!/bin/bash -x

set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='
if [ $# != "1" ] ; then
    echo 'ERROR:'
    echo "Expected 1 parameter got $#"
    printf 'Please use following parameters:
    --dataset_location=<path to raw imagenet dataset>
    '
    exit 1
fi

for i in "$@"
do
    case $i in
        --dataset_location=*)
            dataset_location=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

function main {
    export PATH=${HOME}/miniconda3/bin/:$PATH
    # pip config set global.index-url https://pypi.douban.com/simple/

    create_conda_env
    lpot_install

    # Temporary workaround for config - can be removed inf config will be fixed in LPOT repository
    config_path=${WORKSPACE}/lpot-validation/scripts/feature_test/test/pytorch_prune_config.yaml

    # Run Pytorch Prune test
    cd ${WORKSPACE}/lpot-models/examples/pytorch/eager/image_recognition/imagenet/cpu/prune
    python main.py ${dataset_location} --prune --config ${config_path} --pretrained 2>&1 | tee ${WORKSPACE}/pytorch_prune.log
}

function create_conda_env {
    if [[ -z ${pytorch_version} ]]; then
        pytorch_version="1.5.0+cpu"  # Set pytorch 1.5.0+cpu as default
    fi

    if [[ -z ${torchvision_version} ]]; then
        torchvision_version="0.6.0+cpu"  # Set torchvision 0.6.0+cpu as default
    fi

    if [[ -z ${python_version} ]]; then
        python_version=3.6  # Set python 3.6 as default
    fi

    conda_env_name=pt${pytorch_version}-py${python_version}-pytorch_prune

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
    pip install ruamel.yaml
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
