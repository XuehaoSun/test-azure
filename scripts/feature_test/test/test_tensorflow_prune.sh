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
    if [ ! -d "${WORKSPACE}/lpot-models/examples/tensorflow/image_recognition/resnet_v2/pruning/magnitude" ]; then
        git clone -b old_api_examples ${lpot_url} old-lpot-models
        cd old-lpot-models
        git branch 
        mkdir -p ${WORKSPACE}/lpot-models/examples/tensorflow/image_recognition/resnet_v2/pruning/magnitude
        cp -r ${WORKSPACE}/old-lpot-models/examples/tensorflow/image_recognition/resnet_v2/pruning/magnitude/. ${WORKSPACE}/lpot-models/examples/tensorflow/image_recognition/resnet_v2/pruning/magnitude
    fi

    # Run TensorFlow Pruning test
    cd ${WORKSPACE}/lpot-models/examples/tensorflow/image_recognition/resnet_v2/pruning/magnitude
    pip install intel-tensorflow==2.11.0
    # re-install pycocotools resolve the issue with numpy
    echo "re-install pycocotools resolve the issue with numpy..."
    pip uninstall pycocotools -y
    pip install --no-cache-dir pycocotools
    pip install protobuf==3.20.1
    pip list
    cp -r /tf_dataset2/models/tensorflow/resnet_v2/baseline_model .
    python main.py   2>&1 | tee ${WORKSPACE}/tensorflow_prune.log

}

function create_conda_env {

    if [[ -z ${python_version} ]]; then
        python_version=3.8  # Set python 3.7 as default
    fi

    conda_env_name=tensorflow_prune-py${python_version}

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
