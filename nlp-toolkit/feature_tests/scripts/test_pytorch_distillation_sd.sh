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
    [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
    [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH

    create_conda_env
    lpot_install

    # Run Pytorch Prune test
    cd ${WORKSPACE}/lpot-models/examples/optimization/pytorch/huggingface/textual_inversion/distillation_for_quantization
    n=0
    until [ "$n" -ge 5 ]
    do
        python -m pip install -r requirements.txt && break
        n=$((n+1))
        sleep 5
    done
    pip list
    FP32_MODEL_NAME="/tf_dataset2/models/nlp_toolkit/dicoo_model/"
    INT8_MODEL_NAME="int8_model"
    DATA_DIR="${WORKSPACE}/lpot-models/examples/optimization/pytorch/huggingface/textual_inversion/dicoo"
    echo "tune model"
    python textual_inversion.py \
        --pretrained_model_name_or_path=$FP32_MODEL_NAME \
        --train_data_dir=$DATA_DIR \
        --use_ema --learnable_property="object" \
        --placeholder_token="<dicoo>" --initializer_token="toy" \
        --resolution=512 \
        --train_batch_size=1 \
        --gradient_accumulation_steps=4 \
        --max_train_steps=300 \
        --learning_rate=5.0e-04 --max_grad_norm=3 \
        --lr_scheduler="constant" \
        --lr_warmup_steps=0 \
        --output_dir=${INT8_MODEL_NAME} \
        --do_quantization --do_distillation --verify_loading 2>&1 | tee ${WORKSPACE}/pytorch_distillation_sd.log
    echo "inference"
    python text2images.py \
        --pretrained_model_name_or_path=$INT8_MODEL_NAME \
        --caption "a lovely <dicoo> in red dress and hat, in the snowly and brightly night, with many brighly buildings." \
        --images_num 1


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
