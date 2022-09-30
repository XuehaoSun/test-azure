#!/bin/bash
set -xe

PATTERN='[-a-zA-Z0-9_]*='
for i in "$@"
do
    case $i in
        --model=*)
            model=`echo $i | sed "s/${PATTERN}//"`;;
        --python_version=*)
            python_version=`echo $i | sed "s/${PATTERN}//"`;;
        --tensorflow_version=*)
            tensorflow_version=`echo $i | sed "s/${PATTERN}//"`;;
        --pytorch_version=*)
            pytorch_version=`echo $i | sed "s/${PATTERN}//"`;;
        --torchvision_version=*)
            torchvision_version=`echo $i | sed "s/${PATTERN}//"`;;
        --mxnet_version=*)
            mxnet_version=`echo $i | sed "s/${PATTERN}//"`;;
        --onnx_version=*)
            onnx_version=`echo $i | sed "s/${PATTERN}//"`;;
        --onnxruntime_version=*)
            onnxruntime_version=`echo $i | sed "s/${PATTERN}//"`;;
        --engine_version=*)
            engine_version=`echo $i | sed "s/${PATTERN}//"`;;
        --conda_env_name=*)
            conda_env_name=`echo $i | sed "s/${PATTERN}//"`;;
        --install_ipex=*)
            install_ipex=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

# update conda env name


# step 0: export conda
if [[ ${model} = "dlrm"* ]] && [[ "${pytorch_version}" != "" ]]; then
    export PATH=${HOME}/anaconda3/bin/:$PATH
    export https_proxy=http://proxy-prc.intel.com:913
    export http_proxy=http://proxy-prc.intel.com:913
else
    export PATH=${HOME}/miniconda3/bin/:$PATH
fi

# add channels
conda_ver1=$(conda -V |awk -F '[. ]' '{print $2}')
conda_ver2=$(conda -V |awk -F '[. ]' '{print $3}')
if [[ ${conda_ver1} -le 4 ]] && [[ ${conda_ver2} -lt 10 ]] && [[ ${model} != "dlrm"* ]]; then
    conda update conda -y
fi

function update_conda_env {
    if [ $(conda info -e | grep ${conda_env_name} | wc -l) != 0 ]; then
        (conda remove --name ${conda_env_name} --all -y) || true
    fi
    conda_dir=$(dirname $(dirname $(which conda)))
    if [ -d ${conda_dir}/envs/${conda_env_name} ]; then
        rm -rf ${conda_dir}/envs/${conda_env_name}
    fi
    offending_pkg_dir=("libgcc-ng-9.3.0-h5101ec6_17" "libffi-3.3-he6710b0_2")
    for pkg in ${offending_pkg_dir[@]}
    do 
        [[ -d ${conda_dir}/pkgs/${pkg} && $(ls ${conda_dir}/pkgs/${pkg} | wc -l) != 0 ]] && rm -fr ${conda_dir}/pkgs/${pkg}
    done
    conda config --add channels defaults
    conda create python=${python_version} -y -n ${conda_env_name}

    source activate ${conda_env_name}

    # Upgrade pip
    pip install -U pip

}

echo -e "\nUpdate conda env... "
update_conda_env

#workaround for python3.10 collection interface problem
if [ "${pytorch_version}" == "3.10" ]; then
    pip install py4j>=0.10.9.5
fi

# Install TF
if [ "${tensorflow_version}" == '1.15UP1' ]; then
    if [ ${python_version} == '3.6' ]; then
        pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp36-cp36m-manylinux2010_x86_64.whl
    elif [ ${python_version} == '3.7' ]; then
        pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp37-cp37m-manylinux2010_x86_64.whl
    elif [ ${python_version} == '3.5' ]; then
        pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp35-cp35m-manylinux2010_x86_64.whl
    else
        echo "!!! TF 1.15UP1 do not support ${python_version}"
    fi
