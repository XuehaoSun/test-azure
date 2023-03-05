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
    cd ${WORKSPACE}/lpot-models/examples/optimization/pytorch/huggingface/question-answering/pruning/longformer_triviaqa/
    n=0
    until [ "$n" -ge 5 ]
    do
        python -m pip install -r requirements.txt && break
        n=$((n+1))
        sleep 5
    done
    pip install nltk
    pip list
    #bash ./scripts/download_data_and_convert.sh
    train_file=/tf_dataset2/models/nlp_toolkit/longformer/squad-wikipedia-train-4096.json
    validation_file=/tf_dataset2/models/nlp_toolkit/longformer/squad-wikipedia-dev-4096.json
    teacher_model=/tf_dataset2/models/nlp_toolkit/longformer/longformer-base-4096
    cp -r $train_file ./
    cp -r $validation_file ./
    cp -r $teacher_model ./
    python run_qa_no_trainer.py \
        --model_name_or_path "./longformer-base-4096" \
        --do_train \
        --do_eval \
        --train_file "./squad-wikipedia-train-4096.json" \
        --validation_file "./squad-wikipedia-dev-4096.json" \
        --cache_dir ./tmp_cached \
        --max_seq_length 4096 \
        --doc_stride -1 \
        --per_device_train_batch_size 1 \
        --gradient_accumulation_steps 8 \
        --per_device_eval_batch_size 1 \
        --num_warmup_steps 10 \
        --do_prune \
        --target_sparsity 0.8 \
        --pruning_scope "global" \
        --pruning_pattern "4x1" \
        --pruning_frequency 1000 \
        --cooldown_epochs 1 \
        --learning_rate 1e-4 \
        --num_train_epochs 3 \
        --weight_decay  0.01 \
        --output_dir longformer-base-4096-pruned-global-sparse80 \
        --teacher_model_name_or_path $teacher_model \
        --distill_loss_weight 3 \
        --max_train_samples 128 \
        --max_eval_samples 128 \
        --max_predict_samples 128  2>&1 | tee ${WORKSPACE}/pytorch_pruning_longformer.log

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
