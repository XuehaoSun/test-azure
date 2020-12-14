#!/bin/bash

set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='
if [ $# != "10" ] ; then
    echo 'ERROR:'
    echo "Expected 9 parameters got $#"
    printf 'Please use following parameters:
    --framework=<framework name>
    --model=<model name>
    --model_src_dir=<path to model tuning script>
    --dataset_location=<path to dataset>
    --input_model=<path to input model>
    --precision=<kind of data precision>
    --mode=<benchmark mode>
    --batch_size=<batch_size for accuracy and throughput>
    --conda_env_name=<conda environment name>
    --yaml=<path to lpot yaml configuration>
    '
    exit 1
fi

for i in "$@"
do
    case $i in
        --framework=*)
            framework=`echo $i | sed "s/${PATTERN}//"`;;
        --model=*)
            model=`echo $i | sed "s/${PATTERN}//"`;;
        --model_src_dir=*)
            model_src_dir=`echo $i | sed "s/${PATTERN}//"`;;
        --dataset_location=*)
            dataset_location=`echo $i | sed "s/${PATTERN}//"`;;
        --input_model=*)
            input_model=`echo $i | sed "s/${PATTERN}//"`;;
        --precision=*)
            precision=`echo $i | sed "s/${PATTERN}//"`;;
        --mode=*)
            mode=`echo $i | sed "s/${PATTERN}//"`;;
        --batch_size=*)
            batch_size=`echo $i | sed "s/${PATTERN}//"`;;
        --conda_env_name=*)
            conda_env_name=`echo $i | sed "s/${PATTERN}//"`;;
        --yaml=*)
            yaml=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

# Run Benchmark
main() {

    # Import common functions
    source ${WORKSPACE}/lpot-validation/scripts/env_setup.sh --framework=${framework} --model=${model} --conda_env_name=${conda_env_name}

    echo -e "\nSetting environment..."
    set_environment

    if [ -d ${model_src_dir} ]; then
        cd ${model_src_dir}
        echo -e "\nWorking in $(pwd)..."
    else
        echo "[ERROR] model_src_dir \"${model_src_dir}\" not exists."
        exit 1
    fi

    echo -e "\nInstalling model requirements..."
    if [ -f "requirements.txt" ]; then
        sed -i '/lpot/d' requirements.txt
        python -m pip install -r requirements.txt
        pip list
    else
        echo "Not found requirements.txt file."
    fi

    echo -e "\nSet a modified yaml..."
    echo "${yaml}"
    cp "${yaml}" "benchmark.yaml"
    yaml="benchmark.yaml"
    echo "${yaml}"

    echo -e "\nGetting git information..."
    echo "$(git remote -v)"
    echo "$(git branch)"
    echo "$(git show | head -5)"

    q_model=${WORKSPACE}/${framework}-${model}-tune
    if [ ${framework} == "tensorflow" ]; then
        q_model="${q_model}.pb"
    elif [ ${framework} == "mxnet" ] && [[ ${model_src_dir} == *"object_detection" ]]; then
        q_model="${q_model}/${model}"
    elif [ ${framework} == "onnxrt" ]; then
        q_model="${q_model}.onnx"
    fi

    # ------ WORKAROUND FOR MXNET RESNET50V1 -----
    topology=${model}
    if [ "${model}" == "resnet50v1" ]; then
        topology="resnet50_v1"
    fi

    if [[ "${model}" == *"_qat" ]]; then
        topology="${model%_qat} "
    fi

    # pytorch int8 still use fp32 input_model
    if [ ${precision} == "int8" ] && [ ${framework} != "pytorch" ]; then
      input_model=${q_model}
    fi
    # set parameters for benchmark
    parameters="--topology=${topology} --dataset_location=${dataset_location} --input_model=${input_model}"

    # add flag for pytorch int8
    if [ ${framework} == "pytorch" ] && [ ${precision} == "int8" ]; then
      parameters="${parameters} --int8=true"
    fi

    echo -e "\nStart run function..."
    case ${mode} in
      accuracy)
        run_accuracy;;
      throughput)
        run_benchmark;;
      latency)
        run_benchmark;;
      *)
        echo "MODE ${mode} not recognized."; exit 1;;
    esac
}

function run_accuracy {
  parameters="${parameters} --mode=accuracy --batch_size=${batch_size}"

  # general yaml for new config format
  iters=-1
  config_new_yaml

  if [ -f "run_benchmark.sh" ]; then
        run_cmd="bash run_benchmark.sh ${parameters}"
  else
        echo "Not found run_benchmark file."
        exit 1
  fi

  logFile=${WORKSPACE}/${framework}_${model}_${precision}_${mode}.log
  echo "RUNCMD: $run_cmd " >& ${logFile}
  eval "${run_cmd}" >> ${logFile}
}

