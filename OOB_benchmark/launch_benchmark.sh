#!/bin/bash

PATTERN='[-a-zA-Z0-9_]*='
for i in "$@"
do
    case $i in
        --model=*)
            model=`echo $i | sed "s/${PATTERN}//"`;;
        --input_model=*)
            input_model=`echo $i | sed "s/${PATTERN}//"`;;
        --precision=*)
            precision=`echo $i | sed "s/${PATTERN}//"`;;
        --conda_env_name=*)
            conda_env_name=`echo $i | sed "s/${PATTERN}//"`;;
        --verbose=*)
            verbose=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

output_path=${WORKSPACE}/benchmark_logs/${model}
if [ ! -d ${output_path} ]; then
    mkdir -p ${output_path}
fi

# Run Benchmark
main() {

    set_environment
    run_benchmark
    log_collect
}

function set_environment {

    export PATH=${HOME}/miniconda3/bin/:$PATH
    source activate ${conda_env_name}
    echo "Real conda environment..."
    conda info --env
    # install oob requirements
    pip install pyyaml

    cd ${WORKSPACE}/lpot-validation/OOB_benchmark/oob_models || true

    export KMP_BLOCKTIME=1
    export KMP_AFFINITY=granularity=fine,verbose,compact,1,0
    export TF_MKL_OPTIMIZE_PRIMITIVE_MEMUSE=false
    # default use block format
    export TF_ENABLE_MKL_NATIVE_FORMAT=0
    # for open ONEDNN
    export TF_ENABLE_ONEDNN_OPTS=1

    if [ "${verbose}" == "true" ]; then
        echo "open DNNL_VERBOSE..."
        export DNNL_VERBOSE=1
    fi


    if [ "${CPU_NAME}" == "spr" ]; then
        if [ "${precision}" == "int8" ] || [ "${precision}" == "bf16" ]; then
            export DNNL_MAX_CPU_ISA=AVX512_CORE_AMX
            echo "export DNNL_MAX_CPU_ISA=AVX512_CORE_AMX ..."
        fi
    fi
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
    ncores_per_instance=4
    export OMP_NUM_THREADS=${ncores_per_instance}
    iters=200
    num_warmup=10

    if [ "${verbose}" == "true" ]; then
        iters=10
        num_warmup=5
    fi

    # custom iteration
    if [[ "${latency_high_500[@]}" =~ "${model}" ]]; then
        iters=100
    elif [[ "${latency_high_1000[@]}" =~ "${model}" ]]; then
        iters=50
    fi


    if [ -f "run_benchmark.sh" ]; then
        run_cmd="bash run_benchmark.sh --topology=${model} --input_model=${input_model} --iters=${iters} --num_warmup=${num_warmup}"
    else
        echo "Not found run_benchmark file."
        exit 1
    fi

    echo "BENCHMARK RUNCMD: $run_cmd "
    logFile=${output_path}/${model}-${precision}

    for((j=0;$j<${ncores_per_socket};j=$(($j + ${ncores_per_instance}))));
    do
    end_core_num=$((j + ncores_per_instance -1))
    if [ ${end_core_num} -ge ${ncores_per_socket} ]; then
        end_core_num=$((ncores_per_socket-1))
    fi
    numactl -m 0 -C "$j-$end_core_num" \
        ${run_cmd} 2>&1|tee ${logFile}-${ncores_per_socket}-${ncores_per_instance}-${j}.log &
    done

    wait
}

function log_collect {
    if [ "${verbose}" == "true" ]; then
        mkdir ${WORKSPACE}/verbose_logs
        python parsednn.py -f ${logFile}*-4-0.log 2>&1 > "${WORKSPACE}/verbose_logs/${model}-${precision}-verbose.log"

    else
        bs=$(grep 'Batch size =' $(ls ${logFile}* | head -1) | awk -F '=' '{print $2}'| head -1 |sed 's/[^0-9]//g' | tr -d '\r\n')
        latency=$(grep "Latency: " ${logFile}*  | sed -e s"/.*: //" | sed -e s"; ms;;" | awk 'BEGIN{sum=0}{sum+=$1}END{printf("%.3f\n",sum/NR)}')
        echo "tensorflow,${precision},${model},Latency,${bs},${latency}" | tee -a ${WORKSPACE}/summary.log
    fi
}

main
