#!/bin/bash
set -x

PATTERN='[-a-zA-Z0-9_]*='

for i in "$@"
do
    case $i in
        --dataset_location=*)
            dataset_location=`echo $i | sed "s/${PATTERN}//"`;;
        --python_version=*)
            origin_python_version=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

function main {
    export PATH=${HOME}/miniconda3/bin/:$PATH
    cd ${WORKSPACE}/lpot-models/examples/helloworld || return

    for i in `seq 7`
    do
        create_conda_env "tf_example${i}"
        lpot_install
        tf_example${i} 2>&1 | tee ${WORKSPACE}/tf_example${i}.log
    done
}

function tf_example1 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example1 || return
    cp /tf_dataset/examples_helloworld/example1/mobilenet_v1_1.0_224_frozen.pb .
    python test.py --dataset_location=${dataset_location}
}

function tf_example2 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld || return
    python train.py

    if [ ! -d models ]; then
        echo " frozen pb not generated. Exiting..."
        exit 1
    fi
    
    cd ./tf_example2
    python test.py
}

function tf_example3 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example3 || return
    cp /tf_dataset/examples_helloworld/example1/mobilenet_v1_1.0_224_frozen.pb .

    # force bf16 for mix precision test
    export FORCE_BF16=1
    python test.py --dataset_location=${dataset_location}
}

function tf_example4 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example4 || return

    git clone -b 2021.4 https://github.com/openvinotoolkit/open_model_zoo.git
    python ./open_model_zoo/tools/downloader/downloader.py --name rfcn-resnet101-coco-tf --output_dir model

    python test.py
}

function tf_example5 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example5 || return
    cp /tf_dataset/examples_helloworld/example1/mobilenet_v1_1.0_224_frozen.pb .

    python test.py --tune --dataset_location=${dataset_location}
    python test.py --benchmark --dataset_location=${dataset_location}
}

function tf_example6 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example6 || return
    cp /tf_dataset/examples_helloworld/example1/mobilenet_v1_1.0_224_frozen.pb .

    python test.py --tune --dataset_location=${dataset_location}
    python test.py --benchmark --dataset_location=${dataset_location}
}

function tf_example7 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example7 || return
    cp /tf_dataset/examples_helloworld/example1/mobilenet_v1_1.0_224_frozen.pb .

    python test.py
}

function create_conda_env {
    example_name=$1

    python_version="${origin_python_version}"
    conda_env_name=lpot-py${python_version}-helloworld
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
    cd ${WORKSPACE}/lpot-models/examples/helloworld/${example_name} || return
    if [ -f "requirements.txt" ]; then
        python -m pip install -r requirements.txt
        pip list
    fi
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
