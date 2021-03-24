#!/bin/bash

set -x

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
    create_conda_env 2.3.0

    cd ${WORKSPACE}/lpot-models/examples/helloworld || return
    python train.py

    for i in `seq 5`
    do
        tf_example${i} 2>&1 | tee ${WORKSPACE}/tf_example${i}.log
    done

}

function tf_example1 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld || return
    if [ ! -d frozen_models ]; then
        echo " frozen pb not generated. Exiting..."
        exit 1
    fi

    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example1 || return
    if [ -f "requirements.txt" ]; then
        python -m pip install -r requirements.txt
        pip list
    fi
    lpot_install

    wget https://storage.googleapis.com/intel-optimized-tensorflow/models/v1_6/mobilenet_v1_1.0_224_frozen.pb
    sed -i "/\/path\/to\/imagenet/s|root:.*|root: ${dataset_location}|g" conf.yaml

    python test.py
}

function tf_example2 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld || return
    if [ ! -d frozen_models ]; then
        echo " frozen pb not generated. Exiting..."
        exit 1
    fi

    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example2 || return
    if [ -f "requirements.txt" ]; then
        python -m pip install -r requirements.txt
        pip list
    fi
    lpot_install

    python test.py
}

function tf_example3 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld || return
    if [ ! -d frozen_models ]; then
        echo " frozen pb not generated. Exiting..."
        exit 1
    fi

    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example3 || return
    if [ -f "requirements.txt" ]; then
        python -m pip install -r requirements.txt
        pip list
    fi
    lpot_install

    wget http://download.tensorflow.org/models/inception_v1_2016_08_28.tar.gz
    tar -xvf inception_v1_2016_08_28.tar.gz
    sed -i "/\/path\/to\/imagenet/s|root:.*|root: ${dataset_location}|g" conf.yaml

    python test.py
}

function tf_example4 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld || return
    if [ ! -d frozen_models ]; then
        echo " frozen pb not generated. Exiting..."
        exit 1
    fi

    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example4 || return
    if [ -f "requirements.txt" ]; then
        python -m pip install -r requirements.txt
        pip list
    fi
    lpot_install

    git clone https://github.com/openvinotoolkit/open_model_zoo.git
    python ./open_model_zoo/tools/downloader/downloader.py --name rfcn-resnet101-coco-tf --output_dir model

    python test.py
}

function tf_example5 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld || return
    if [ ! -d frozen_models ]; then
        echo " frozen pb not generated. Exiting..."
        exit 1
    fi

    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example5 || return
    if [ -f "requirements.txt" ]; then
        python -m pip install -r requirements.txt
        pip list
    fi
    lpot_install

    if [-f ${WORKSPACE}/lpot-models/examples/helloworld/tf_example1/mobilenet_v1_1.0_224_frozen.pb ]; then
        mv ${WORKSPACE}/lpot-models/examples/helloworld/tf_example1/mobilenet_v1_1.0_224_frozen.pb .
    else
        wget https://storage.googleapis.com/intel-optimized-tensorflow/models/v1_6/mobilenet_v1_1.0_224_frozen.pb
    fi
    sed -i "/\/path\/to\/imagenet/s|root:.*|root: ${dataset_location}|g" conf.yaml

    python test.py
}

function create_conda_env {
    tensorflow_version=$1
    python_version=3.6
    conda_env_name=lpot-py${python_version}-helloworld_examples

    if [ $(conda info -e | grep ${conda_env_name} | wc -l) == 0 ]; then
        conda create python=${python_version} -y -n ${conda_env_name}
    fi
    # make sure no more conda nested
    conda deactivate || source deactivate
    conda deactivate || source deactivate
    source activate ${conda_env_name}
    pip install intel-tensorflow==${tensorflow_version}
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
