#!/bin/bash

set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='
if [ $# != "5" ] ; then
    echo 'ERROR:'
    echo "Expected 5 parameters got $#"
    printf 'Please use following parameters:
    --framework=<framework name>
    --model=<model name>
    --conda_env_name=<conda environment name>
    --conda_env_mode=<conda environment mode>
    --log_level=<INC LOGLEVEL>
    '
    exit 1
fi

for i in "$@"
do
    case $i in
        --framework=*)
            framework=`echo $i | sed "s/${PATTERN}//"`;;
        --model=*)
            model=`echo $i | sed "s/${PATTERN}//"`;;
        --conda_env_name=*)
            conda_env_name=`echo $i | sed "s/${PATTERN}//"`;;
        --conda_env_mode=*)
            conda_env_mode=`echo $i | sed "s/${PATTERN}//"`;;
        --log_level=*)
            log_level=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done


# ------------------------------------- Environment -------------------------------------

function set_TF_env {
    export KMP_BLOCKTIME=1
    export KMP_AFFINITY=granularity=fine,verbose,compact,1,0
    export TF_MKL_OPTIMIZE_PRIMITIVE_MEMUSE=false

    export PATH=${HOME}/miniconda3/bin/:$PATH
    echo "Activating ${conda_env_name} env"
    source activate ${conda_env_name}

    tf_version=$(python -c "import tensorflow as tf; print(tf.__version__)")
    echo "tf_version: \"${tf_version}\""
    if [[ "${tf_version}" = "2.5.0" ]]; then
        # default use block format
        export TF_ENABLE_MKL_NATIVE_FORMAT=0
        echo "export TF_ENABLE_MKL_NATIVE_FORMAT=0 ..."
    fi
    intel_tf=$(pip list | grep 'tensorflow' | grep -c 'intel')
    if [[ "${tf_version}" = "2.6.1" ]] || [[ "${tf_version}" = "2.6.2" ]] || [[ "${intel_tf}" = "0" ]]; then
        # default use block format
        export TF_ENABLE_ONEDNN_OPTS=1
        echo "export TF_ENABLE_ONEDNN_OPTS=1 ..."
    fi
}

function set_MXNet_env {
    export KMP_BLOCKTIME=1
    export KMP_AFFINITY=granularity=fine,verbose,compact,1,0
    export OMP_NUM_THREADS=28

    export PATH=${HOME}/miniconda3/bin/:$PATH
    echo "Activating ${conda_env_name} env"
    source activate ${conda_env_name}
}

function set_PT_env {
    export OMP_NUM_THREADS=28

    if [[ "${model}" = "dlrm"* ]]; then
      export PATH=${HOME}/anaconda3/bin/:$PATH
      export https_proxy=http://child-prc.intel.com:913
      export http_proxy=http://child-prc.intel.com:913
    else
      export PATH=${HOME}/miniconda3/bin/:$PATH
    fi

    echo "Activating ${conda_env_name} env"
    source activate ${conda_env_name}
    if [ "${conda_env_mode}" == "conda" ];then
        pip install opencv-python
    fi
}

function set_ONNXRT_env {
    export KMP_AFFINITY=granularity=fine,noduplicates,compact,1,0
    export OMP_NUM_THREADS=28
    export PATH=${HOME}/miniconda3/bin/:$PATH
    echo "Activating ${conda_env_name} env"
    source activate ${conda_env_name}
    if [ "${conda_env_mode}" == "conda" ];then
        pip install opencv-python
    fi
}

function set_ENGINE_env {
    export PATH=${HOME}/miniconda3/bin/:$PATH
    echo "Activating ${conda_env_name} env"
    source activate ${conda_env_name}
}

function set_ENGINE_env {
    export PATH=${HOME}/miniconda3/bin/:$PATH
    echo "Activating ${conda_env_name} env"
    source activate ${conda_env_name}
}

function set_environment {
    case "${framework}" in
        tensorflow)
            set_TF_env;;
        mxnet)
            set_MXNet_env;;
        pytorch)
            set_PT_env;;
        onnxrt)
            set_ONNXRT_env;;
        baremetal)
            set_ENGINE_env;;
        *)
            echo "Framework ${framework} not recognized."; exit 1;;
    esac

    if [[ $(pip list | grep 'neural-compressor')  ]]; then
        echo "found nueral-compressor installed by pypi"
        return 0
    fi
    if [[ $(conda list | grep 'neural-compressor') ]]; then
        echo "found nueral-compressor installed by conda"
        return 0
    fi
    cd ${WORKSPACE}
    echo "Install neural-compressor binary..."
    n=0
    until [ "$n" -ge 5 ]
    do
        if [ "${conda_env_mode}" == "conda" ];then
            cd ${WORKSPACE}/lpot-models
            lpot_bz2_path="$(find ${WORKSPACE} -name neural-compressor*.tar.* |tail -1)"
            lpot_bz2_file="$(basename ${lpot_bz2_path})"
            lpot_version="$(echo ${lpot_bz2_file} |sed 's/\.tar\..*//' |awk -F '-' '{print $3}')"
            lpot_build="$(echo ${lpot_bz2_file} |sed 's/\.tar\..*//' |awk -F '-' '{print $4}')"
            sed -i "s+LPOT_BZ2_FILE+${lpot_bz2_file}+g" ${WORKSPACE}/lpot-validation/config/conda/noarch/repodata.json
            sed -i "s+LPOT_VERSION+${lpot_version}+g" ${WORKSPACE}/lpot-validation/config/conda/noarch/repodata.json
            sed -i "s+LPOT_BUILD+${lpot_build}+g" ${WORKSPACE}/lpot-validation/config/conda/noarch/repodata.json
            cp ${lpot_bz2_path} ${WORKSPACE}/lpot-validation/config/conda/noarch/
            pip uninstall neural-compressor -y || true
            conda install neural-compressor-conda -c file:/${WORKSPACE}/lpot-validation/config/conda -c conda-forge -c intel -y && break
        elif [ "${conda_env_mode}" == "source" ];then
            cd ${WORKSPACE}/lpot-models
            git submodule update --init --recursive
            pip install -r requirements.txt
            python setup.py clean || true
            python setup.py install && break
            cd -
        else
            pip install neural_compressor*.whl && break
        fi
        n=$((n+1))
        sleep 5
    done
    echo "Checking lpot..."
    if [[ "${conda_env_mode}" == "conda" ]]; then
        #python_version=$(python --version | grep -Po [0-9]+.[0-9]+)
        if [[ ! $(pip list | grep opencv-python) ]]; then
            pip install opencv-python    
        fi
        if [[ ! $(conda list | grep ffmpeg) ]]; then
            conda install ffmpeg -c conda-forge -y
        fi
        cp ${HOME}/miniconda3/envs/${conda_env_name}/lib/libopenh264.so.6 ${HOME}/miniconda3/envs/${conda_env_name}/lib/libopenh264.so.5
        conda list --show-channel-urls
    else
        pip list
    fi

    if [[ "${log_level}" != "" ]] && [[ "${log_level}" != "default" ]]; then
        export LOGLEVEL=${log_level}
    fi
}
