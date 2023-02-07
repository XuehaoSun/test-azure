#!/bin/bash
set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='

for i in "$@"
do
    case $i in
        --python_version=*)
            python_version=`echo $i | sed "s/${PATTERN}//"`;;
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
        --strategy=*)
            strategy=`echo $i | sed "s/${PATTERN}//"`;;
        --strategy_token=*)
            strategy_token=`echo $i | sed "s/${PATTERN}//"`;;
        --max_trials=*)
            max_trials=`echo $i | sed "s/${PATTERN}//"`;;
        --accuracy_criterion=*)
            accuracy_criterion=`echo $i | sed "s/${PATTERN}//"`;;
        --algorithm=*)
            algorithm=`echo $i | sed "s/${PATTERN}//"`;;
        --sampling_size=*)
            sampling_size=`echo $i | sed "s/${PATTERN}//"`;;
        --conda_env_name=*)
            conda_env_name=`echo $i | sed "s/${PATTERN}//"`;;
        --conda_env_mode=*)
            conda_env_mode=`echo $i | sed "s/${PATTERN}//"`;;
        --log_level=*)
            log_level=`echo $i | sed "s/${PATTERN}//"`;;
        --dtype=*)
            dtype=`echo $i | sed "s/${PATTERN}//"`;;
        --itex_mode=*)
            itex_mode=`echo $i | sed "s/${PATTERN}//"`;;
        --is_gpu=*)
            is_gpu=`echo $i | sed "s/${PATTERN}//"`;;
        --main_script=*)
            main_script=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

main() {
    echo -e "\n[VAL INFO] Run INC new API quantization..."
    echo -e "\n[VAL INFO] Setting environment..."
    source ${WORKSPACE}/lpot-validation/scripts/env_setup.sh --framework=${framework} --model=${model} \
         --conda_env_name=${conda_env_name} --conda_env_mode=${conda_env_mode} --log_level=${log_level} \
         --itex_mode=${itex_mode} --install_inc="true"
    set_environment

    echo -e "\n[VAL INFO] Installing model requirements..."
    install_model_deps

    echo -e "\n[VAL INFO] Getting git information..."
    echo "$(git remote -v)"
    echo "$(git branch)"
    echo "$(git show | head -5)"

    echo -e "\n[VAL INFO] Getting topology and qmodel name..."
    get_topology

    echo -e "\n[VAL INFO] Getting input model..."
    get_input_model

    echo -e "\n[VAL INFO] Setting run_tuning.sh cmd line..."
    parameters="--dataset_location=${dataset_location} --input_model=${input_model} --output_model=${q_model}"
    if [ ${framework} == "tensorflow" ] && [[ ${model_src_dir} == *"oob_models/quantization"* ]]; then
        parameters="${parameters} --topology=${topology}"
    fi
    if [ ${framework} == "pytorch" ]; then
        parameters="${parameters} --topology=${topology}"
    fi
    if [ "${framework}" == "onnxrt" ]; then
        quant_format="QOperator"
        if [[ "${model}" == *"_qdq" ]]; then
            quant_format="QDQ"
        fi
        if [[ "${model}" == *"_dynamic" ]]; then
            quant_format="default"
        fi
        parameters="${parameters} --quant_format=${quant_format}"
    fi
    echo "bash run_tuning.sh ${parameters}"

    echo -e "\n[VAL INFO] Update tuning config in main script..."
    update_conf_params=""
    if [ "${strategy}" != "" ]; then
        echo "Tuning strategy: ${strategy}"
        update_conf_params="${update_conf_params} --strategy=${strategy}"
    fi
    if [ "${max_trials}" != "" ]; then
        update_conf_params="${update_conf_params} --max_trials=${max_trials}"
    fi

    backend=""
    if [ "$itex_mode" == "native" ]; then
        backend="default"
    elif [ "$itex_mode" == "onednn_graph" ]; then
        backend="itex"
    fi
    if [ "${backend}" != "" ]; then
        update_conf_params="${update_conf_params} --backend=${backend}"
    fi

    if [ "${is_gpu}" == "true" ]; then
        update_conf_params="${update_conf_params} --device=gpu"
    fi

    if [ "${update_conf_params}" != "" ]; then
        echo "update_conf_params: $update_conf_params"
        python ${WORKSPACE}/lpot-validation/scripts/update_new_api_config.py --main_script=${main_script} ${update_conf_params}
    fi

    echo -e "\n[VAL INFO] Run tuning env list..."
    env

    echo -e "\n[VAL INFO] Running tuning..."
    starttime=`date +'%Y-%m-%d %H:%M:%S'`
    bash run_tuning.sh ${parameters}
    endtime=`date +'%Y-%m-%d %H:%M:%S'`
    start_seconds=$(date --date="$starttime" +%s);
    end_seconds=$(date --date="$endtime" +%s);
    echo "Tuning time spend: "$((end_seconds-start_seconds))"s "

    collect_pb_size || true
    collect_quantized_model || true
}

