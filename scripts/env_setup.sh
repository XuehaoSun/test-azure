#!/bin/bash

set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='
if [ $# != "3" ] ; then 
    echo 'ERROR:'
    echo "Expected 3 parameters got $#"
    printf 'Please use following parameters:
    --framework=<framework name>
    --model=<model name>
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

    if [[ ${model} = 'bert'* ]]; then
      export PATH=${HOME}/miniconda3/bin/:$PATH
      source activate pytorch-bert-1.6
    elif [[ ${model} = 'dlrm' ]]; then
      export PATH=${HOME}/anaconda3/bin/:$PATH
      source activate pytorch3
    else
      export PATH=${HOME}/miniconda3/bin/:$PATH
      source activate ${conda_env_name}
    fi
}

function set_environment {
    case "${framework}" in
        tensorflow)
            set_TF_env;;
        mxnet)
            set_MXNet_env;;
        pytorch)
            set_PT_env;;
        *)
            echo "Framework ${framework} not recognized."; exit 1;;
    esac

    echo "Checking ilit..."
    python -V
    pip list
    c_ilit=$(pip list | grep -c 'ilit') || true  # Prevent from exiting when 'ilit' not found
    if [ ${c_ilit} != 0 ]; then
        pip uninstall ilit -y
        pip list
    fi

    if [ ! -d ${WORKSPACE}/ilit-models ]; then
        echo "\"ilit-model\" not found. Exiting..."
        exit 1
    fi
    cd ${WORKSPACE}/ilit-models
    python setup.py install
    pip list

    export LOGLEVEL=DEBUG
    echo "HOSTNAME IS ${HOSTNAME}"
}


