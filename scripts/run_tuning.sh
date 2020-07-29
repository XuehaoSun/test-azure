#!/bin/bash

set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='
if [ $# != "4" ] ; then 
    echo 'ERROR:'
    echo "Expected 4 parameters got $#"
    printf 'Please use following parameters:
    --framework=<framework name>
    --model=<model name>
    --tuning_strategy=<tuning strategy>
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
        --tuning_strategy=*)
            tuning_strategy=`echo $i | sed "s/${PATTERN}//"`;;
        --conda_env_name=*)
            conda_env_name=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

# framework=tensorflow
# model=resnet50v1.0
# conda_env_name=tensorflow-1.15.2

# Run auto tune
main() {
    # Import common functions
    source ${WORKSPACE}/ilit-validation/scripts/common_functions.sh --framework=${framework} --model=${model} --tuning_strategy=${tuning_strategy} --conda_env_name=${conda_env_name}

    echo -e "\nSetting environment..."
    set_environment
    
    # Get model source dir and model path
    echo -e "\nGetting benchmark variables..."
    get_benchmark_envs 

    if [ -d ${model_src_dir} ]; then
        cd ${model_src_dir}
        echo -e "\nWorking in $(pwd)..."
    else
        echo "[ERROR] model_src_dir \"${model_src_dir}\" not exists."
        exit 1
    fi

    echo -e "\nGetting git information..."
    echo "$(git remote -v)"
    echo "$(git branch)"
    echo "$(git show | head -5)"

    q_model=${WORKSPACE}/${framework}-${model}-tune
    if [ ${framework} == "tensorflow" ]; then
        q_model="${q_model}.pb"
    fi

    # run_tuning.sh
    starttime=`date +'%Y-%m-%d %H:%M:%S'`
    
    # ------ WORKAROUND FOR MXNET RESNET50V1 -----
    topology=${model}
    if [ "${model}" == "resnet50v1" ]; then
        topology="resnet50_v1"
    fi

    parameters="--topology=${topology} --dataset_location=${dataset_location}"

    if [ ${framework} == "mxnet" ]; then
        parameters="${parameters} --model_location=${model_base_path} --output_model=${q_model}"
    fi

    if [ "${framework}" == "tensorflow" ]; then
        parameters="${parameters} --input_model=${input_model} --output_model=${q_model}"
    fi

    bash run_tuning.sh ${parameters}
    endtime=`date +'%Y-%m-%d %H:%M:%S'`
    start_seconds=$(date --date="$starttime" +%s);
    end_seconds=$(date --date="$endtime" +%s);
    echo "Tuning time spend: "$((end_seconds-start_seconds))"s "
}

main
