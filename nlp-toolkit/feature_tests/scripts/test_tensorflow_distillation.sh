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
    cd ${WORKSPACE}/lpot-models/examples/optimization/tensorflow/huggingface/text-classification/distillation
    pip install -r requirements.txt
    [[ ! -d ./tmp/sst2_output ]] && mkdir -p ./tmp/sst2_output
    python -u ./run_glue.py \
        --model_name_or_path distilbert-base-uncased \
        --teacher_model_name_or_path distilbert-base-uncased-finetuned-sst-2-english \
        --task_name sst2 \
        --temperature 1.0 \
        --distill \
        --loss_types CE CE \
        --layer_mappings classifier classifier \
        --do_eval \
        --do_train \
        --per_device_eval_batch_size 64 \
        --overwrite_output_dir \
        --output_dir ./tmp/sst2_output 2>&1 | tee ${WORKSPACE}/tensorflow_distillation.log

        ## benchmark stage
        bash run_benchmark.sh --topology=distilbert-base-uncased --mode=accuracy 2>&1 | tee -a ${WORKSPACE}/tensorflow_distillation.log
        bash run_benchmark.sh --topology=distilbert-base-uncased --mode=benchmark 2>&1 | tee -a ${WORKSPACE}/tensorflow_distillation.log
}

function create_conda_env {
    conda_env_name="tensorflow_distillation"

    if [ -f "${HOME}/miniconda3/etc/profile.d/conda.sh" ]; then
        . "${HOME}/miniconda3/etc/profile.d/conda.sh"
    else
        export PATH="${HOME}/miniconda3/bin:$PATH"
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
    pip install ${WORKSPACE}/intel_extension_for_transformers*.whl
    pip list

}

main