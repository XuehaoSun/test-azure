#!/bin/bash
set -xe

PATTERN='[-a-zA-Z0-9_]*='
for i in "$@"
do
    case $i in
        --python_version=*)
            python_version=`echo $i | sed "s/${PATTERN}//"`;;
    esac
done

function main {
    # conda env
    create_conda_env
    # example execution
    cd ${WORKSPACE}/lpot-models/examples/optimization/tensorflow/huggingface/text-classification/pruning
    pip install -r requirements.txt
    [[ ! -d ./tmp/sst2_output ]] && mkdir -p ./tmp/sst2_output
    ## pruning stage
    python run_glue.py \
        --model_name_or_path distilbert-base-uncased-finetuned-sst-2-english \
        --task_name sst2 \
        --target_sparsity_ratio 0.1 \
        --prune \
        --do_train \
        --do_eval \
        --overwrite_output_dir \
        --per_device_train_batch_size 64 \
        --per_device_eval_batch_size 64 \
        --output_dir ./tmp/sst2_output 2>&1 | tee ${WORKSPACE}/tensorflow_pruning.log

        ## benchmark stage
        bash run_benchmark.sh --topology=distilbert_base_sst2 --mode=accuracy 2>&1 | tee -a ${WORKSPACE}/tensorflow_pruning.log
        bash run_benchmark.sh --topology=distilbert_base_sst2 --mode=benchmark 2>&1 | tee -a ${WORKSPACE}/tensorflow_pruning.log
}

function create_conda_env {
    conda_env_name="tensorflow_pruning"

    if [ -f "${HOME}/miniconda3/etc/profile.d/conda.sh" ]; then
        . "${HOME}/miniconda3/etc/profile.d/conda.sh"
    else
        [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
        [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
    fi
    conda remove --all -y -n ${conda_env_name}
    conda create python=${python_version} -y -n ${conda_env_name}
    conda activate ${conda_env_name}
    cd ${WORKSPACE}/lpot-models
    #pip install datasets==2.4.0
    pip install -r requirements.txt
    pip install -U pip

    # install inc
    pip install ${WORKSPACE}/neural_compressor*.whl
    # install nlp-toolkit
    pip install ${WORKSPACE}/intel_extension_for_transformers-*.whl
    pip list

}

main