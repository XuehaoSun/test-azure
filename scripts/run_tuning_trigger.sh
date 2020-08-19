#!/bin/bash

set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='
if [ $# != "8" ] ; then 
    echo 'ERROR:'
    echo "Expected 8 parameters got $#"
    printf 'Please use following parameters:
    --framework=<framework name>
    --model=<model name>
    --model_src_dir=<path to model tuning script>
    --dataset_location=<path to dataset>
    --input_model=<path to input model>
    --yaml=<path to ilit yaml configuration>
    --strategy=<tuning strategy>
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
        --conda_env_name=*)
            conda_env_name=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

# Run auto tune
main() {
    # Import common functions
    source ${WORKSPACE}/ilit-validation/scripts/env_setup.sh --framework=${framework} --model=${model} --conda_env_name=${conda_env_name}

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
        sed -i '/ilit/d' requirements.txt
        python -m pip install -r requirements.txt
        pip list
    else
        echo "Not found requirements.txt file."
    fi


    echo -e "\nGetting git information..."
    echo "$(git remote -v)"
    echo "$(git branch)"
    echo "$(git show | head -5)"

    # ------ WORKAROUND FOR MXNET RESNET50V1 -----
    topology=${model}
    if [ "${model}" == "resnet50v1" ]; then
        topology="resnet50_v1"
    fi

    if [[ "${model}" == *"_qat" ]]; then
        topology="${model%_qat} "
    fi

    q_model=${WORKSPACE}/${framework}-${model}-tune
    if [ ${framework} == "tensorflow" ]; then
        q_model="${q_model}.pb"
    elif [ ${framework} == "mxnet" ]; then
        mkdir -p ${q_model}
        q_model="${q_model}/${topology}"
    fi

    # run_tuning.sh
    starttime=`date +'%Y-%m-%d %H:%M:%S'`

    parameters="--topology=${topology} --dataset_location=${dataset_location} --input_model=${input_model}"

    if [ "${framework}" == "tensorflow" ] || [ ${framework} == "mxnet" ]; then
        parameters="${parameters} --output_model=${q_model}"
    fi

    update_yaml_config

    echo -e "\nRun_tuning parameters... "
    echo ${parameters}

    bash run_tuning.sh ${parameters}
    endtime=`date +'%Y-%m-%d %H:%M:%S'`
    start_seconds=$(date --date="$starttime" +%s);
    end_seconds=$(date --date="$endtime" +%s);
    echo "Tuning time spend: "$((end_seconds-start_seconds))"s "
}

function update_yaml_config {
    if [ ! -f ${yaml} ]; then
        echo "Not found yaml config at \"${yaml}\" location."
        exit 1
    fi

    update_yaml_params=""
    # Replace tuning strategy in yaml file
    if [ "${strategy}" != "" ]; then
        update_yaml_params="${update_yaml_params} --strategy=${strategy}"
    fi

    dataset_params="--calib-data=${dataset_location} --eval-data=${dataset_location}"

    if [ "${framework}" == "pytorch" ]; then
        if [[ "${model_src_dir}" = *"resnet" ]] || [[ "${model_src_dir}" = *"mobilenet"* ]]; then
            dataset_params="--calib-data=${dataset_location}/train --eval-data=${dataset_location}/val"
        fi
    fi

    if [ "${update_yaml_params}" != "" ]; then
        python ${WORKSPACE}/ilit-validation/scripts/update_yaml_config.py --yaml=${yaml} ${update_yaml_params} ${dataset_params}
    fi

    count=$(grep -c 'strategy: ' "${yaml}") || true  # Prevent from exiting when 'strategy' not found
    if [ ${count} == 0 ]; then
      strategy='basic'
    else
      strategy=$(grep 'strategy: ' ${yaml} | awk -F 'strategy: ' '{print$2}')
    fi

    echo "Tuning strategy: ${strategy}"
}

main
