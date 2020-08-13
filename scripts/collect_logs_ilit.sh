#!/bin/bash

for var in "$@"
do 
    case $var in
        --framework=*)
            framework=$(echo $var |cut -f2 -d=)
        ;;
        --model=*)
            model=$(echo $var |cut -f2 -d=)
        ;;
        --precision=*)
            precision=$(echo $var |cut -f2 -d=)
        ;;
        --mode=*)
            mode=$(echo $var |cut -f2 -d=)
        ;;
        --mr=*)
            mr=$(echo $var |cut -f2 -d=)
        ;;
        *)
            echo "Error: No such parameter: ${var}"
            exit 1
        ;;
    esac
done

echo "---- $framework, $model ----"

benchmark_log_file="${framework}/${model}/${framework}_${model}_${precision}_${mode}_benchmark.log"
tuning_file="${framework}/${model}/${framework}-${model}-tune.log"

if [ "${mode}" == "tuning" ]; then
  strategy=$(grep 'Tuning strategy:' ${tuning_file} | tail -1 | awk -F ': ' '{print $2}')
  tune_count=$(grep -F 'Tune result is: [' ${tuning_file} | wc -l)
  tune_time=$(grep 'Tuning time spend:' ${tuning_file} | awk -F ' ' '{print $4}'| sed 's/.$//g')
  echo "${framework};${model};${strategy};${tune_time};${tune_count};${BUILD_URL}artifact/$tuning_file" | tee -a ${WORKSPACE}/tuning_info.log

  if [ "${framework}" == "pytorch" ]; then
    accuracy=$(grep -F 'Best tune result is: [' ${tuning_file} | tail -1 | awk -F ': ' '{print $2}' | sed 's/[][]//g' | awk -F ', ' '{print $1}')
    echo "${framework};CLX8280;INT8;${model};Inference;Throughput;;N/A;${BUILD_URL}artifact/$tuning_file" | tee -a ${WORKSPACE}/summary.log
    echo "${framework};CLX8280;INT8;${model};Inference;Accuracy;;${accuracy};${BUILD_URL}artifact/$tuning_file" | tee -a ${WORKSPACE}/summary.log
    accuracy_fp32=$(grep -F 'FP32 baseline is: [' ${tuning_file} | tail -1 | awk -F ': ' '{print $2}' | sed 's/[][]//g' | awk -F ', ' '{print $1}')
    echo "${framework};CLX8280;FP32;${model};Inference;Throughput;;N/A;${BUILD_URL}artifact/$tuning_file" | tee -a ${WORKSPACE}/summary.log
    echo "${framework};CLX8280;FP32;${model};Inference;Accuracy;;${accuracy_fp32};${BUILD_URL}artifact/$tuning_file" | tee -a ${WORKSPACE}/summary.log

  fi

  if [ "${mr}" != "" ]; then
    # Read result from tuning log
    accuracy=$(grep -F 'Best tune result is: [' ${tuning_file} | tail -1 | awk -F ': ' '{print $2}' | sed 's/[][]//g' | awk -F ', ' '{print $1}')
    echo "${framework};CLX8280;INT8;${model};Inference;Accuracy;;${accuracy};${BUILD_URL}artifact/$tuning_file" | tee -a ${WORKSPACE}/summary.log
    accuracy_fp32=$(grep -F 'FP32 baseline is: [' ${tuning_file} | tail -1 | awk -F ': ' '{print $2}' | sed 's/[][]//g' | awk -F ', ' '{print $1}')
    echo "${framework};CLX8280;FP32;${model};Inference;Accuracy;;${accuracy_fp32};${BUILD_URL}artifact/$tuning_file" | tee -a ${WORKSPACE}/summary.log

    log_file="${framework}/${model}/${framework}_${model}_${precision}_throughput"
    bs=$(grep 'Batch size =' $(ls ${log_file}* | head -1) | awk -F ' ' '{print $4}')
    throughput=$(grep "Throughput: " ${log_file}*  | sed -e s";.*: ;;" | sed -e s"; images/sec;;" | awk 'BEGIN{sum=0}{sum+=$1}END{print sum}')
    echo "${framework};CLX8280;${PRECISION};${model};Inference;Throughput;${bs};${throughput};${BUILD_URL}artifact/$(ls ${log_file}* | head -1)" | tee -a ${WORKSPACE}/summary.log

  fi

  exit 0
fi

if [ ${precision} = "fp32" ]; then
  PRECISION='FP32'
else
  PRECISION='INT8'
fi

log_file="${framework}/${model}/${framework}_${model}_${precision}_${mode}"

if [ "${mode}" == "throughput" ]; then
  bs=$(grep 'Batch size =' $(ls ${log_file}* | head -1) | awk -F ' ' '{print $4}')
  throughput=$(grep "Throughput: " ${log_file}*  | sed -e s";.*: ;;" | sed -e s"; images/sec;;" | awk 'BEGIN{sum=0}{sum+=$1}END{print sum}')
  echo "${framework};CLX8280;${PRECISION};${model};Inference;Throughput;${bs};${throughput};${BUILD_URL}artifact/$(ls ${log_file}* | head -1)" | tee -a ${WORKSPACE}/summary.log
fi

if [ "${mode}" == "latency" ]; then
    bs=$(grep 'Batch size =' $(ls ${log_file}* | head -1) | awk -F ' ' '{print $4}')
    latency=$(grep "Latency: " ${log_file}*  | sed -e s"/.*: //" | sed -e s"; ms;;" | awk 'BEGIN{sum=0}{sum+=$1}END{printf("%.3f\n",sum/NR)}')
    echo "${framework};CLX8280;${PRECISION};${model};Inference;Latency;${bs};${latency};${BUILD_URL}artifact/$(ls ${log_file}* | head -1)" | tee -a ${WORKSPACE}/summary.log
fi

if [ "${mode}" == "accuracy" ]; then
  log_file="${framework}/${model}/${framework}_${model}_${precision}_${mode}.log"
  bs=$(for param in $(grep 'batch_size=' tensorflow/resnet50v1.0/tensorflow_resnet50v1.0_fp32_accuracy.log); do if [[ ${param} =~ "batch_size" ]]; then echo ${param} | cut -f 2 -d =; fi ; done)
  accuracy=$(grep 'Accuracy: ' ${log_file} | awk -F ' ' '{print $2}')
  echo "$framework;CLX8280;${PRECISION};$model;Inference;Accuracy;${bs};${accuracy};${BUILD_URL}artifact/${log_file}" | tee -a ${WORKSPACE}/summary.log
fi
