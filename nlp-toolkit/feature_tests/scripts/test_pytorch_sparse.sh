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
    cd ${WORKSPACE}/lpot-models/examples/optimization/pytorch/huggingface/pytorch_pruner
    n=0
    until [ "$n" -ge 5 ]
    do
        python -m pip install -r requirements.txt && break
        n=$((n+1))
        sleep 5
    done
    pip list
    # training dense model
    echo "train dense bert mini model"
    python ./run_glue_no_trainer.py \
    --model_name_or_path "prajjwal1/bert-mini" \
    --pruning_config "./bert_mini_mrpc_4x1.yaml" \
    --task_name "mrpc" \
    --per_device_train_batch_size "8" \
    --per_device_eval_batch_size "16" \
    --num_warmup_steps "10" \
    --learning_rate "5e-5" \
    --num_train_epochs 5 \
    --output_dir "./output_bert-mini" 2>&1 | tee ${WORKSPACE}/pytorch_sparse.log

    cp ./output_bert-mini/epoch4/config.json ./output_bert-mini/
    cp ./output_bert-mini/epoch4/pytorch_model.bin ./output_bert-mini/
    ## pruning
    echo "sparse pruner"
    python ./run_glue_no_trainer.py \
        --model_name_or_path "./output_bert-mini" \
        --pruning_config "./bert_mini_mrpc_4x1.yaml" \
        --task_name "mrpc" \
        --per_device_train_batch_size "16" \
        --per_device_eval_batch_size "16" \
        --num_warmup_steps "1000" \
        --do_prune \
        --cooldown_epochs 5 \
        --learning_rate "4.5e-4" \
        --num_train_epochs 10 \
        --weight_decay  "1e-7" \
        --output_dir "pruned_squad_bert-mini" \
        --distill_loss_weight "4.5" 2>&1 | tee -a ${WORKSPACE}/pytorch_sparse.log

}

function create_conda_env {

    if [[ -z ${python_version} ]]; then
        python_version=3.7  # Set python 3.7 as default
    fi

    conda_env_name=pytorch_pruning_py${python_version}

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



