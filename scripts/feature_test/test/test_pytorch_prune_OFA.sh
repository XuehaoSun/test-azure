#!/bin/bash -x
set -eo pipefail

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
    # pip config set global.index-url https://pypi.douban.com/simple/

    create_conda_env
    lpot_install

    # Run Pytorch Prune test
    cd ${WORKSPACE}/lpot-models/examples/pytorch/nlp/huggingface_models/question-answering/optimization_pipeline/prune_once_for_all/fx
    n=0
    until [ "$n" -ge 5 ]
    do
        python -m pip install -r requirements.txt && break
        n=$((n+1))
        sleep 5
    done

    pip list
    #stage 1
    [[ ! -d ${WORKSPACE}/stage1_output ]] && mkdir -p ${WORKSPACE}/stage1_output
    python run_qa_no_trainer_pruneOFA.py --dataset_name squad \
      --model_name_or_path Intel/bert-base-uncased-sparse-90-unstructured-pruneofa \
      --teacher_model_name_or_path csarron/bert-base-uncased-squad-v1 \
      --do_prune --do_distillation --max_seq_length 384 --batch_size 12 \
      --learning_rate 1.5e-4 --do_eval --num_train_epochs 3 \
      --output_dir ${WORKSPACE}/stage1_output --loss_weights 0 1 \
      --max_train_samples 24 --max_eval_samples 24 \
      --temperature 2 --seed 5143 --pad_to_max_length --run_teacher_logits  2>&1 | tee ${WORKSPACE}/pytorch_prune_OFA_stage1.log
    #stage 2
    [[ ! -d ${WORKSPACE}/stage2_output ]] && mkdir -p ${WORKSPACE}/stage2_output
    python run_qa_no_trainer_pruneOFA.py --dataset_name squad \
      --model_name_or_path Intel/bert-base-uncased-sparse-90-unstructured-pruneofa \
      --teacher_model_name_or_path csarron/bert-base-uncased-squad-v1 \
      --do_prune --do_distillation --max_seq_length 384 --batch_size 12 \
      --learning_rate 1e-5 --do_eval --num_train_epochs 2 --do_quantization \
      --output_dir ${WORKSPACE}/stage2_output --loss_weights 0 1 \
      --max_train_samples 24 --max_eval_samples 24 \
      --temperature 2 --seed 5143 --pad_to_max_length  --run_teacher_logits \
      --resume ${WORKSPACE}/stage1_output/best_model.pt 2>&1 | tee ${WORKSPACE}/pytorch_prune_OFA_stage2.log

}

function create_conda_env {

    if [[ -z ${python_version} ]]; then
        python_version=3.7  # Set python 3.7 as default
    fi

    conda_env_name=pytorch_prune_OFA-py${python_version}

    conda_dir=$(dirname $(dirname $(which conda)))
    if [ -d ${conda_dir}/envs/${conda_env_name} ]; then
        rm -rf ${conda_dir}/envs/${conda_env_name}
    fi
    n=0
    until [ "$n" -ge 5 ]
    do
        conda create python=${python_version} -y -n ${conda_env_name}
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
    cd ${WORKSPACE}/lpot-models || return
}

function lpot_install {
    echo "Checking lpot..."
    python -V
    c_lpot=$(pip list | grep -c 'neural-compressor') || true  # Prevent from exiting when 'lpot' not found
    if [ ${c_lpot} != 0 ]; then
        pip uninstall neural-compressor -y
        pip list
    fi
    pip install ${WORKSPACE}/neural_compressor*.whl
    pip list
}

main
