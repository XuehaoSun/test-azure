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
    --yaml=<path to lpot yaml configuration>
    --strategy=<tuning strategy>
    --max_trials=<max tuning trials>
    --algorithm=<algorithm for quantization>
    --conda_env_name=<conda environment name>
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
        --yaml=*)
            yaml=`echo $i | sed "s/${PATTERN}//"`;;
        --strategy=*)
            strategy=`echo $i | sed "s/${PATTERN}//"`;;
        --max_trials=*)
            max_trials=`echo $i | sed "s/${PATTERN}//"`;;
        --algorithm=*)
            algorithm=`echo $i | sed "s/${PATTERN}//"`;;
        --conda_env_name=*)
            conda_env_name=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

# Run auto tune
main() {
    # Import common functions
    source ${WORKSPACE}/lpot-validation/scripts/env_setup.sh --framework=${framework} --model=${model} --conda_env_name=${conda_env_name}

    echo -e "\nSetting environment..."
    set_environment

    # Temporary change for helloworld_keras
    if [ "${model}" == "helloworld_keras" ]; then
        model_src_dir="${WORKSPACE}/lpot-models/examples/helloworld"
    fi
    
    if [ -d ${model_src_dir} ]; then
        cd ${model_src_dir}
        echo -e "\nWorking in $(pwd)..."
    else
        echo "[ERROR] model_src_dir \"${model_src_dir}\" not exists."
        exit 1
    fi

    echo -e "\nInstalling model requirements..."
    # ipex model shouldn't re-install dependencies.
    if [[ "${model}" != *"_ipex" ]]; then
      if [ -f "requirements.txt" ]; then
          sed -i '/lpot/d' requirements.txt
          sed -i "/tensorflow==/d;/torch==/d;/mxnet==/d" requirements.txt
          if [ "${framework}" == "onnxrt" ]; then
            sed -i '/onnx/d;/onnxruntime/d' requirements.txt
          fi
          python -m pip install -r requirements.txt
          pip list
      else
          echo "Not found requirements.txt file."
      fi
    fi

    echo -e "\nGetting git information..."
    echo "$(git remote -v)"
    echo "$(git branch)"
    echo "$(git show | head -5)"

    # Temporary change for helloworld_keras
    if [ "${model}" == "helloworld_keras" ]; then
        python train.py
        cd "tf2.x"
        python test.py
        exit 0
    fi

    # ------ WORKAROUND FOR MXNET RESNET50V1 -----
    topology=${model}
    if [ "${model}" == "resnet50v1" ]; then
        topology="resnet50_v1"
    fi

    if [[ "${model}" == *"_qat" ]]; then
        topology="${model%_qat} "
    fi

    if [[ "${model}" == *"_gpu" ]]; then
        topology="${model%_gpu}"
    fi

    q_model=${WORKSPACE}/${framework}-${model}-tune
    if [ ${framework} == "tensorflow" ]; then
        q_model="${q_model}.pb"
    elif [ ${framework} == "mxnet" ]; then
        mkdir -p ${q_model}
        q_model="${q_model}/${topology}"
    elif [ ${framework} == "onnxrt" ]; then
        q_model="${q_model}.onnx"
    elif [ ${framework} == "pytorch" ]; then
        q_model=""
    fi

    # run_tuning.sh
    starttime=`date +'%Y-%m-%d %H:%M:%S'`
    parameters="--topology=${topology} --dataset_location=${dataset_location} --input_model=${input_model}"
    # pytorch need to use default output_model path
    if [ ${framework} != "pytorch" ]; then
      parameters="${parameters} --output_model=${q_model}"
    fi

    # new config with yaml
    if [ "${framework}" == "tensorflow" ]; then
      if [[ "${model_src_dir}" == *"image_recognition"* ]] || [[ "${model_src_dir}" == *"object_detection"* ]]; then
        parameters="--config=${yaml} --input_model=${input_model} --output_model=${q_model}"
      fi
    fi
    if [ "${framework}" == "onnxrt" ] && [[ "${model_src_dir}" == *"image_recognition"* ]]; then
      parameters="--config=${yaml} --input_model=${input_model} --output_model=${q_model}"
    fi

    update_yaml_config
    echo -e "\nPrint_updated_yaml... "
    cat ${yaml}

    echo -e "\nRun_tuning parameters... "
    echo ${parameters}
    echo "Total resident size (kbytes): $(cat /proc/meminfo |grep 'MemTotal' |sed 's/[^0-9]//g')"

    /usr/bin/time -v bash run_tuning.sh ${parameters}
    endtime=`date +'%Y-%m-%d %H:%M:%S'`
    start_seconds=$(date --date="$starttime" +%s);
    end_seconds=$(date --date="$endtime" +%s);
    echo "Tuning time spend: "$((end_seconds-start_seconds))"s "

    collect_pb_size || true

    # copy tuning result to /tmp
    save_path=/tmp/${framework}-${model}-tune-$(date +%s)
    echo "HOSTNAME IS ${HOSTNAME}"
    echo "!!!tune model save path is ${save_path} !!!"
    mkdir -p "${save_path}"
    cp -r "${q_model}"* "${save_path}"
}

function collect_pb_size {

    if [ "${framework}" == "tensorflow" ];then
        if [ "${model}" == "style_transfer" ];then
          fp32_pb_size=$(du -s -BM ${input_model}.meta |cut -f1)
        else
          fp32_pb_size=$(du -s -BM ${input_model} |cut -f1)
        fi
        int8_pb_size=$(du -s -BM ${q_model} |cut -f1)
        python ${WORKSPACE}/lpot-validation/scripts/count_quantize_op.py --fp32_model ${input_model} --int8_model ${q_model}
    elif [ "${framework}" == "mxnet" ];then
        fp32_pb_size=$(du -s -BM ${input_model} |cut -f1)
        int8_pb_size=$(du -s -BM ${q_model%/*} |cut -f1)
    elif [ "${framework}" == "pytorch" ];then
        fp32_pb_size="None"
        int8_pb_size="None"
    else
        fp32_pb_size="0M"
        int8_pb_size="0M"
    fi

    echo "The input model size is: ${fp32_pb_size}"
    echo "The output model size is: ${int8_pb_size}"
}

function update_yaml_config {
    if [ ! -f ${yaml} ]; then
        echo "Not found yaml config at \"${yaml}\" location."
        exit 1
    fi
    # update dataset
    if [ "${framework}" != "pytorch" ]; then
      sed -i "/\/path\/to\/calibration\/dataset/s|root:.*|root: $dataset_location|g" ${yaml}
      sed -i "/\/path\/to\/evaluation\/dataset/s|root:.*|root: $dataset_location|g" ${yaml}
    fi

    update_yaml_params=""
    # Replace tuning strategy in yaml file
    if [ "${strategy}" != "" ]; then
        update_yaml_params="${update_yaml_params} --strategy=${strategy}"
    fi

    if [ "${max_trials}" != "" ]; then
        update_yaml_params="${update_yaml_params} --max-trials=${max_trials}"
    fi

    if [ "${algorithm}" != "" ]; then
        update_yaml_params="${update_yaml_params} --algorithm=${algorithm}"
    fi

    if [ "${update_yaml_params}" != "" ]; then
        python ${WORKSPACE}/lpot-validation/scripts/update_yaml_config.py --yaml=${yaml} ${update_yaml_params}
    fi

    echo "Tuning strategy: ${strategy}"
}

main
