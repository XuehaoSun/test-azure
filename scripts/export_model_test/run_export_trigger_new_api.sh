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
        --output_model=*)
            output_model=`echo $i | sed "s/${PATTERN}//"`;;
        --precision=*)
            precision=`echo $i | sed "s/${PATTERN}//"`;;
        --strategy=*)
            strategy=`echo $i | sed "s/${PATTERN}//"`;;
        --max_trials=*)
            max_trials=`echo $i | sed "s/${PATTERN}//"`;;
        --conda_env_name=*)
            conda_env_name=`echo $i | sed "s/${PATTERN}//"`;;
        --log_level=*)
            log_level=`echo $i | sed "s/${PATTERN}//"`;;
        --main_script=*)
            main_script=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

main() {
    echo -e "\n[VAL INFO] Run INC new API quantization+export..."
    echo -e "\n[VAL INFO] Setting environment..."
    source ${WORKSPACE}/lpot-validation/scripts/env_setup.sh --framework=${framework} --model=${model} \
         --conda_env_name=${conda_env_name} --log_level=${log_level} --install_inc="true"
    set_environment

    echo -e "\n[VAL INFO] Installing model requirements..."
    install_model_deps

    echo -e "\n[VAL INFO] Getting git information..."
    echo "$(git remote -v)"
    echo "$(git branch)"
    echo "$(git show | head -5)"

    echo -e "\n[VAL INFO] Setting run_export.sh cmd line..."
    parameters="--input_model=${input_model} --output_model=${output_model} --dataset_location=${dataset_location} --dtype=${precision}"

    echo "bash run_export.sh ${parameters}"

    echo -e "\n[VAL INFO] Update quantization config in main script..."
    update_conf_params=""
    if [ "${strategy}" != "" ]; then
        echo "Tuning strategy: ${strategy}"
        update_conf_params="${update_conf_params} --strategy=${strategy}"
    fi
    if [ "${max_trials}" != "" ]; then
        update_conf_params="${update_conf_params} --max_trials=${max_trials}"
    fi
    if [ "${update_conf_params}" != "" ]; then
        echo "update_conf_params: $update_conf_params"
        python ${WORKSPACE}/lpot-validation/scripts/update_new_api_config.py --main_script=${main_script} ${update_conf_params}
    fi

    # work around for itex omp issue
    ncores_per_socket=${ncores_per_socket:=$( lscpu | grep 'Core(s) per socket' | cut -d: -f2 | xargs echo -n)}
    export OMP_NUM_THREADS=${ncores_per_socket}

    echo -e "\n[VAL INFO] Run export env list..."
    env

    echo -e "\n[VAL INFO] Running export..."
    bash run_export.sh ${parameters}

    echo -e "\n[VAL INFO] Collect source quant model..."
    if [ ${framework} == "tensorflow" ] && [ "$precision" == "int8" ]; then
        cp ${model_src_dir}/tf-quant.pb ${WORKSPACE}/${framework}-${model_name}-tune.pb
    fi

}

function install_model_deps {
    if [ -d ${model_src_dir} ]; then
        cd ${model_src_dir}
        echo -e "\n[VAL INFO] Working in $(pwd)..."
        if [ -f "requirements.txt" ]; then
            sed -i '/neural-compressor/d' requirements.txt
            if [ "${framework}" == "tensorflow" ]; then
              sed -i '/tensorflow==/d;/tensorflow$/d' requirements.txt
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
    else
        echo "[ERROR] model_src_dir \"${model_src_dir}\" not exists."
        exit 1
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

main