elif [ "${tensorflow_version}" == '1.15UP2' ]; then
    if [ ${python_version} == '3.6' ]; then
        pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp36-cp36m-manylinux2010_x86_64.whl
    elif [ ${python_version} == '3.7' ]; then
        pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp37-cp37m-manylinux2010_x86_64.whl
    elif [ ${python_version} == '3.5' ]; then
        pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp35-cp35m-manylinux2010_x86_64.whl
    else
        echo "!!! TF 1.15UP2 do not support ${python_version}"
    fi
elif [ "${tensorflow_version}" == '1.15UP3' ]; then
    if [ ${python_version} == '3.6' ]; then
        pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up3-cp36-cp36m-manylinux_2_12_x86_64.manylinux2010_x86_64.whl
    elif [ ${python_version} == '3.7' ]; then
        pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up3-cp37-cp37m-manylinux_2_12_x86_64.manylinux2010_x86_64.whl
    else
        echo "!!! TF 1.15UP3 do not support ${python_version}"
    fi
elif [[ "${tensorflow_version}" == "customized"* ]]; then
    download_link=$(echo "${tensorflow_version}" | awk -F '=' '{print $2}')
    pip install "${download_link}"
elif [[ "${tensorflow_version}" == '2.6.0' ]]; then
    pip install intel-tensorflow==${tensorflow_version}
    pip install tensorflow-estimator==2.6.0
    pip install keras==2.6.0
elif [[ "${tensorflow_version}" == '2.6.2' ]] || [[ "${tensorflow_version}" == '2.6.1' ]]; then
    pip install tensorflow==${tensorflow_version}
elif [[ "${tensorflow_version}" == "spr-base" ]]; then
    pip install ${WORKSPACE}/tensorflow*.whl
elif [[ "${tensorflow_version}" == *"-official" ]]; then
    pip install tensorflow==${tensorflow_version%-official}
elif [[ "${tensorflow_version}" != "" ]]; then
    pip install intel-tensorflow==${tensorflow_version}
    pip install protobuf==3.20.1
else
    echo "Won't install TensorFlow!"
fi

# Install PyTorch
if [[ "${pytorch_version}" == "nightly" ]]; then
    pip install sklearn onnx
    pip install lark-parser hypothesis
    conda install numpy ninja pyyaml mkl mkl-include setuptools cmake cffi typing_extensions future six requests dataclasses psutil
    torch_whl=${WORKSPACE}/torch-*.whl
    if [ -f ${torch_whl} ]; then
        pip install ${torch_whl}
    fi
    torchvision_whl=${WORKSPACE}/torchvision-*.whl
    if [ -f ${torchvision_whl} ]; then
        pip install ${torchvision_whl}
    fi
