#!/bin/bash

set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='
if [ $# != "4" ] ; then 
    echo 'ERROR:'
    echo "Expected 4 parameters got $#"
    printf 'Please use following parameters:
    --framework=<framework name>
    --model=<model name>
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

# ----------------------------- Consts -----------------------------
cnn_models=(
    "resnet18"
    "resnet50"
    "resnet101"
    "resnet50v1"
    "resnet50v1.0"
    "resnet50v1.5"
    "resnet101"
    "inception_v1"
    "inception_v2"
    "inceptionv3"
    "inception_v3"
    "inception_v4"
    "inception_resnet_v2"
    "mobilenetv1"
    "mobilenet1.0"
    "mobilenetv2"
    "mobilenetv2_1.0"
    "resnet18_v1"
    "squeezenet1.0"
)

obj_models=(
    "ssd_resnet50_v1"
    "SSD-ResNet50_v1"
)

bert_models=(
    "bert_base_MRPC"
    "bert_base_CoLA"
    "bert_base_STS-B"
    "bert_base_SST-2"
    "bert_base_RTE"
    "bert_large_MRPC"
    "bert_large_SQuAD"
    "bert_large_QNLI"
    "bert_large_RTE"
    "bert_large_CoLA"
)

dlrm_models=(
    "dlrm"
)

# ------------------------------------- Environment -------------------------------------

function set_TF_env {
    export KMP_BLOCKTIME=1
    export KMP_AFFINITY=granularity=fine,verbose,compact,1,0
    export TF_MKL_OPTIMIZE_PRIMITIVE_MEMUSE=false

    export PATH=${HOME}/miniconda3/bin/:$PATH
    source activate ${conda_env_name}
}

function set_MXNet_env {
    export KMP_BLOCKTIME=1
    export KMP_AFFINITY=granularity=fine,verbose,compact,1,0
    export OMP_NUM_THREADS=28

    export PATH=${HOME}/miniconda3/bin/:$PATH
    source activate ${conda_env_name}
    export PYTHONPATH=${PYTHONPATH}:${WORKSPACE}/ilit-models/
}

function set_PT_env {
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
}

function set_environment {
    case "${framework}" in
        tensorflow)
            set_TF_env;;
        mxnet)
            set_MXNet_env;;
        pytorch)
            set_PT_env;;
        *)
            echo "Framework ${framework} not recognized."; exit 1;;
    esac

    echo "Checking ilit..."
    python -V
    pip list
    c_ilit=$(pip list | grep -c 'ilit') || true  # Prevent from exiting when 'ilit' not found
    if [ ${c_ilit} != 0 ]; then
        pip uninstall ilit -y
    fi
    pip list

    if [ "${framework}" == "tensorflow" ]; then
        if [ ! -d ${WORKSPACE}/ilit-models ]; then
            echo "\"ilit-model\" not found. Exiting..."
            exit 1
        fi
        cd ${WORKSPACE}/ilit-models
        python setup.py install
        pip list

        echo "HOSTNAME IS ${HOSTNAME}"
    fi
}


# ---------------- Get model parameters ---------------

function get_benchmark_envs {
    get_model_type
    get_dataset_location
    get_model_src_path
    get_input_model
    get_yaml_path
    get_strategy
}

# ----- Model Type -----
function get_model_type {
    if [[ " ${cnn_models[*]} " =~ " ${model} " ]]; then
        model_type="cnn"
    elif [[ " ${obj_models[*]} " =~ " ${model} " ]]; then
        model_type="obj"
    elif [[ " ${bert_models[*]} " =~ " ${model} " ]]; then
        model_type="bert"
    elif [[ " ${dlrm_models[*]} " =~ " ${model} " ]]; then
        model_type="dlrm"
    else
        model_type="unknown"
    fi
    echo "Model type: ${model_type}"
}


# ----- Dataset Location -----
function get_dataset_location {
    case "${framework}" in
        tensorflow)
            get_tf_dataset_location;;
        mxnet)
            get_mxnet_dataset_location;;
        pytorch)
            get_pytorch_dataset_location;;
        *)
            echo "Framework ${framework} not recognized."; exit 1;;
    esac
    echo "Dataset location: ${dataset_location}"
}

function get_tf_dataset_location {
    case ${model_type} in
        cnn)
            dataset_location="/tf_dataset/dataset/imagenet";;
        obj)
            dataset_location="/tf_dataset/tensorflow/coco_val.record";;
        *)
            echo "Model ${model} not supported for ${framework}."; exit 1;;
    esac
}

function get_mxnet_dataset_location {
    case ${model_type} in
        cnn)
            dataset_location="/tf_dataset/mxnet/val_256_q90.rec";;
        obj)
            dataset_location="/tf_dataset/dataset/coco_dataset/raw-data";;
        bert)
            dataset_location="?";;  # TODO
        *)
            echo "Model ${model} not supported for ${framework}."; exit 1;;
    esac
}

function get_pytorch_dataset_location {
    case ${model_type} in
        cnn)
            dataset_location="/tf_dataset/pytorch/ImageNet/raw";;
        bert)
            dataset_location="?";;  # TODO
        dlrm)
            dataset_location="/mnt/local_disk3/dataset/dlrm/dlrm/input";;
        *)
            echo "Model ${model} not supported for ${framework}."; exit 1;;
    esac
}