function get_topology {
    topology=${model}
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
    if [[ "${model}" == *"-oob_fx" ]]; then
        topology="${model%-oob_fx}"
    fi
    if [[ "${framework}" == "onnxrt" ]] && [[ "${model}" == "gpt2_lm_head_wikitext_model_zoo" ]]; then
        topology="gpt2_lm_wikitext2"
    fi
    if [[ "${framework}" == "pytorch" ]] && [[ "${model}" == "bert_base_MRPC_qat" ]]; then
        topology="bert-base-cased"
    fi
    if [ "${framework}" == "pytorch" ]; then
        if [ "${model}" == "ssd_resnet34_fx" ] || [ "${model}" == "ssd_resnet34_qat_fx" ]; then
            topology="ssd-resnet34"
        fi
    fi

    echo "Checking topology..."
    echo "Framework: ${framework}"
    echo "Model: ${model}"
    echo "Topology: ${topology}"

    q_model=${WORKSPACE}/${framework}-${model}-tune
    if [ ${framework} == "tensorflow" ] && [[ ${model_src_dir} != *"keras"* ]];  then
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
            q_model="${model_src_dir}/saved_results"
        fi
    fi
}

function get_input_model {
    echo -e "\n[VAL INFO] [Walk around]Copy model from remote storage..."
    if [ "${framework}" == "onnxrt" ]; then
        if [[ "${model_src_dir}" == *"nlp"* ]]; then
            bert_dirname=$(dirname ${input_model})
            if [[ -d "${bert_dirname}/uncased_L-12_H-768_A-12" ]]; then
              cp -r ${bert_dirname}/uncased_L-12_H-768_A-12 ${model_src_dir}/
            fi
        fi
        if [[ "${model_src_dir}" == *"unet"* ]]; then
            unet_dirname=$(dirname ${input_model})
            if [[ -f "${unet_dirname}/weights.pb" ]]; then
              cp -r ${unet_dirname}/weights.pb ${WORKSPACE}/
            fi
        fi
        copy_model
    fi
}

