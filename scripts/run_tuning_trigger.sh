#!/bin/bash
set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='
if [ $# != "11" ] ; then
    echo 'ERROR:'
    echo "Expected 11 parameters got $#"
    printf 'Please use following parameters:
    --framework=<framework name>
    --model=<model name>
    --model_src_dir=<path to model tuning script>
    --dataset_location=<path to dataset>
    --input_model=<path to input model>
    --yaml=<path to lpot yaml configuration>
    --strategy=<tuning strategy>
    --strategy_token=<token for strategy>
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
        --strategy_token=*)
            strategy_token=`echo $i | sed "s/${PATTERN}//"`;;
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
    source ${WORKSPACE}/lpot-validation/scripts/env_setup.sh --framework=${framework} --model=${model} --model_src_dir=${model_src_dir} --conda_env_name=${conda_env_name}

    echo -e "\nSetting environment..."
    set_environment

    # Temporary change for helloworld_keras
    if [ "${model}" == "helloworld_keras" ]; then
        model_src_dir="${WORKSPACE}/lpot-models/examples/helloworld"
    fi
    
    if [ -d ${model_src_dir} ]; then
        cd ${model_src_dir}
        echo -e "\nWorking in $(pwd)..."
        if [[ "${model_src_dir}" == *"pytorch/eager/language_translation/ptq" ]]; then
            python setup.py install
        fi
        if [[ "${model_src_dir}" == *"/huggingface_models" ]]; then
            python setup.py install
            pip install git-python
            bash install_requirements.sh --topology=${model}
        fi
        if [[ "${framework}" == "pytorch" ]] && [[ "${model}" == *"3dunet"* ]]; then
            # Install nnUnet
            cd nnUnet
            python setup.py install
            cd ..
            # Workaround for problem with passing dataset location
            mkdir ${model_src_dir}/build
            for dirname in `ls ${dataset_location}`
            do
                ln -s ${dataset_location}/${dirname} ${model_src_dir}/build/${dirname}
            done
            mkdir ${model_src_dir}/build/postprocessed_data
            mkdir ${model_src_dir}/build/logs
            dataset_location=${model_src_dir}/build/preprocessed_data

            # Export variables required for nnUnet
            export nnUNet_raw_data_base=${model_src_dir}/build/raw_data
            export nnUNet_preprocessed=${dataset_location}
            export RESULTS_FOLDER=${model_src_dir}/build/result
        fi
        if [[ "${framework}" == "pytorch" ]] && [[ "${model}" == "maskrcnn"* ]]; then
            echo "Checking gcc version:"
            gcc -v
            bash install.sh
        fi
    else
        echo "[ERROR] model_src_dir \"${model_src_dir}\" not exists."
        exit 1
    fi

    echo -e "\nInstalling model requirements..."
    # ipex model shouldn't re-install dependencies.
    if [[ "${model}" != *"_ipex" ]]; then
      if [ -f "requirements.txt" ]; then
          sed -i '/lpot/d' requirements.txt
          if [ "${framework}" == "onnxrt" ]; then
            sed -i '/onnx/d;/onnxruntime/d' requirements.txt
          fi
          if [ "${framework}" == "tensorflow" ]; then
            sed -i '/tensorflow==/d;/tensorflow$/d' requirements.txt
          fi
          if [ "${framework}" == "mxnet" ]; then
            sed -i '/mxnet==/d;/mxnet$/d;/mxnet-mkl==/d;/mxnet-mkl$/d' requirements.txt
          fi
          if [ "${framework}" == "pytorch" ]; then
            sed -i '/torch==/d;/torch$/d;/torchvision==/d;/torchvision$/d' requirements.txt
          fi
          n=0
          until [ "$n" -ge 5 ]
          do
              python -m pip install -r requirements.txt && break
              n=$((n+1))
              sleep 5
          done
          pip list
      else
          echo "Not found requirements.txt file."
      fi
    fi

    if [[ "${framework}" == "pytorch" ]]; then
        if [[ "${model}" == "rnnt" ]] || [[ "${model}" == "ssd_resnet34_fx" ]]; then
            if [ ${model} == "rnnt" ];then
                cd ${model_src_dir}/../../../utils/MLPerf/loadgen
            else
                cd ${model_src_dir}/../../../../utils/MLPerf/loadgen
            fi
            echo "Checking gcc version:"
            gcc -v
            python setup.py install
            cd ${model_src_dir}
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

    if [[ "${model}" == *"_fx" ]]; then
        topology="${model%_fx}"
    fi

    if [[ "${model}" == *"_qat_fx" ]]; then
        topology="${model%_qat_fx}"
    fi

    if [[ "${framework}" == "onnxrt" ]] && [[ "${model}" == "gpt2_lm_head_wikitext_model_zoo" ]]; then
        topology="gpt2_lm_wikitext2"
    fi

    q_model=${WORKSPACE}/${framework}-${model}-tune
    if [ ${framework} == "tensorflow" ] && [[ ${model_src_dir} != *"keras" ]];  then
        q_model="${q_model}.pb"
    elif [ ${framework} == "mxnet" ]; then
        mkdir -p ${q_model}
        q_model="${q_model}/${topology}"
    elif [ ${framework} == "onnxrt" ]; then
        q_model="${q_model}.onnx"
    elif [ ${framework} == "pytorch" ]; then
        if [ ${model} == "maskrcnn_fx" ]; then
            q_model="${q_model}.pth"
        else
            q_model=""
        fi
    fi

    # Workaround for ONNX models from remote storage
    if [ "${framework}" == "onnxrt" ]; then
        if [ "${model}" == "bert_squad_model_zoo" ] || [ "${model}" == "mobilebert_squad_mlperf" ]; then
            bert_dirname=$(dirname ${input_model})
            cp -r ${bert_dirname}/uncased_L-12_H-768_A-12 ${model_src_dir}/
        fi
        copy_model
    fi

    if [ "${framework}" == "tensorflow" ] && [ "${model}" == "bert_base_mrpc" ]; then
        cp -r ${input_model} ${model_src_dir}/bert_base_mrpc
        input_model=${model_src_dir}/bert_base_mrpc
    fi

    echo "Checking topology..."
    echo "Framework: '${framework}'"
    echo "Model: '${model}'"
    if [ "${framework}" == "pytorch" ] && [ "${model}" == "ssd_resnet34_fx" ]; then
        topology="ssd-resnet34"
        echo "Setting topology to ${topology}"
    fi

    echo "Topology is '${topology}'"

    # run_tuning.sh
    starttime=`date +'%Y-%m-%d %H:%M:%S'`
    parameters="--topology=${topology} --dataset_location=${dataset_location} --input_model=${input_model}"
    # pytorch need to use default output_model path
    if [ ${framework} != "pytorch" ]; then
      parameters="${parameters} --output_model=${q_model}"
    elif [ "${model}" == "rnnt" ] || [ "${model}" == "ssd_resnet34_fx" ]; then
        parameters=" ${parameters} --output_model=${model_src_dir}/saved_results"
    fi

    # new config with yaml
    if [ "${framework}" == "tensorflow" ]; then
        new_config_dirs=("image_recognition" "object_detection" "nlp/bert" "semantic_image_segmentation" "keras")
        for model_dir in ${new_config_dirs[*]}; do
            if [[ "${model_src_dir}" == *"${model_dir}"* ]]; then
                parameters="--config=${yaml} --input_model=${input_model} --output_model=${q_model}"
                break
            fi
        done
    fi

    if [ "${framework}" == "onnxrt" ] && [[ "${model_src_dir}" != *"language_translation"* ]] && [[ "${model}" != "gpt2_lm_head_wikitext_model_zoo" ]]; then
      parameters="--config=${yaml} --input_model=${input_model} --output_model=${q_model}"
    fi

    if [ "${framework}" == "onnxrt" ] && [[ "${model_src_dir}" == *"language_translation"* ]]; then
      ln -s ${input_model} ${model_src_dir}/
    fi

    if [ "${framework}" == "tensorflow" ] && [ "${model}" == "bert_base_mrpc" ]; then
        parameters="${parameters} --dataset_location=${dataset_location}"
    fi

    if [ "${framework}" == "onnxrt" ]; then
        onnxrt_ds_location_models=("bert_squad_model_zoo" "mobilebert_squad_mlperf" "gpt2_lm_head_wikitext_model_zoo")
        if [[ " ${onnxrt_ds_location_models[@]} " =~ " ${model} " ]]; then
            parameters="${parameters} --dataset_location=${dataset_location}"
        fi
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

    # copy tuning result to /tmp, dlrm is too big and space consuming
    if [ -z ${q_model} ]; then
        return
    fi
    if [[ "${model}" == "dlrm"* ]];then
        rm -rf /tmp/pytorch-"${model}"-tune*
    fi
    save_path=/tmp/${framework}-${model}-tune-$(date +%s)
    echo "!!!tune model save path is ${HOSTNAME}:${save_path}/* !!!"
    mkdir -p "${save_path}"
    echo "Copying \"${q_model}*\" to \"${save_path}\""
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
        # python ${WORKSPACE}/lpot-validation/scripts/count_quantize_op.py --fp32_model ${input_model} --int8_model ${q_model}
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
        sed -i "/\/path\/to\/annotation/s|anno_path:.*|anno_path: /tf_dataset/dataset/coco_dataset/raw-data/annotations/instances_val2017.json |g" ${yaml}
        if [ "${framework}" == "tensorflow" ]; then
            if [ "${model}" == "bert_large_squad" ]; then
                sed -i "/\/path\/to\/eval.tf_record/s|root:.*|root: $dataset_location/eval.tf_record|g" ${yaml}
                sed -i "/\/path\/to\/dev-v1.1.json/s|label_file:.*|label_file: $dataset_location/dev-v1.1.json|g" ${yaml}
                sed -i "/\/path\/to\/vocab.txt/s|vocab_file:.*|vocab_file: $dataset_location/vocab.txt|g" ${yaml}
            fi
            if [ "${model}" == "efficientnet_b0" ]; then
                echo "Updating imagenet dataset in ${yaml} yaml"
                sed -i "/\/path\/to\/calibration\/dataset/s|data_path:.*|data_path: $dataset_location|g" ${yaml}
                sed -i "/\/path\/to\/evaluation\/dataset/s|data_path:.*|data_path: $dataset_location|g" ${yaml}
                sed -i "/\/path\/to\/calibration\/label/s|image_list:.*|image_list: /tf_dataset/pytorch/ImageNet/raw/caffe_ilsvrc12/val.txt|g" ${yaml}
                sed -i "/\/path\/to\/evaluation\/label/s|image_list:.*|image_list: /tf_dataset/pytorch/ImageNet/raw/caffe_ilsvrc12/val.txt|g" ${yaml}
            fi
            if [ "${model}" == "deeplab" ]; then
                sed -i "/\/path\/to\/pascal_voc_seg\/tfrecord/s|root:.*|root: $dataset_location|g" ${yaml}
            fi
        fi
        if [ "${framework}" == "onnxrt" ] && [ "${model}" == "resnet_v1_5_mlperf" ];  then
            sed -i "/\/path\/to\/calibration\/dataset/s|data_path:.*|data_path: $dataset_location|g" ${yaml}
            sed -i "/\/path\/to\/evaluation\/dataset/s|data_path:.*|data_path: $dataset_location|g" ${yaml}
            sed -i "/\/path\/to\/calibration\/label/s|image_list:.*|image_list: /tf_dataset/pytorch/ImageNet/raw/caffe_ilsvrc12/val.txt|g" ${yaml}
            sed -i "/\/path\/to\/evaluation\/label/s|image_list:.*|image_list: /tf_dataset/pytorch/ImageNet/raw/caffe_ilsvrc12/val.txt|g" ${yaml}
        fi
    fi

    if [ "${framework}" == "pytorch" ] && [ "${model}" == "ssd_resnet34_fx" ]; then
        sed -i "/convert_dataset\/annotations\/instances_val2017\.json/s|anno_dir:.*|anno_dir: ${dataset_location}/annotations/instances_val2017.json |g" ${yaml}
    fi

    update_yaml_params=""
    # Replace tuning strategy in yaml file
    if [ "${strategy}" != "" ]; then
        update_yaml_params="${update_yaml_params} --strategy=${strategy} --strategy-token=${strategy_token}"
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

function copy_model {
    echo "Copying model to workspace."
    model_name=$(basename ${input_model})
    local_model="${WORKSPACE}/${model_name}"
    cp -r "${input_model}" "${local_model}"
    input_model=${local_model}
}

main
