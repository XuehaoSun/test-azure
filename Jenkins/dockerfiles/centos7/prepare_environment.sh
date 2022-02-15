#!/bin/bash
set -x
set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='
if [ $# != "7" ] ; then 
    echo 'ERROR:'
    echo "Expected 7 parameters got $#"
    printf 'Please use following parameters:
    --python_version=<Python version>
    --tensorflow_version=<TensorFlow version>
    --pytorch_version=<PyTorch version>
    --torchvision_version=<Torchvision version>
    --mxnet_version=<MXNet version>
    --onnx_version=<ONNX version>
    --onnxruntime_version=<OnnxRuntime version>
    '
    exit 1
fi

for i in "$@"
do
    case $i in
        --python_version=*)
            python_version=$(echo $i | sed "s/${PATTERN}//");;
        --tensorflow_version=*)
            tensorflow_version=$(echo $i | sed "s/${PATTERN}//");;
        --pytorch_version=*)
            pytorch_version=$(echo $i | sed "s/${PATTERN}//");;
        --torchvision_version=*)
            torchvision_version=$(echo $i | sed "s/${PATTERN}//");;
        --mxnet_version=*)
            mxnet_version=$(echo $i | sed "s/${PATTERN}//");;
        --onnx_version=*)
            onnx_version=$(echo $i | sed "s/${PATTERN}//");;
        --onnxruntime_version=*)
            onnxruntime_version=$(echo $i | sed "s/${PATTERN}//");;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done


function main {
    create_conda_env tensorflow ${tensorflow_version} ${python_version} "LPOT_TensorFlow-${tensorflow_version}_py${python_version}-${CPU_NAME}"
    create_conda_env pytorch ${pytorch_version} ${python_version} "LPOT_PyTorch-${pytorch_version}_py${python_version}-${CPU_NAME}"
    create_conda_env mxnet ${mxnet_version} ${python_version} "LPOT_MXNet-${mxnet_version}_py${python_version}-${CPU_NAME}"
    create_conda_env onnx ${onnxruntime_version} ${python_version} "LPOT_ONNXRT-${onnxruntime_version}_py${python_version}-${CPU_NAME}"
}


function create_conda_env {
    framework="${1}"
    framework_version="${2}"
    python_version="${3}"
    env_name="${4}"
    
    conda create python=${python_version} -y -n ${env_name}
    source activate ${env_name}
    pip install -U pip

    install_framework "${framework}" "${framework_version}" "${python_version}"
    install_lpot
}


function install_framework {
    framework="${1}"
    framework_version="${2}"
    python_version="${3}"

    case ${framework} in
        tensorflow)
            install_tensorflow "${framework_version}" "${python_version}";;
        pytorch)
            install_pytorch "${framework_version}" "${python_version}";;
        mxnet)
            install_mxnet "${framework_version}" "${python_version}";;
        onnx)
            install_onnxrt "${framework_version}" "${python_version}";;
        *)
            echo "Framework ${framework} is not supported."; exit 1;;
    esac
}


function install_tensorflow {
    tensorflow_version="${1}"
    python_version="${2}"

    if [ ${tensorflow_version} == '1.15UP1' ]; then
        if [ ${python_version} == '3.6' ]; then
            install_params="https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp36-cp36m-manylinux2010_x86_64.whl"
        elif [ ${python_version} == '3.7' ]; then
            install_params="https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp37-cp37m-manylinux2010_x86_64.whl"
        elif [ ${python_version} == '3.5' ]; then
            install_params="https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp35-cp35m-manylinux2010_x86_64.whl"
        fi
    elif [ ${tensorflow_version} == '1.15UP2' ]; then
        if [ ${python_version} == '3.6' ]; then
            install_params="https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp36-cp36m-manylinux2010_x86_64.whl"
        elif [ ${python_version} == '3.7' ]; then
            install_params="https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp37-cp37m-manylinux2010_x86_64.whl"
        elif [ ${python_version} == '3.5' ]; then
            install_params="https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp35-cp35m-manylinux2010_x86_64.whl"
        fi
    elif [ ${tensorflow_version} == '1.15UP3' ]; then
        if [ ${python_version} == '3.6' ]; then
            install_params="https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up3-cp36-cp36m-manylinux_2_12_x86_64.manylinux2010_x86_64.whl"
        elif [ ${python_version} == '3.7' ]; then
            install_params="https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up3-cp37-cp37m-manylinux_2_12_x86_64.manylinux2010_x86_64.whl"
        fi
    elif [[ "${tensorflow_version}" == "customized"* ]]; then
        install_params=$(echo "${tensorflow_version}" | awk -F '=' '{print $2}')
    elif [[ "${tensorflow_version}" == "2.6.0"* ]]; then
        install_params="tensorflow==${tensorflow_version}"
    elif [[ "${tensorflow_version}" != "" ]]; then
        install_params="intel-tensorflow==${tensorflow_version}"
    else
        echo "Won't install TensorFlow!"
    fi

    if [ -z ${install_params} ]; then
        echo "TF ${tensorflow_version} do not support ${python_version}"
    else
        pip install ${install_params}
    fi
}


function install_pytorch {
    pytorch_version="${1}"
    python_version="${2}"

    if [[ "${pytorch_version}" != "" ]]; then
        torch_whl_path=/tf_dataset/pytorch/pypi
        torch_whl=${torch_whl_path}/${python_version}/torch-${pytorch_version}-*.whl
        if [ -f ${torch_whl} ]; then
            pip install ${torch_whl}
        else
            pip install torch==${pytorch_version} -f https://download.pytorch.org/whl/torch_stable.html
        fi
        torchvision_whl=${torch_whl_path}/${python_version}/torchvision-${torchvision_version}-*.whl
        if [ -f ${torchvision_whl} ]; then
            pip install ${torchvision_whl}
        else
            pip install torchvision==${torchvision_version} -f https://download.pytorch.org/whl/torch_stable.html
        fi
    else
        echo "Won't install PyTorch!"
    fi
}


function install_mxnet {
    mxnet_version="${1}"
    python_version="${2}"

    if [ ${mxnet_version} == '1.6.0' ]; then
        pip install mxnet-mkl==${mxnet_version}
    elif [ ${mxnet_version} == '1.7.0' ]; then
        pip install mxnet==${mxnet_version}.post2
    elif [ ${mxnet_version} != '' ]; then
        pip install mxnet==${mxnet_version}
    else
        echo "Won't install MXNet!"
    fi
}


function install_onnxrt {
    onnxruntime_version="${1}"
    python_version="${2}"

    if [ ${onnxruntime_version} != '' ]; then
        pip install onnx==${onnx_version}
        # if onnxrt==nightly then use requirements to install
        if [ ${onnxruntime_version} != "nightly" ]; then
            pip install onnxruntime==${onnxruntime_version}
        fi
    else
        echo "Won't install ONNXRT!"
    fi
}


function install_lpot {
    pushd lpot
    pip install -r requirements.txt
    python setup.py install
    popd
}

main