function install_model_deps {
    if [ -d ${model_src_dir} ]; then
        cd ${model_src_dir}
        echo -e "\n[VAL INFO] Working in $(pwd)..."
        if [ -f "requirements.txt" ]; then
            sed -i '/neural-compressor/d' requirements.txt
            if [ "${framework}" == "onnxrt" ]; then
                sed -i '/^onnx>=/d;/^onnx==/d;/^onnxruntime>=/d;/^onnxruntime==/d' requirements.txt
            fi
            if [ "${framework}" == "tensorflow" ]; then
                sed -i '/tensorflow==/d;/tensorflow$/d' requirements.txt
            fi
            if [ "${framework}" == "mxnet" ]; then
                sed -i '/mxnet==/d;/mxnet$/d;/mxnet-mkl==/d;/mxnet-mkl$/d' requirements.txt
            fi
            if [ "${framework}" == "pytorch" ]; then
                sed -i '/torch==/d;/torch$/d;/torchvision==/d;/torchvision$/d' requirements.txt
                if [[ $(grep "torchaudio" requirements.txt | wc -l) != 0 ]]; then
                    pt_version=$(python -c "import torch; print(torch.__version__)")
                    torchaudio_version="0.$(echo $pt_version| cut -d'.' -f2).$(echo $pt_version| cut -d'.' -f3)"
                    pip install torchaudio=="$torchaudio_version" -f https://download.pytorch.org/whl/torch_stable.html
                    sed -i '/torchaudio/d' requirements.txt
                fi
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
    else
        echo "[ERROR] model_src_dir \"${model_src_dir}\" not exists."
        exit 1
    fi

    # specific ENV setting for some models
    if [[ "${model_src_dir}" == *"text-classification/quantization/ptq_static/eager" ]] || [[ "${model_src_dir}" == *"language-modeling/quantization/ptq_static/eager" ]]; then
        echo -e "\n[VAL INFO] Installing pytorch-huggingface requirements..."
        n=0
        until [ "$n" -ge 5 ]
        do
            python -m pip install -r ${WORKSPACE}/lpot-validation/requirement_pytorch_huggingface.txt && break
            n=$((n+1))
            sleep 5
        done
        pip list
        setup_install_pypi_source
        cd ../../../../common
        python setup.py install
        cd -
    fi

    if [[ "${framework}" == "pytorch" ]] && [[ "${model}" == *"3dunet"* ]]; then
        # Install mlperf_loadgen
        pip install absl-py
        if [[ ${model} == '3dunet' ]] && [[ ${python_version} != "3.10" ]]; then
            mlperf_loadgen_whl=/tf_dataset/pytorch/mlperf_3dunet/mlperf_loadgen-0.5a0-cp${python_version//./}-*.whl
            pip install ${mlperf_loadgen_whl}
        elif [[ ${model} == '3dunet' ]] && [[ ${python_version} == "3.10" ]]; then
            git clone https://github.com/mlcommons/inference.git --recursive
            cd inference/loadgen
            python setup.py install
            cd -
        fi

        # Install nnUnet
        cd nnUnet
        pip install -r requirements.txt
        setup_install_pypi_source
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

    if [[ -f "prepare_loadgen.sh" ]]; then
        if [[ "${model}" == "rnnt_ipex" ]]; then
            bash prepare_env.sh
        else
            echo "\nInstalling loadgen..."
            bash prepare_loadgen.sh "$(pwd)"
        fi
    fi
    # temperate limit datasets version to 2.2.2(hugginface models will be removed soon)
    if [[ "${framework}" == "pytorch" ]] && [[ "${model_src_dir}" == *"nlp/huggingface_models"* ]]; then
        pip install datasets==2.2.2
    fi
    # re-install pycocotools resolve the issue with numpy
    echo "re-install pycocotools resolve the issue with numpy..."
    pip uninstall pycocotools -y
    pip install --no-cache-dir pycocotools
}

function setup_install_pypi_source {
    echo -e "\n[easy_install]\nindex_url = https://pypi.tuna.tsinghua.edu.cn/simple" >> setup.cfg
}

function copy_model {
    echo "[VAL INFO] Copying model to workspace."
    model_name=$(basename ${input_model})
    local_model="${WORKSPACE}/${model_name}"
    cp -r "${input_model}" "${local_model}"
    input_model=${local_model}
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

function collect_quantized_model {
    # copy tuning result to /tmp, dlrm is too big and space consuming
    if [ -z ${q_model} ]; then
        return
    fi

    rm -rf /tmp/"${framework}-${model}-tune"*
    rm -rf /tmp/inc/"${framework}-${model}-tune"*
    save_path=/tmp/inc/${framework}-${model}-tune-$(date +%s)
    echo "!!!tune model save path is ${HOSTNAME}:${save_path}/* !!!"
    mkdir -p "${save_path}"
    echo "Copying \"${q_model}*\" to \"${save_path}\""
    cp -r "${q_model}"* "${save_path}"
    if [ "${framework}" == "pytorch" ]; then
        cp -r "${q_model}"* "${WORKSPACE}/${framework}-${model}-tune"
    fi
}

main