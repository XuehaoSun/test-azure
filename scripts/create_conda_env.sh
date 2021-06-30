#!/bin/bash
set -xe

PATTERN='[-a-zA-Z0-9_]*='
for i in "$@"
do
    case $i in
        --model=*)
            model=`echo $i | sed "s/${PATTERN}//"`;;
        --framework=*)
            framework=`echo $i | sed "s/${PATTERN}//"`;;
        --framework_version=*)
            framework_version=`echo $i | sed "s/${PATTERN}//"`;;
        --python_version=*)
            python_version=`echo $i | sed "s/${PATTERN}//"`;;
        --onnx_version=*)
            onnx_version=`echo $i | sed "s/${PATTERN}//"`;;
        --requirement_list=*)
            requirement_list=`echo $i | sed "s/${PATTERN}//"`;;
        --conda_env_name=*)
            conda_env_name=`echo $i | sed "s/${PATTERN}//"`;;
        --torchvision_version=*)
            torchvision_version=`echo $i | sed "s/${PATTERN}//"`;;
        --tensorflow_version=*)
            tensorflow_version=`echo $i | sed "s/${PATTERN}//"`;;
        --onnxruntime_version=*)
            onnxruntime_version=`echo $i | sed "s/${PATTERN}//"`;;
        --run_ut=*)
            run_ut=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

function update_conda_env {

    if [ $(conda info -e | grep ${conda_env_name} | wc -l) != 0 ]; then
        conda remove --name ${conda_env_name} --all -y
    fi

    conda_dir=$(dirname $(dirname $(which conda)))
    if [ -d ${conda_dir}/envs/${conda_env_name} ]; then
        rm -rf ${conda_dir}/envs/${conda_env_name}
    fi

    conda create python=${python_version} -y -n ${conda_env_name}

    source activate ${conda_env_name}

    # Upgrade pip
    pip install -U pip

}

# step 0: export conda
if [[ ${model} = "dlrm"* ]]; then
    export PATH=${HOME}/anaconda3/bin/:$PATH
    export https_proxy=http://child-prc.intel.com:913
    export http_proxy=http://child-prc.intel.com:913
else
    export PATH=${HOME}/miniconda3/bin/:$PATH
fi

if [[ ${run_ut} != '' ]]; then
# for ut test
    echo -e "\nUpdate conda env... "
    update_conda_env

    # Install TF
    if [ ${tensorflow_version} == '1.15UP1' ]; then
        if [ ${python_version} == '3.6' ]; then
            pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp36-cp36m-manylinux2010_x86_64.whl
        elif [ ${python_version} == '3.7' ]; then
            pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp37-cp37m-manylinux2010_x86_64.whl
        elif [ ${python_version} == '3.5' ]; then
            pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp35-cp35m-manylinux2010_x86_64.whl
        else
            echo "!!! TF 1.15UP1 do not support ${python_version}"
        fi
    elif [ ${tensorflow_version} == '1.15UP2' ]; then
        if [ ${python_version} == '3.6' ]; then
            pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp36-cp36m-manylinux2010_x86_64.whl
        elif [ ${python_version} == '3.7' ]; then
            pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp37-cp37m-manylinux2010_x86_64.whl
        elif [ ${python_version} == '3.5' ]; then
            pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp35-cp35m-manylinux2010_x86_64.whl
        else
            echo "!!! TF 1.15UP2 do not support ${python_version}"
        fi
    elif [[ "${tensorflow_version}" == "customized"* ]]; then
            download_link=$(echo "${tensorflow_version}" | awk -F '=' '{print $2}')
            pip install "${download_link}"
    else
        pip install intel-tensorflow==${tensorflow_version}
    fi

    # Install PyTorch
    if [ ${framework} == "pytorch" ] && [[ ${model} == *"_qat"* ]]; then
        pip install torch==1.8.0+cpu torchvision==0.9.0+cpu -f https://download.pytorch.org/whl/torch_stable.html
    else
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
    fi

    # Install MXNet
    if [ ${mxnet_version} == '1.6.0' ]; then
        pip install mxnet-mkl==${mxnet_version}
    elif [ ${mxnet_version} == '1.7.0' ]; then
        pip install mxnet==${mxnet_version}.post2
    else
        pip install mxnet==${mxnet_version}
    fi

    # Install ONNX
    pip install onnx==${onnx_version}
    # if onnxrt==nightly then use requirements to install
    if [ ${onnxruntime_version} != "nightly" ]; then
        pip install onnxruntime==${onnxruntime_version}
    fi

    wait

