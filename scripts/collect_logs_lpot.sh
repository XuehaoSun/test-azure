#!/bin/bash

cpu="unknown"
for var in "$@"
do 
    case $var in
        --framework=*)
            framework=$(echo $var |cut -f2 -d=);;
        --model=*)
            model=$(echo $var |cut -f2 -d=);;
        --precision=*)
            precision=$(echo $var |cut -f2 -d=);;
        --mode=*)
            mode=$(echo $var |cut -f2 -d=);;
        --cpu=*)
            cpu=$(echo $var |cut -f2 -d=);;
        --os=*)
            os=$(echo $var |cut -f2 -d=);;
        --mr=*)
            mr=$(echo $var |cut -f2 -d=);;
        --perf_steps=*)
            perf_steps=$(echo $var |cut -f2 -d=);;
        *)
            echo "Error: No such parameter: ${var}"
            exit 1;;
    esac
done

echo "---- $framework, $model ----"

unknown_platforms=("unknown" "*" "any")

platform="Unknown"
if [ "${mode}" == "tuning" ]; then
    if [[ " ${unknown_platforms[@]} " =~ " ${cpu} " ]]; then
        if [ $(ls ${framework}/${model}/${framework}-${model}-${os}-*-tune.log | wc -l) != 0 ]; then
            platform=$(ls ${framework}/${model}/${framework}-${model}-${os}-*-tune.log | tail -n 1 | awk -F '-' '{print $(NF-1)}')  # Get one before last element as model name can contain dash
        fi
    else
        platform=${cpu}
    fi
    echo "Platform: ${platform}"

    tuning_file="${framework}/${model}/${framework}-${model}-${os}-${platform}-tune.log"
    # Temporary change for helloworld_keras
    if [ "${model}" == "helloworld_keras" ]; then
        if [ ! -f ${tuning_file} ]; then
            echo "Helloworld Keras log does not exists. Skipping."
            exit 0
        fi

        if [ $(grep 'Inference is done.' ${tuning_file} | wc -l) == 1 ]; then
            status="SUCCESS";
        else
            status="FAILURE"
        fi

        echo -e "Helloworld Keras,${status},${BUILD_URL}artifact/$tuning_file\n" | tee -a ${WORKSPACE}/summary_overview.log
        exit 0
    fi
    strategy=$(grep 'Tuning strategy:' ${tuning_file} | tail -1 | awk -F ': ' '{print $2}' | tr -d '\r\n')
    tune_count=$(grep -F 'Best tune result is:' ${tuning_file} | wc -l)
    tune_time=$(grep 'Tuning time spend:' ${tuning_file} | awk -F ' ' '{print $4}' | tr -d '\r\n' | sed 's/.$//g')
    fp32_pb_size=$(grep 'The input model size is:' ${tuning_file} |sed 's/[^0-9]//g')
    int8_pb_size=$(grep 'The output model size is:' ${tuning_file} |sed 's/[^0-9]//g')
    total_mem_size=$(grep 'Total resident size' ${tuning_file} |sed 's/[^0-9]//g')
    max_mem_size=$(grep 'Maximum resident set size' ${tuning_file} |sed 's/[^0-9]//g')
    mem_percentage="N/A"
    pure_tune_1_start=$(grep 'FP32 baseline is:' ${tuning_file} | head -1 | awk -F ' \\[' '{print $1}')
    start_seconds=$(date --date="$pure_tune_1_start" +%s)
    pure_tune_1_end=$(grep 'Converted graph file is saved to:' ${tuning_file} | head -1 | awk -F ' \\[' '{print $1}')
    end_seconds=$(date --date="$pure_tune_1_end" +%s)
    pure_tune_1_time=$((end_seconds-start_seconds))
    echo "$model: $pure_tune_1_time" >> ${WORKSPACE}/pure_tuning_time.log
    if [ ! -z ${total_mem_size} ] && [ ! -z ${max_mem_size} ]; then
        mem_percentage=$(echo |awk -v total=${total_mem_size} -v max=${max_mem_size} '{printf("%.0f%", max / total * 100)}')
    fi
    echo "${os};${platform};${framework};${model};${strategy};${tune_time};${tune_count};${BUILD_URL}artifact/$tuning_file;${fp32_pb_size};${int8_pb_size};${mem_percentage}" | tee -a ${WORKSPACE}/tuning_info.log

    if [ "${mr}" != "" ]; then
        # Read result from tuning log
        accuracy=$(grep -F 'Best tune result is: [' ${tuning_file} | tail -1 | awk -F ': ' '{print $2}' | sed 's/[][]//g' | awk -F ', ' '{print $1}' | tr -d '\r\n')
        echo "${os};${platform};${framework};INT8;${model};Inference;Accuracy;;${accuracy};${BUILD_URL}artifact/$tuning_file" | tee -a ${WORKSPACE}/summary.log
        accuracy_fp32=$(grep -F 'FP32 baseline is: [' ${tuning_file} | tail -1 | awk -F ': ' '{print $2}' | sed 's/[][]//g' | awk -F ', ' '{print $1}' | tr -d '\r\n')
        echo "${os};${platform};${framework};FP32;${model};Inference;Accuracy;;${accuracy_fp32};${BUILD_URL}artifact/$tuning_file" | tee -a ${WORKSPACE}/summary.log
          
        # Read latency result
        if [ "${perf_steps}" != "" ]; then
          benchmark_mode="latency"
          log_file="${framework}/${model}/${framework}-${model}-int8-${benchmark_mode}-${os}-${platform}"
          bs=$(grep 'Batch size =' $(ls ${log_file}* | head -1) | awk -F '=' '{print $2}'| sed 's/[^0-9]//g')
          latency=$(python ${WORKSPACE}/lpot-validation/scripts/get_stable_iteration.py --framework "${framework}" --model "${model}" --datatype "int8" --mode "${benchmark_mode}" --logs-dir "${framework}/${model}" --start_skip 200 --end_skip 200 --s-to-ms)
          echo "${os};${platform};${framework};INT8;${model};Inference;Latency;${bs};${latency};${BUILD_URL}artifact/$(ls ${log_file}* | head -1)" | tee -a ${WORKSPACE}/summary.log

          log_file="${framework}/${model}/${framework}-${model}-fp32-${benchmark_mode}-${os}-${platform}"
          bs=$(grep 'Batch size =' $(ls ${log_file}* | head -1) | awk -F '=' '{print $2}'| sed 's/[^0-9]//g')
          latency_fp32=$(python ${WORKSPACE}/lpot-validation/scripts/get_stable_iteration.py --framework "${framework}" --model "${model}" --datatype "fp32" --mode "${benchmark_mode}" --logs-dir "${framework}/${model}" --start_skip 200 --end_skip 200 --s-to-ms)
          echo "${os};${platform};${framework};FP32;${model};Inference;Latency;${bs};${latency_fp32};${BUILD_URL}artifact/$(ls ${log_file}* | head -1)" | tee -a ${WORKSPACE}/summary.log
          # for test
          yum -y install bc
          if [ $(echo "$latency > $latency_fp32"|bc) -eq 1 ];then
            echo "int8/fp32 performance regression" > ${WORKSPACE}/perf_regression.log
          fi
        fi
    fi
    exit 0
