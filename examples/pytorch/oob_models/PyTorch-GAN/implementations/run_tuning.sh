#!/bin/bash
set -x

function main {

  init_params "$@"
  copy_dataset
  run_tuning

}

# init params
function init_params {
  tuned_checkpoint=saved_results
  for var in "$@"
  do
    case $var in
      --topology=*)
          topology=$(echo $var |cut -f2 -d=)
      ;;
      --dataset_location=*)
          dataset_location=$(echo $var |cut -f2 -d=)
      ;;
      --input_model=*)
          input_model=$(echo $var |cut -f2 -d=)
      ;;
      --output_model=*)
          tuned_checkpoint=$(echo $var |cut -f2 -d=)
      ;;
      *)
          echo "Error: No such parameter: ${var}"
          exit 1
      ;;
    esac
  done

}

#copy dataset
function copy_dataset {
   cd ../..
   mkdir data
   cp -r  /tf_dataset2/pytorch_oobmodels/mnist ./data
   cd PyTorch-GAN/implementations
}

# run_tuning
function run_tuning {
    if [ "${topology}" = "srgan" ];then
        python srgan/srgan.py  --batch_size 1 --inference \
        --num-iterations 100 --latent_dim 16 --tune
    else
        python ${topology}/${topology}.py --batch_size 1 --inference \
        --num-iterations 100 --tune
    fi
}

main "$@"
