#!/bin/bash
set -x

function main {
    init_params "$@"
    set_environment
    if [ "${model}" = "resnet18" ] || [ "${model}" = "resnet50" ] || [ "${model}" = "resnet101" ];then
      model_src_dir=${WORKSPACE}/ilit-models/examples/${framework}/resnet50
      init_cnn_cmd
    elif [[ ${model} = 'bert'* ]]; then
      model_src_dir=${WORKSPACE}/ilit-models/examples/${framework}/bert
      init_bert_cmd
    elif [ "${model}" = "dlrm" ]; then
      model_src_dir=${WORKSPACE}/ilit-models/examples/${framework}/dlrm
      init_dlrm_cmd
    fi

    if [ "${model_src_dir}" != "" ];then
        cd ${model_src_dir}
    fi
    git remote -v
    git branch
    git show |head -5

    generate_core
}

# init params
function init_params {
    framework='pytorch'
    model='resnet50'

    for var in "$@"
    do
        case $var in
            --framework=*)
                framework=$(echo $var |cut -f2 -d=)
            ;;
            --model=*)
                model=$(echo $var |cut -f2 -d=)
            ;;
            --conda_env_name=*)
                conda_env_name=$(echo $var |cut -f2 -d=)
            ;;
            *)
                echo "Error: No such parameter: ${var}"
                exit 1
            ;;
        esac
    done
}

function init_dlrm_cmd {

    data_path=/mnt/local_disk3/dataset/dlrm/dlrm/input
    fp32_load_path=/mnt/local_disk3/dataset/dlrm/dlrm_weight/terabyte_mlperf.pt
    cmd="python -u dlrm_s_pytorch_tune.py \
        --arch-sparse-feature-size=128 \
        --arch-mlp-bot="13-512-256-128" \
        --arch-mlp-top="1024-1024-512-256-1" \
        --max-ind-range=40000000 \
        --data-generation=dataset \
        --data-set=terabyte \
        --raw-data-file=${data_path}/day \
        --processed-data-file=${data_path}/terabyte_processed.npz \
        --loss-function=bce  \
        --round-targets=True  \
        --learning-rate=1.0 \
        --mini-batch-size=2048 \
        --print-freq=2048 \
        --print-time \
        --test-freq=102400 \
        --test-mini-batch-size=16384 \
        --test-num-workers=16 \
        --memory-map \
        --mlperf-logging \
        --mlperf-auc-threshold=0.8025 \
        --mlperf-bin-loader \
        --mlperf-bin-shuffle \
        --load-model=${fp32_load_path}"

}

# init_cnn_cmd
function init_cnn_cmd {
    dataset=/tf_dataset/pytorch/ImageNet/raw
    if [ "${model}" = "resnet18" ] || [ "${model}" = "resnet50" ] || [ "${model}" = "resnet101" ];then
        cmd=" python main.py \
            -a ${model} \
            --pretrained \
            --data ${dataset}"
    fi
}


function init_bert_cmd {

  GLUE_DIR=/tf_dataset/pytorch/glue_data
  model_size=$(echo ${model} | awk -F '_' '{print $2}')
  TASK_NAME=$(echo ${model} | awk -F '_' '{print $3}')
  SCRIPTS=examples/run_glue_tune.py
  MAX_SEQ_LENGTH=128
  if [[ "$TASK_NAME" == "SQuAD" ]]; then
    MAX_SEQ_LENGTH=384
    SCRIPTS=examples/run_squad_tune.py
  fi

  if [[ "$model_size" == "base" ]]; then
      BATCH_SIZE=8
      OUTPUT=${GLUE_DIR}/base_weights/${TASK_NAME}_output
  else
      BATCH_SIZE=16
      OUTPUT=${GLUE_DIR}/weights/${TASK_NAME}_output
  fi

  cmd="python $SCRIPTS --model_type bert \
      --model_name_or_path ${OUTPUT} \
      --task_name ${TASK_NAME} \
      --do_eval \
      --do_lower_case \
      --data_dir $GLUE_DIR/$TASK_NAME/ \
      --max_seq_length $MAX_SEQ_LENGTH \
      --per_gpu_eval_batch_size $BATCH_SIZE \
      --no_cuda \
      --output_dir $OUTPUT"
}

# environment
function set_environment {
    export OMP_NUM_THREADS=28

    if [[ ${model} = 'bert'* ]]; then
      export PATH=${HOME}/miniconda3/bin/:$PATH
      source activate pytorch-bert-1.6
    elif [ ${model} = 'dlrm' ]; then
      export PATH=${HOME}/anaconda3/bin/:$PATH
      source activate pytorch3
    else
      export PATH=${HOME}/miniconda3/bin/:$PATH
      source activate ${conda_env_name}
    fi

    export PYTHONPATH=${PYTHONPATH}:${WORKSPACE}/ilit-models/
    python -V
    pip list
}

# run
function generate_core {
      # run tunning
    excute_cmd_file="/tmp/${framework}-${model}-run-$(date +'%s').sh"
    rm -f ${excute_cmd_file}
    run_cmd="numactl -l -C 0-27,56-83 ${cmd} --tune"
    printf "${run_cmd}" |tee -a ${excute_cmd_file}
    bash ${excute_cmd_file}

    # run fp32 benchmark
    run_cmd="numactl -l -C 0-27,56-83 ${cmd} --fp32_benchmark"
    eval "${run_cmd}"

}

main "$@"