function run_benchmark {
  # define a low iteration list to save time
  # if latency ~ 500 ms , then set iter = 200. if latency ~ 1000 ms, then set iter = 100
  latency_high_500=("arttrack-coco-multi" "arttrack-mpii-single" "east_resnet_v1_50" \
  "DeepLab" "mask_rcnn_resnet50_atrous_coco")

  latency_high_1000=("efficientnet-b7_auto_aug" "i3d-flow" "i3d-rgb" "VNet" "icnet-camvid-ava-0001" \
  "icnet-camvid-ava-sparse-30-0001" "icnet-camvid-ava-sparse-60-0001" "dilation" \
  "faster_rcnn_inception_resnet_v2_atrous_coco" "faster_rcnn_nas_coco" "faster_rcnn_nas_lowproposals_coco" \
  "gmcnn-places2" "mask_rcnn_inception_resnet_v2_atrous_coco" "Transformer-LT" "mask_rcnn_resnet101_atrous_coco" \
  "person-vehicle-bike-detection-crossroad-yolov3-1024" "unet-3d-isensee_2017" "unet-3d-origin")

  # get cpu information for multi-instance
  ncores_per_socket=${ncores_per_socket:=$( lscpu | grep 'Core(s) per socket' | cut -d: -f2 | xargs echo -n)}

  if [[ ${mode} == "latency" ]]; then
      ncores_per_instance=4
      batch_size=1
      iters=500
      if [ "${model}" == "wide_deep_large_ds" ]; then
        batch_size=100
      fi
      
      # walk around for pytorch yolov3 model, failed in load 194 iteration.
      if [ "${model}" == "yolo_v3" ] && [ "${framework}" == "pytorch" ]; then
        iters=150
      fi 
      # custom iteration
      if [[ "${latency_high_500[@]}" =~ "${model}" ]]; then
        iters=200
      elif [[ "${latency_high_1000[@]}" =~ "${model}" ]]; then
        iters=100
      fi
  else
      ncores_per_instance=${ncores_per_socket}
      iters=100
  fi

  export OMP_NUM_THREADS=${ncores_per_instance}

  parameters="${parameters} --mode=benchmark --batch_size=${batch_size} --iters=${iters}"

  # Disable fp32 optimization for oob models on TF1.15UP1
  if [ "${topology}" == "RetinaNet50" ] || [ "${topology}" == "ssd_resnet50_v1_fpn_coco" ]; then
    tensorflow_version=$(pip list| grep intel-tensorflow | awk -F ' ' '{print $2}')
    if [ "${precision}" == "fp32" ] && [ "${tensorflow_version}" == "1.15.0up1" ]; then
      sed -i "/models_need_disable_optimize/a ${topology}" ${model_src_dir}/run_benchmark.sh
    fi
  fi

  # general yaml for new config format
  config_new_yaml

  if [ -f "run_benchmark.sh" ]; then
        run_cmd="bash run_benchmark.sh ${parameters}"
  else
        echo "Not found run_benchmark file."
        exit 1
  fi

  echo "BENCHMARK RUNCMD: $run_cmd "
  logFile=${WORKSPACE}/${framework}_${model}_${precision}_${mode}

  for((j=0;$j<${ncores_per_socket};j=$(($j + ${ncores_per_instance}))));
  do
    numactl -m 0 -C "$j-$((j + ncores_per_instance -1))" \
    ${run_cmd} 2>&1|tee ${logFile}_${ncores_per_socket}_${ncores_per_instance}_${j}.log &
  done

  wait

}

# update yaml file
function update_yaml_config {
    if [ ! -f ${yaml} ]; then
        echo "Not found yaml config at \"${yaml}\" location."
        exit 1
    fi

    update_yaml_params=" --batch-size ${batch_size} --iteration ${iters} --mode ${mode}"

    if [ "${update_yaml_params}" != "" ]; then
        python ${WORKSPACE}/lpot-validation/scripts/update_yaml_config.py --yaml=${yaml} ${update_yaml_params}
    fi
}

# general yaml for new config format
function config_new_yaml {

  if [ "${framework}" == "tensorflow" ]; then
    if [[ "${model_src_dir}" == *"image_recognition"* ]] || [[ "${model_src_dir}" == *"object_detection"* ]]; then
      update_yaml_config
      echo -e "\nPrint_updated_yaml... "
      cat ${yaml}
      parameters="--config=${yaml} --input_model=${input_model}"
    fi
  fi

  if [ "${framework}" == "onnxrt" ] && [[ "${model_src_dir}" == *"image_recognition"* ]]; then
      update_yaml_config
      echo -e "\nPrint_updated_yaml... "
      cat ${yaml}
      parameters="--config=${yaml} --input_model=${input_model}"
  fi

}

main
