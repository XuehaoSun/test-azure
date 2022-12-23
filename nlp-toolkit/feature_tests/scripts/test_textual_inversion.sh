#!/bin/bash
set -xe


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

    create_conda_env
    lpot_install

    # Run Pytorch Prune test
    cd ${WORKSPACE}/lpot-models/examples/optimization/pytorch/huggingface/textual_inversion
    n=0
    until [ "$n" -ge 5 ]
    do
        python -m pip install -r requirements.txt && break
        n=$((n+1))
        sleep 5
    done
    pip list
    export MODEL_NAME="CompVis/stable-diffusion-v1-4"
    export DATA_DIR="./dicoo"

    # add use_bf16
    python textual_inversion_ipex.py \
      --pretrained_model_name_or_path=$MODEL_NAME \
      --train_data_dir=$DATA_DIR \
      --learnable_property="object" \
      --placeholder_token="<dicoo>" --initializer_token="toy" \
      --resolution=512 \
      --train_batch_size=1 \
      --gradient_accumulation_steps=4 \
      --use_bf16 \
      --max_train_steps=10 \
      --learning_rate=5.0e-04 --scale_lr \
      --lr_scheduler="constant" \
      --lr_warmup_steps=0 \
      --output_dir="dicoo_model"  2>&1 | tee ${WORKSPACE}/textual_inversion.log

}

function create_conda_env {

    if [[ -z ${python_version} ]]; then
        python_version=3.7  # Set python 3.7 as default
    fi

    conda_env_name=pytorch_distillation_py${python_version}

    conda_dir=$(dirname $(dirname $(which conda)))
    if [ -d ${conda_dir}/envs/${conda_env_name} ]; then
        rm -rf ${conda_dir}/envs/${conda_env_name}
    fi
    n=0
    until [ "$n" -ge 5 ]
    do
        conda create python=${python_version} -y -n ${conda_env_name} || true
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
    pip install protobuf==3.20.1
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

    # install inc
    pip install ${WORKSPACE}/neural_compressor*.whl
    # install nlp-toolkit
    pip install ${WORKSPACE}/intel_extension_for_transformers-*.whl
    pip list
}

main



