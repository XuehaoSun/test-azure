#!/bin/bash

set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='
if [ $# != "4" ] ; then
    echo 'ERROR:'
    echo "Expected 4 parameters got $#"
    printf 'Please use following parameters:
    --framework=<framework name>
    --model=<model name>
    --model_src_dir=<path to model tuning script>
    --conda_env_name=<conda environment name>
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
        --model_src_dir=*)
            model_src_dir=`echo $i | sed "s/${PATTERN}//"`;;
        --conda_env_name=*)
            conda_env_name=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done


# ------------------------------------- Environment -------------------------------------

function set_TF_env {
    export KMP_BLOCKTIME=1
    export KMP_AFFINITY=granularity=fine,verbose,compact,1,0
    export TF_MKL_OPTIMIZE_PRIMITIVE_MEMUSE=false
    # default use block format
    export TF_ENABLE_MKL_NATIVE_FORMAT=0

    export PATH=${HOME}/miniconda3/bin/:$PATH
    source activate ${conda_env_name}
}

function set_MXNet_env {
    export KMP_BLOCKTIME=1
    export KMP_AFFINITY=granularity=fine,verbose,compact,1,0
    export OMP_NUM_THREADS=28

    export PATH=${HOME}/miniconda3/bin/:$PATH
    source activate ${conda_env_name}
}

function set_PT_env {
    export OMP_NUM_THREADS=28

    if [[ ${model_src_dir} = *'language_translation'* ]]; then
      export PATH=${HOME}/miniconda3/bin/:$PATH
      echo "Activating pytorch-bert-1.6 env"
      source activate pytorch-bert-1.6
    elif [[ "${model}" = "dlrm"* ]]; then
      export PATH=${HOME}/anaconda3/bin/:$PATH
      echo "Activating ${conda_env_name} env"
      source activate ${conda_env_name}
      export https_proxy=http://child-prc.intel.com:913
      export http_proxy=http://child-prc.intel.com:913
    elif [[ ${model} = *'_ipex' ]]; then
      export PATH=${HOME}/miniconda3/bin/:$PATH
      echo "Activating pt-ipex-3.6 env"
      source activate pt-ipex-3.6
    else
      export PATH=${HOME}/miniconda3/bin/:$PATH
      echo "Activating ${conda_env_name} env"
      source activate ${conda_env_name}
    fi
}

function set_ONNXRT_env {
    export KMP_AFFINITY=granularity=fine,noduplicates,compact,1,0
    export OMP_NUM_THREADS=28
    export PATH=${HOME}/miniconda3/bin/:$PATH
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
        *)
            echo "Framework ${framework} not recognized."; exit 1;;
    esac

    echo "Real conda environment..."
    conda info --env

    echo "Checking pip list..."
    python -V
    pip list
    c_lpot=$(pip list | grep -c 'lpot') || true  # Prevent from exiting when 'lpot' not found
    if [ ${c_lpot} != 0 ]; then
        pip uninstall lpot -y
        pip list
    fi

    cd ${WORKSPACE}
    echo "Install lpot binary..."
    n=0
    until [ "$n" -ge 5 ]
    do
        pip install lpot*.whl && break
        n=$((n+1))
        sleep 5
    done
    echo "Checking lpot..."
    pip list

    if [ ! -d ${WORKSPACE}/lpot-models ]; then
        echo "\"lpot-model\" not found. Exiting..."
        exit 1
    fi
    cd ${WORKSPACE}/lpot-models

    export LOGLEVEL=DEBUG
}

function get_conda_env_name {
    if [ ${framework} != "pytorch" ]; then
        echo ${conda_env_name}
        exit
    fi

    if [[ ${model_src_dir} = *'language_translation'* ]]; then
        echo "pytorch-bert-1.6"
        exit
    elif [[ ${model} = *'_ipex' ]]; then
        export PATH=${HOME}/miniconda3/bin/:$PATH
        echo "pt-ipex-3.6"
        exit
    fi
    echo ${conda_env_name}
}