fi


if [ ${precision} = "fp32" ]; then
    PRECISION='FP32'
else
    PRECISION='INT8'
fi

if [[ " ${unknown_platforms[@]} " =~ " ${cpu} " ]]; then
    if [ $(ls ${framework}/${model}/${framework}-${model}-${precision}-${mode}-${os}-* | wc -l) != 0 ]; then
        platform=$(ls ${framework}/${model}/${framework}-${model}-${precision}-${mode}-${os}-* | tail -n 1 | awk -F '-' '{print $(NF-3)}')
        if [ ${mode} == "accuracy" ]; then
            platform=$(ls ${framework}/${model}/${framework}-${model}-${precision}-${mode}-${os}-* | tail -n 1 | awk -F '-' '{print $(NF)}' | cut -d '.' -f 1)
        fi
    fi
else
    platform=${cpu}
fi

if [ ${platform} == "Unknown" ]; then
    if [ $(ls ${WORKSPACE}/tuning_info.log | wc -l) == 1 ]; then
        if [ $(cat ${WORKSPACE}/tuning_info.log | grep "^${os};.*;${framework};${model};" | wc -l) == 1 ]; then
            platform=$(cat ${WORKSPACE}/tuning_info.log | grep "^${os};.*;${framework};${model};" | cut -d ";" -f 2)
        fi
    fi
fi
echo "Platform: ${platform}"

log_file="${framework}/${model}/${framework}-${model}-${precision}-${mode}-${os}-${platform}"

if [ "${mode}" == "throughput" ]; then
    bs=$(grep 'Batch size =' $(ls ${log_file}* | head -1) | awk -F '=' '{print $2}'| head -1 |sed 's/[^0-9]//g' | tr -d '\r\n')
    throughput=$(grep "Throughput: " ${log_file}*  | sed -e s";.*: ;;" | sed -e s"; images/sec;;" | awk 'BEGIN{sum=0}{sum+=$1}END{print sum}')
    echo "${os};${platform};${framework};${PRECISION};${model};Inference;Throughput;${bs};${throughput};${BUILD_URL}artifact/$(ls ${log_file}* | head -1)" | tee -a ${WORKSPACE}/summary.log
fi

if [ "${mode}" == "latency" ]; then
    bs=$(grep 'Batch size =' $(ls ${log_file}* | head -1) | awk -F '=' '{print $2}'| head -1 | sed 's/[^0-9]//g' | tr -d '\r\n')
    latency=$(grep "Latency: " ${log_file}*  | sed -e s"/.*: //" | sed -e s"; ms;;" | awk 'BEGIN{sum=0}{sum+=$1}END{printf("%.3f\n",sum/NR)}')
    echo "${os};${platform};${framework};${PRECISION};${model};Inference;Latency;${bs};${latency};${BUILD_URL}artifact/$(ls ${log_file}* | head -1)" | tee -a ${WORKSPACE}/summary.log
fi

if [ "${mode}" == "accuracy" ]; then
    log_file="${framework}/${model}/${framework}-${model}-${precision}-${mode}-${os}-${platform}.log"
    bs=$(grep 'Batch size =' $(ls ${log_file}* | head -1) | awk -F '=' '{print $2}' | head -1 | sed 's/[^0-9]//g' | tr -d '\r\n')
    echo ${bs}
    if [ "${bs}" == "" ]; then
        bs=$(for param in $(grep 'batch_size=' ${log_file}); do if [[ ${param} =~ "batch_size" ]]; then echo ${param} | cut -f 2 -d =; break; fi ; done)
    fi
    accuracy=$(grep 'Accuracy: ' ${log_file} | awk -F ' ' '{print $2}' | tr -d '\r\n')
    if [ "${accuracy}" == "" ]; then
        accuracy=$(grep 'Accuracy is' ${log_file} | awk -F ' ' '{print $3}' | tr -d '\r\n')
    fi
    echo "${os};${platform};${framework};${PRECISION};$model;Inference;Accuracy;${bs};${accuracy};${BUILD_URL}artifact/${log_file}" | tee -a ${WORKSPACE}/summary.log
fi