elif [[ "${pytorch_version}" != "" ]]; then
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
    if [[ ${model} == '3dunet' ]] && [[ ${python_version} != "3.10" ]]; then
        # Install mlperf_loadgen
        pip install absl-py
        mlperf_loadgen_whl=/tf_dataset/pytorch/mlperf_3dunet/mlperf_loadgen-0.5a0-cp${python_version//./}-*.whl
        pip install ${mlperf_loadgen_whl}
    elif [[ ${model} == '3dunet' ]] && [[ ${python_version} == "3.10" ]]; then
        git clone https://github.com/mlcommons/inference.git --recursive
        cd inference/loadgen
        pip install absl-py
        python setup.py install
        cd -
    fi
else
    echo "Won't install PyTorch!"
fi

if [[ "${install_ipex}" == "true" ]]; then
    case "${pytorch_version}" in
        1.8.0*)
            case "${python_version}" in
                3.6)
                    install_params="/tf_dataset/pytorch/torch_ipex-1.8.0-cp36-cp36m-linux_x86_64.whl";;
                3.7)
                    install_params="torch_ipex==1.8.0 -f https://software.intel.com/ipex-whl-stable";;
                3.8)
                    install_params="/tf_dataset/pytorch/torch_ipex-1.8.0-cp38-cp38-linux_x86_64.whl";;
            esac;;
        1.9.0*)
            case "${python_version}" in
                3.6)
                    ipex_whl="/tf_dataset/pytorch/torch_ipex-1.9.0-cp36-cp36m-linux_x86_64.whl";;
                3.7)
                    ipex_whl="/tf_dataset/pytorch/torch_ipex-1.9.0-cp37-cp37m-linux_x86_64.whl";;
                3.8)
                    ipex_whl="/tf_dataset/pytorch/torch_ipex-1.9.0-cp38-cp38-linux_x86_64.whl";;
            esac
            [[ -f ${ipex_whl} ]] && install_params="${ipex_whl}" || install_params="torch_ipex==1.9.0 -f https://software.intel.com/ipex-whl-stable";;
        1.10.0*)
            case "${python_version}" in
                3.7)
                    ipex_whl="https://intel-optimized-pytorch.s3.cn-north-1.amazonaws.com.cn/wheels/v1.10/intel_extension_for_pytorch-1.10.0%2Bcpu-cp37-cp37m-linux_x86_64.whl";;
                3.8)
                    ipex_whl="https://intel-optimized-pytorch.s3.cn-north-1.amazonaws.com.cn/wheels/v1.10/intel_extension_for_pytorch-1.10.0%2Bcpu-cp38-cp38-linux_x86_64.whl";;
                3.9)
                    ipex_whl="https://intel-optimized-pytorch.s3.cn-north-1.amazonaws.com.cn/wheels/v1.10/intel_extension_for_pytorch-1.10.0%2Bcpu-cp39-cp39-linux_x86_64.whl";;
            esac
            [[ ! -z "${ipex_whl}" ]] && install_params="${ipex_whl}" || install_params="torch_ipex==1.10.0 -f https://software.intel.com/ipex-whl-stable";;
        1.10.1*)
            install_params="intel_extension_for_pytorch==1.10.100+cpu -f https://software.intel.com/ipex-whl-stable";;
        1.11.0*)
            case "${python_version}" in
                3.7)
                    ipex_whl="http://intel-optimized-pytorch.s3.cn-north-1.amazonaws.com.cn/wheels/v1.11.0/intel_extension_for_pytorch-1.11.0%2Bcpu-cp37-cp37m-linux_x86_64.whl";;
                3.8)
                    ipex_whl="http://intel-optimized-pytorch.s3.cn-north-1.amazonaws.com.cn/wheels/v1.11.0/intel_extension_for_pytorch-1.11.0%2Bcpu-cp38-cp38-linux_x86_64.whl";;
                3.9)
                    ipex_whl="http://intel-optimized-pytorch.s3.cn-north-1.amazonaws.com.cn/wheels/v1.11.0/intel_extension_for_pytorch-1.11.0%2Bcpu-cp39-cp39-linux_x86_64.whl";;
                3.10)
                    ipex_whl="http://intel-optimized-pytorch.s3.cn-north-1.amazonaws.com.cn/wheels/v1.11.0/intel_extension_for_pytorch-1.11.0%2Bcpu-cp310-cp310-linux_x86_64.whl";;
            esac
            [[ ! -z "${ipex_whl}" ]] && install_params="${ipex_whl}" || install_params="torch_ipex==1.11.0 -f https://software.intel.com/ipex-whl-stable";;
        1.12.0*)
            case "${python_version}" in
                3.7)
                    ipex_whl="http://intel-optimized-pytorch.s3.cn-north-1.amazonaws.com.cn/wheels/v1.12.0/intel_extension_for_pytorch-1.12.0%2Bcpu-cp37-cp37m-linux_x86_64.whl";;
                3.8)
                    ipex_whl="http://intel-optimized-pytorch.s3.cn-north-1.amazonaws.com.cn/wheels/v1.12.0/intel_extension_for_pytorch-1.12.0%2Bcpu-cp38-cp38-linux_x86_64.whl";;
                3.9)
                    ipex_whl="http://intel-optimized-pytorch.s3.cn-north-1.amazonaws.com.cn/wheels/v1.12.0/intel_extension_for_pytorch-1.12.0%2Bcpu-cp39-cp39-linux_x86_64.whl";;
                3.10)
                    ipex_whl="http://intel-optimized-pytorch.s3.cn-north-1.amazonaws.com.cn/wheels/v1.12.0/intel_extension_for_pytorch-1.12.0%2Bcpu-cp310-cp310-linux_x86_64.whl";;
            esac
            [[ ! -z "${ipex_whl}" ]] && install_params="${ipex_whl}" || install_params="torch_ipex==1.12.0 -f https://software.intel.com/ipex-whl-stable";;
        1.12.1*)
            case "${python_version}" in
                3.7)
                    ipex_whl="http://intel-optimized-pytorch.s3.cn-north-1.amazonaws.com.cn/wheels/v1.12.100/intel_extension_for_pytorch-1.12.100%2Bcpu-cp37-cp37m-linux_x86_64.whl";;
                3.8)
                    ipex_whl="http://intel-optimized-pytorch.s3.cn-north-1.amazonaws.com.cn/wheels/v1.12.100/intel_extension_for_pytorch-1.12.100%2Bcpu-cp38-cp38-linux_x86_64.whl";;
                3.9)
                    ipex_whl="http://intel-optimized-pytorch.s3.cn-north-1.amazonaws.com.cn/wheels/v1.12.100/intel_extension_for_pytorch-1.12.100%2Bcpu-cp39-cp39-linux_x86_64.whl";;
                3.10)
                    ipex_whl="http://intel-optimized-pytorch.s3.cn-north-1.amazonaws.com.cn/wheels/v1.12.100/intel_extension_for_pytorch-1.12.100%2Bcpu-cp310-cp310-linux_x86_64.whl";;
            esac
            [[ ! -z "${ipex_whl}" ]] && install_params="${ipex_whl}" || install_params="torch_ipex==1.12.1 -f https://software.intel.com/ipex-whl-stable";;

        "nightly")
            case "${python_version}" in
                 3.8)
                    ipex_whl="intel_extension_for_pytorch*.whl";;
             esac
            [[ ! -z "${ipex_whl}" ]] && install_params="${ipex_whl}"

            #pip install cmake
            #cmake_path=`which cmake`
            #ln -s $cmake_path ${cmake_path}3
            #git clone https://github.com/intel-innersource/frameworks.ai.pytorch.ipex-cpu.git
            #cd frameworks.ai.pytorch.ipex-cpu
            #git submodule sync && git submodule update --init --recursive
            #python setup.py install
            #cd -
    esac
    if [[ ! -z ${install_params} ]]; then
        pip install ${install_params}
    fi
