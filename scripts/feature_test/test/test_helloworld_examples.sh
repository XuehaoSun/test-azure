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

    for i in `seq 8`
    do
        create_conda_env "tf_example${i}"
        lpot_install
        tf_example${i} 2>&1 | tee ${WORKSPACE}/tf_example${i}.log
    done
}

function tf_example1 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example1 || return
    cp /tf_dataset/examples_helloworld/example1/mobilenet_v1_1.0_224_frozen.pb .
    sed -i "/\/path\/to\/imagenet/s|root:.*|root: ${dataset_location}|g" conf.yaml
    python test.py
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

    cp /tf_dataset/examples_helloworld/example3/inception_v1_2016_08_28.tar.gz .
    tar -xvf inception_v1_2016_08_28.tar.gz
    sed -i "/\/path\/to\/imagenet/s|root:.*|root: ${dataset_location}|g" conf.yaml

    # Set env variables to speedup test
    export KMP_AFFINITY=granularity=fine,verbose,compact,1,0
    export KMP_BLOCKTIME=1
    export KMP_SETTINGS=1
    export TF_NUM_INTEROP_THREADS=1

    python test.py
}

function tf_example4 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example4 || return

    git clone -b 2021.4 https://github.com/openvinotoolkit/open_model_zoo.git
    python ./open_model_zoo/tools/downloader/downloader.py --name rfcn-resnet101-coco-tf --output_dir model

    python test.py
}

function tf_example5 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example5 || return

    if [ -f ${WORKSPACE}/lpot-models/examples/helloworld/tf_example1/mobilenet_v1_1.0_224_frozen.pb ]; then
        cp ${WORKSPACE}/lpot-models/examples/helloworld/tf_example1/mobilenet_v1_1.0_224_frozen.pb .
    else
        cp /tf_dataset/examples_helloworld/example1/mobilenet_v1_1.0_224_frozen.pb .
    fi
    sed -i "/\/path\/to\/imagenet/s|root:.*|root: ${dataset_location}|g" conf.yaml

    python test.py --tune
    python test.py --benchmark
}

function tf_example6 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example6 || return

    if [ -f ${WORKSPACE}/lpot-models/examples/helloworld/tf_example1/mobilenet_v1_1.0_224_frozen.pb ]; then
        cp ${WORKSPACE}/lpot-models/examples/helloworld/tf_example1/mobilenet_v1_1.0_224_frozen.pb .
    else
        cp /tf_dataset/examples_helloworld/example1/mobilenet_v1_1.0_224_frozen.pb .
    fi
    sed -i "/\/path\/to\/imagenet/s|root:.*|root: ${dataset_location}|g" conf.yaml

    python test.py --tune
    python test.py --benchmark
}

function tf_example7 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example7 || return
    python test.py --tune
    python test.py --benchmark
}

function tf_example8 {
    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example8 || return
    if [ -f ${WORKSPACE}/lpot-models/examples/helloworld/tf_example1/mobilenet_v1_1.0_224_frozen.pb ]; then
        cp ${WORKSPACE}/lpot-models/examples/helloworld/tf_example1/mobilenet_v1_1.0_224_frozen.pb .
    else
        cp /tf_dataset/examples_helloworld/example1/mobilenet_v1_1.0_224_frozen.pb .
    fi
    python test.py
}

function tf_example9 {
    export FORCE_BF16=1
    cd ${WORKSPACE}/lpot-models/examples/helloworld/tf_example9 || return
    if [ -f ${WORKSPACE}/lpot-models/examples/helloworld/tf_example1/mobilenet_v1_1.0_224_frozen.pb ]; then
        cp ${WORKSPACE}/lpot-models/examples/helloworld/tf_example1/mobilenet_v1_1.0_224_frozen.pb .
    else
        cp /tf_dataset/examples_helloworld/example1/mobilenet_v1_1.0_224_frozen.pb .
    fi
    python test.py
}

function create_conda_env {
    example_name=$1
    if [[ "${example_name}" == "tf_example3" ]]; then
      python_version="3.7"
    else
      python_version="${origin_python_version}"
    fi
    conda_env_name=lpot-py${python_version}-helloworld_${example_name}
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
    pip install protobuf==3.20.1
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
