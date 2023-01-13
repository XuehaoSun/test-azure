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
    cd ${WORKSPACE}/lpot-models/examples/optimization/pytorch/huggingface/question-answering/dynamic
    n=0
    until [ "$n" -ge 5 ]
    do
        python -m pip install -r requirements.txt && break
        n=$((n+1))
        sleep 5
    done
    pip list
    echo "quantization start"
    python run_qa.py \
        --model_name_or_path "sguskin/dynamic-minilmv2-L6-H384-squad1.1" \
        --dataset_name squad \
        --quantization_approach PostTrainingStatic \
        --do_eval \
        --do_train \
        --tune \
        --output_dir output/quantized-dynamic-minilmv \
        --overwrite_cache \
        --per_device_eval_batch_size 32 \
        --overwrite_output_dir 2>&1 | tee ${WORKSPACE}/pytorch_dynamic.log
    
    echo "length adaptive"

    python run_qa.py \
        --model_name_or_path output/quantized-dynamic-minilmv \
        --dataset_name squad \
        --do_eval \
        --accuracy_only \
        --int8 \
        --output_dir output/quantized-dynamic-minilmv \
        --overwrite_cache \
        --per_device_eval_batch_size 32 \
        --length_config "(315, 251, 242, 159, 142, 33)" 2>&1 | tee -a ${WORKSPACE}/pytorch_dynamic.log


}

function create_conda_env {

    if [[ -z ${python_version} ]]; then
        python_version=3.7  # Set python 3.7 as default
    fi

    conda_env_name=pytorch_dynamic_py${python_version}

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