fi

# Install MXNet
if [ "${mxnet_version}" == '1.6.0' ]; then
    pip install mxnet-mkl==${mxnet_version}
elif [ "${mxnet_version}" == '1.7.0' ]; then
    pip install mxnet==${mxnet_version}.post2
elif [ "${mxnet_version}" != '' ]; then
    pip install mxnet==${mxnet_version}
else
    echo "Won't install MXNet!"
fi

# Install ONNX
if [ "${onnxruntime_version}" != '' ]; then
    pip install onnx==${onnx_version}
    # if onnxrt==nightly then use requirements to install
    if [ "${onnxruntime_version}" != "nightly" ]; then
        pip install onnxruntime==${onnxruntime_version}
    fi
else
    echo "Won't install ONNXRT!"
fi

# Engine env setup with TF
if [ ${engine_version} != '' ]; then
    if [ ${python_version} == '3.6' ]; then
        pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp36-cp36m-manylinux2010_x86_64.whl
    elif [ ${python_version} == '3.7' ]; then
        pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp37-cp37m-manylinux2010_x86_64.whl
    elif [ ${python_version} == '3.5' ]; then
        pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp35-cp35m-manylinux2010_x86_64.whl
    else
        echo "!!! TF 1.15UP2 do not support ${python_version}"
    fi
fi

if [ -f "${WORKSPACE}/lpot-validation/requirements.txt" ]; then
    pip install -r "${WORKSPACE}/lpot-validation/requirements.txt"
fi

wait

echo "pip list all the components------------->"
pip list
sleep 2
echo "------------------------------------------"
echo "conda list all the components------------->"
conda list
sleep 2
echo "------------------------------------------"

