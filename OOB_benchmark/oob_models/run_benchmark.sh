#!/bin/bash
set -x

function main {
  init_params "$@"
  run_benchmark

}

# init params
function init_params {
  iters=100
  for var in "$@"
  do
    case $var in
      --topology=*)
          topology=$(echo $var |cut -f2 -d=)
      ;;
      --input_model=*)
          input_model=$(echo $var |cut -f2 -d=)
      ;;
      --iters=*)
          iters=$(echo ${var} |cut -f2 -d=)
      ;;
      --num_warmup=*)
            num_warmup=$(echo ${var} |cut -f2 -d=)
      ;;
      *)
          echo "Error: No such parameter: ${var}"
          exit 1
      ;;
    esac
  done

}

models_need_name=(
efficientnet-b0
efficientnet-b0_auto_aug
efficientnet-b5
efficientnet-b7_auto_aug
vggvox
aipg-vdcnn
arttrack-coco-multi
arttrack-mpii-single
deepvariant_wgs
east_resnet_v1_50
facenet-20180408-102900
handwritten-score-recognition-0003
license-plate-recognition-barrier-0007
optical_character_recognition-text_recognition-tf
PRNet
Resnetv2_200
text-recognition-0012
Hierarchical_LSTM
icnet-camvid-ava-0001
icnet-camvid-ava-sparse-30-0001
icnet-camvid-ava-sparse-60-0001
deeplabv3
ssd-resnet34_300x300
)

models_need_disable_optimize=(
efficientnet-b0
efficientnet-b0_auto_aug
efficientnet-b5
efficientnet-b7_auto_aug
vggvox
)

# run_tuning
function run_benchmark {
    mode_cmd=" --num_iter ${iters} --benchmark"
    extra_cmd="--num_warmup ${num_warmup}"
    if [[ "${models_need_name[@]}"  =~ "${topology}" ]]; then
      echo "$topology need model name!"
      extra_cmd="--num_warmup ${num_warmup} --model_name "${topology}
    fi

    if [[ "${models_need_disable_optimize[@]}"  =~ "${topology}" ]]; then
      echo "$topology need to disable optimize_for_inference!"
      extra_cmd="--num_warmup ${num_warmup} --disable_optimize --model_name "${topology}
    fi

    python tf_benchmark.py \
            --model_path ${input_model} \
            --num_iter ${iters} \
            ${extra_cmd} \
            ${mode_cmd}
}

main "$@"
