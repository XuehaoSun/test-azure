#!/bin/bash
set -x

function main {
    init_params "$@"
    set_environment
    if [ ${model} = 'ssd_resnet50_v1' ]; then
      model_src_dir=${WORKSPACE}/ilit-models/examples/${framework}/object_detection
      init_obj_cmd
      cd ${model_src_dir}
      pip install -r requirements.txt
    else
      model_src_dir=${WORKSPACE}/ilit-models/examples/${framework}/image_recognition
      init_cnn_cmd
    fi

    if [ "${model_src_dir}" != "" ];then
        cd ${model_src_dir}
    else
        echo "ERROR model_src_dir"
    fi
    git remote -v
    git branch
    git show |head -5

    generate_core
}

# init params
function init_params {
    framework='tensorflow'
    model='resnet50'

    for var in "$@"
    do
        case $var in
            --framework=*)
                framework=$(echo $var |cut -f2 -d=)
            ;;
            --model=*)
                model=$(echo $var |cut -f2 -d=)
            ;;
            --conda_env_name=*)
                conda_env_name=$(echo $var |cut -f2 -d=)
            ;;
            *)
                echo "Error: No such parameter: ${var}"
                exit 1
            ;;
        esac
    done
}

# init_obj_cmd
function init_obj_cmd {

  input_graph=/tf_dataset/pre-train-model-oob/object_detection/${model}/frozen_inference_graph.pb
  if [ ${model} = 'ssd_resnet50_v1' ]; then
    yaml=ssd_resnet50_v1.yaml
  fi

  cmd="python infer_detections.py \
      --batch-size 1 \
      --input-graph ${input_graph} \
      --data-location /tf_dataset/tensorflow/coco_val.record \
      --accuracy-only \
      --config ${yaml}"

}

# init_cnn_cmd
function init_cnn_cmd {
    extra_cmd=''
    input="input"
    output="predict"
    image_size=224
    if [ "${model}" = "resnet50v1.0" ];then
        extra_cmd=' --resize_method crop'
        input_graph=/tf_dataset/pre-trained-models/resnet50/fp32/freezed_resnet50.pb
        yaml=${model_src_dir}/resnet50_v1.yaml
    elif [ "${model}" = "resnet50v1.5" ]; then
        extra_cmd=' --resize_method=crop --r_mean 123.68 --g_mean 116.78 --b_mean 103.94'
        input_graph=/tf_dataset/pre-trained-models/resnet50v1_5/fp32/resnet50_v1.pb
        input="input_tensor"
        output="softmax_tensor"
        yaml=${model_src_dir}/resnet50_v1_5.yaml
    elif [ "${model}" = "resnet101" ]; then
        extra_cmd=' --resize_method vgg --label_adjust'
        input_graph=/tf_dataset/pre-trained-models/resnet101/fp32/optimized_graph.pb
        output="resnet_v1_101/SpatialSqueeze"
        yaml=${model_src_dir}/resnet101.yaml
    elif [ "${model}" = "inception_v1" ]; then
        extra_cmd=' --resize_method bilinear'
        input_graph=/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_${model}.pb
        output=InceptionV1/Logits/Predictions/Reshape_1
        yaml=${model_src_dir}/inceptionv1.yaml
    elif [ "${model}" = "inception_v2" ]; then
        extra_cmd=' --resize_method bilinear'
        input_graph=/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_${model}.pb
        output=InceptionV2/Predictions/Reshape_1
        yaml=${model_src_dir}/inceptionv2.yaml
    elif [ "${model}" = "inception_v3" ]; then
        extra_cmd=' --resize_method bilinear'
        input_graph=/tf_dataset/pre-trained-models/inceptionv3/fp32/freezed_inceptionv3.pb
        image_size=299
        yaml=${model_src_dir}/inceptionv3.yaml
    elif [ "${model}" = "inception_v4" ]; then
        extra_cmd=' --resize_method bilinear'
        output="InceptionV4/Logits/Predictions"
        input_graph=/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_${model}.pb
        image_size=299
        yaml=${model_src_dir}/inceptionv4.yaml
    elif [ "${model}" = "inception_resnet_v2" ]; then
        extra_cmd="--resize_method bilinear"
        output="InceptionResnetV2/Logits/Predictions"
        input_graph=/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_${model}.pb
        image_size=299
        yaml=${model_src_dir}/irv2.yaml
    elif [ "${model}" = "mobilenetv1" ];then
        extra_cmd=' --resize_method bilinear'
        output="MobilenetV1/Predictions/Reshape_1"
        input_graph=/tf_dataset/pre-trained-models/mobilenet_v1/fp32/mobilenet_v1_1.0_224_frozen.pb
        yaml=${model_src_dir}/mobilenet_v1.yaml
    elif [ "${model}" = "mobilenetv2" ]; then
        extra_cmd=' --resize_method bilinear'
        output="MobilenetV2/Predictions/Reshape_1"
        input_graph=/tf_dataset/pre-train-model-slim/pbfile/frozen_pb/frozen_mobilenet_v2.pb
        yaml=${model_src_dir}/mobilenet_v2.yaml
    fi

    cmd="python main.py \
            --input_graph ${input_graph} \
            --image_size ${image_size} \
            --input ${input} \
            --output ${output} \
            --data_location /tf_dataset/dataset/imagenet \
            --config ${yaml} \
            ${extra_cmd}"
}

# environment
function set_environment {
    export KMP_BLOCKTIME=1
    export KMP_AFFINITY=granularity=fine,verbose,compact,1,0
    export TF_MKL_OPTIMIZE_PRIMITIVE_MEMUSE=false
    export OMP_NUM_THREADS=28

    export PATH=${HOME}/miniconda3/bin/:$PATH
    source activate ${conda_env_name}
    export PYTHONPATH=${PYTHONPATH}:${WORKSPACE}/ilit-models/
    python -V
    pip list
    c_ilit=$(pip list | grep -c 'ilit')
    if [ ${c_ilit} = 0 ]; then
      pip install /tf_dataset/ilit/1.0a_release/ilit-1.0a0-py3-none-any.whl
    else
      pip uninstall ilit -y
      pip install /tf_dataset/ilit/1.0a_release/ilit-1.0a0-py3-none-any.whl
    fi
    pip list
}

# run
function generate_core {

    # get strategy
    count=$(grep -c 'strategy: ' ${yaml})
    if [ ${count} = 0 ]; then
      strategy='basic'
    else
      strategy=$(grep 'strategy: ' ${yaml} | awk -F 'strategy: ' '{print$2}')
    fi
    echo "Tuning strategy: ${strategy}"

    # run tuning
    export ILIT_DEBUG="/tmp/${model}_quantize.pb"
    run_cmd="numactl -l -C 0-27,56-83 ${cmd} --tune"
    eval "${run_cmd}"
    echo "QUANTIZE PB SAVED IN ${ILIT_DEBUG}"
    echo "HOSTNAME IS ${HOSTNAME}"

    # run fp32 benchmark
    run_cmd="numactl -l -C 0-27,56-83 ${cmd} --fp32_benchmark"
    eval "${run_cmd}"

}

main "$@"