else
# for model validation
    echo -e "\nUpdate conda env... "
    # pip config set global.index-url https://pypi.douban.com/simple/
    update_conda_env

    if [ ${framework} == 'tensorflow' ]; then
        if [ ${framework_version} == '1.15UP1' ]; then
            if [ ${python_version} == '3.6' ]; then
                pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp36-cp36m-manylinux2010_x86_64.whl
            elif [ ${python_version} == '3.7' ]; then
                pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp37-cp37m-manylinux2010_x86_64.whl
            elif [ ${python_version} == '3.5' ]; then
                pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp35-cp35m-manylinux2010_x86_64.whl
            else
                echo "!!! TF 1.15UP1 do not support ${python_version}"
            fi
        elif [ ${framework_version} == '1.15UP2' ]; then
            if [ ${python_version} == '3.6' ]; then
                pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp36-cp36m-manylinux2010_x86_64.whl
            elif [ ${python_version} == '3.7' ]; then
                pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp37-cp37m-manylinux2010_x86_64.whl
            elif [ ${python_version} == '3.5' ]; then
                pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up2-cp35-cp35m-manylinux2010_x86_64.whl
            else
                echo "!!! TF 1.15UP2 do not support ${python_version}"
            fi
        elif [[ "${framework_version}" == "customized"* ]]; then
            download_link=$(echo "${framework_version}" | awk -F '=' '{print $2}')
            pip install "${download_link}"
        else
            pip install intel-${framework}==${framework_version}
        fi
    elif [ ${framework} == 'pytorch' ]; then
        if [[ ${model} == 'dlrm'* ]]; then
            torch_whl_path=/home/torch/lpot/pytorch/pypi
        else
            torch_whl_path=/tf_dataset/pytorch/pypi
        fi
        torch_whl=${torch_whl_path}/${python_version}/torch-${framework_version}-*.whl
        if [ -f ${torch_whl} ]; then
            pip install ${torch_whl}
        else
            pip install torch==${framework_version} -f https://download.pytorch.org/whl/torch_stable.html
        fi
        torchvision_whl=${torch_whl_path}/${python_version}/torchvision-${torchvision_version}-*.whl
        if [ -f ${torchvision_whl} ]; then
            pip install ${torchvision_whl}
        else
            pip install torchvision==${torchvision_version} -f https://download.pytorch.org/whl/torch_stable.html
        fi

        if [ ${model} == '3dunet' ]; then
            # Install mlperf_loadgen
            pip install absl-py
            mlperf_loadgen_whl=/tf_dataset/pytorch/mlperf_3dunet/mlperf_loadgen-0.5a0-cp${python_version//./}-*.whl
            pip install ${mlperf_loadgen_whl}
        fi
    elif [ ${framework} == 'mxnet' ]; then
        if [ ${framework_version} == '1.6.0' ]; then
            pip install ${framework}-mkl==${framework_version}
        elif [ ${framework_version} == '1.7.0' ]; then
            pip install ${framework}==${framework_version}.post2
        else
            pip install ${framework}==${framework_version}
        fi
    elif [ ${framework} == 'onnxrt' ]; then
        pip install onnx==${onnx_version}
        # if onnxrt==nightly then use requirements to install
        if [ ${framework_version} != "nightly" ]; then
            pip install onnxruntime==${framework_version}
        fi
    fi

    wait

    if [[ ${requirement_list} != '' ]]; then
        pip install ${requirement_list}
    fi
fi

echo "pip list all the components------------->"
pip list
sleep 2
echo "------------------------------------------"

