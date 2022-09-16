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
    cd ${WORKSPACE}/lpot-models/examples/pytorch/nlp/huggingface_models/text-classification/optimization_pipeline/distillation_for_quantization/fx
    pip install torch==1.12.1 torchvision==0.13.1
    pip install -r requirements.txt
    [[ ! -d ${WORKSPACE}/saved_qqp ]] && mkdir -p ${WORKSPACE}/saved_qqp
    python run_glue_no_trainer.py --task_name qqp --model_name_or_path yoshitomo-matsubara/bert-base-uncased-qqp  \
    --teacher_model_name_or_path yoshitomo-matsubara/bert-base-uncased-qqp  --batch_size 32 \
    --do_eval --do_quantization --do_distillation --pad_to_max_length --num_train_epochs 1 \
    --output_dir ${WORKSPACE}/saved_qqp --max_train_steps 10 2>&1 | tee ${WORKSPACE}/pytorch_distillation_for_quantizaton.log
}

function create_conda_env {
    conda_env_name="distillation_for_quantization"

    if [ -f "${HOME}/miniconda3/etc/profile.d/conda.sh" ]; then
        . "${HOME}/miniconda3/etc/profile.d/conda.sh"
    else
        export PATH="${HOME}/miniconda3/bin:$PATH"
    fi
    conda remove --all -y -n ${conda_env_name}
    conda create python=${python_version} -y -n ${conda_env_name}
    conda activate ${conda_env_name}
    cd ${WORKSPACE}/lpot-models
    pip install -r requirements.txt
    pip install -U pip

    # install inc
    pip install ${WORKSPACE}/neural_compressor*.whl

    pip list

}

main