# ----- Model Source Path -----
function get_model_src_path {
    case "${model_type}" in
        cnn)
            model_relative_path="examples/${framework}/image_recognition";;
        obj)
            model_relative_path="examples/${framework}/object_detection";;
        bert)
            model_relative_path="examples/${framework}/language_translation";;
        dlrm)
            model_relative_path="examples/${framework}/recommendation";;
        *)
            echo "Model ${model} not supported for ${framework}."; exit 1;;
    esac
    if [ "${framework}" == "pytorch" ] && [[ "${model}" == "resnet"* ]]; then
        model_relative_path="${model_relative_path}/resnet"
    fi

    model_src_dir=${WORKSPACE}/ilit-models/${model_relative_path}
    benchmark_dir=${WORKSPACE}/ilit-validation/${model_relative_path}

    echo "Model src dir: ${model_src_dir}"
    echo "Benchmark dir: ${benchmark_dir}"
} # LG to keep

# ----- Input Model -----
function get_input_model {
    case "${framework}" in
        tensorflow)
            get_tf_input_model;;
        mxnet)
            model_base_path="/tf_dataset/mxnet/${model}"
            # ------ WORKAROUND FOR MXNET RESNET50V1 -----
            if [ "${model}" == "resnet50v1" ]; then
                model_base_path="/tf_dataset/mxnet/resnet50_v1"
            fi;;
        pytorch)
            get_pytorch_input_model;;
        *)
            echo "Framework ${framework} not recognized."; exit 1;;
    esac
    if [ "${framework}" == "mxnet" ]; then
        echo "Model base path: ${model_base_path}"
    else
        echo "Input model: ${input_model}"
    fi
}

function get_tf_input_model {
    case ${model} in
        resnet50v1.0) input_model="/tf_dataset/pre-trained-models/resnet50/fp32/freezed_resnet50.pb";;
        resnet50v1.5) input_model="/tf_dataset/pre-trained-models/resnet50v1_5/fp32/resnet50_v1.pb";;
        resnet101) input_model="/tf_dataset/pre-trained-models/resnet101/fp32/optimized_graph.pb";;
        inception_v1) input_model="/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_${model}.pb";;
        inception_v2) input_model="/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_${model}.pb";;
        inception_v3) input_model="/tf_dataset/pre-trained-models/inceptionv3/fp32/freezed_inceptionv3.pb";;
        inception_v4) input_model="/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_${model}.pb";;
        inception_resnet_v2) input_model="/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_${model}.pb";;
        mobilenetv1) input_model="/tf_dataset/pre-trained-models/mobilenet_v1/fp32/mobilenet_v1_1.0_224_frozen.pb";;
        mobilenetv2) input_model="/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_mobilenet_v2.pb";;
        ssd_resnet50_v1) input_model="/tf_dataset/pre-train-model-oob/object_detection/${model}/frozen_inference_graph.pb";;
        *) echo "Could not found model path for ${model}."; exit 1;;
    esac
}


function get_pytorch_input_model {
    case ${model_type} in
        cnn)
            echo "?";;
        bert)
            echo "?";;
        *) echo "Could not found model path for ${model}."; exit 1;;
    esac
}

# ----- Get yaml model path -----
function get_yaml_path {
    if [ ! -d ${model_src_dir} ]; then
        echo "Model source dir \"${model_src_dir}\" not found."
        exit 1
    fi
    case "${framework}" in
        tensorflow)
            case "${model}" in
                resnet50v1.0) yaml_name="resnet50_v1.yaml";;  # We may consider unifying yaml name to make it easier to get yaml path from model name.
                resnet50v1.5) yaml_name="resnet50_v1_5.yaml";;
                resnet101) yaml_name="resnet101.yaml";;
                inception_v1) yaml_name="inceptionv1.yaml";;
                inception_v2) yaml_name="inceptionv2.yaml";;
                inception_v3) yaml_name="inceptionv3.yaml";;
                inception_v4) yaml_name="inceptionv4.yaml";;
                inception_resnet_v2) yaml_name="irv2.yaml";;
                mobilenetv1) yaml_name="mobilenet_v1.yaml";;
                mobilenetv2) yaml_name="mobilenet_v2.yaml";;
                ssd_resnet50_v1) yaml_name="ssd_resnet50_v1.yaml";;
                *) yaml_name="${model}.yaml";;
            esac;;
        mxnet)
            case "${model_type}" in
                cnn) yaml_name="cnn.yaml";;
                obj) yaml_name="ssd.yaml";;
                bert) yaml_name="bert.yaml";;
            esac;;
        pytorch)
            yaml_name="conf.yaml";;
    esac
    yaml_config="${model_src_dir}/${yaml_name}"
    echo "Yaml config: ${yaml_config}"
}


# ----- Get strategy -----

function get_strategy {
    if [ ! -f ${yaml_config} ]; then
        echo "Not found yaml config at \"${yaml_config}\" location."
        exit 1
    fi

    update_yaml_params=""
    # Replace tuning strategy in yaml file
    if [ "${tuning_strategy}" != "" ]; then
        update_yaml_params="${update_yaml_params} --strategy=${tuning_strategy}"
    fi

    if [ "${framework}" == "pytorch" ] && [ "${model_type}" == "cnn" ]; then
        update_yaml_params="${update_yaml_params} --calib-data=${dataset_location}/train --eval-data=${dataset_location}/val"
    fi

    if [ "${update_yaml_params}" != "" ]; then
        python ${WORKSPACE}/ilit-validation/scripts/update_yaml_config.py --yaml=${yaml_config} ${update_yaml_params}
    fi

    count=$(grep -c 'strategy: ' "${yaml_config}") || true  # Prevent from exiting when 'strategy' not found
    if [ ${count} == 0 ]; then
      strategy='basic'
    else
      strategy=$(grep 'strategy: ' ${yaml_config} | awk -F 'strategy: ' '{print$2}')
    fi

    echo "Tuning strategy: ${strategy}"
}
