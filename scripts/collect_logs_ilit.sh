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
  tune_time=$(grep 'Tuning time spend:' ${tuning_file} | awk -F ' ' '{print $4}'| sed 's/.$//g')
  echo "${framework};${model};${strategy};${tune_time}" | tee -a ${WORKSPACE}/tuning_info.log

  if [ "${mr}" != "" ]; then
    # Read result from tuning log
    accuracy=$(grep -F 'Tune result is: [' ${tuning_file} | awk -F ': ' '{print $2}' | sed 's/[][]//g' | awk -F ', ' '{print $1}')
    throughput=$(grep -F 'Tune result is: [' ${tuning_file} | awk -F ': ' '{print $2}' | sed 's/[][]//g' | awk -F ', ' '{print $2}')
    echo "${framework};CLX8280;INT8;${model};Inference;Throughput;;${throughput};${BUILD_URL}artifact/$tuning_file" | tee -a ${WORKSPACE}/summary.log
    echo "${framework};CLX8280;INT8;${model};Inference;Accuracy;;${accuracy};${BUILD_URL}artifact/$tuning_file" | tee -a ${WORKSPACE}/summary.log
    accuracy_fp32=$(grep -F 'FP32 baseline is: [' ${tuning_file} | awk -F ': ' '{print $2}' | sed 's/[][]//g' | awk -F ', ' '{print $1}')
    throughput_fp32=$(grep -F 'FP32 baseline is: [' ${tuning_file} | awk -F ': ' '{print $2}' | sed 's/[][]//g' | awk -F ', ' '{print $2}')
    echo "${framework};CLX8280;FP32;${model};Inference;Throughput;;${throughput_fp32};${BUILD_URL}artifact/$tuning_file" | tee -a ${WORKSPACE}/summary.log
    echo "${framework};CLX8280;FP32;${model};Inference;Accuracy;;${accuracy_fp32};${BUILD_URL}artifact/$tuning_file" | tee -a ${WORKSPACE}/summary.log
  fi
  exit 0
fi

if [ ${precision} = "fp32" ]; then
  PRECISION='FP32'
else
  PRECISION='INT8'
fi

bs=$(grep 'input_model accuracy batch_size:' ${benchmark_log_file} | awk -F ' ' '{print $4}')
accuracy=$(grep 'input_model accuracy:' ${benchmark_log_file} | awk -F ' ' '{print $3}')
echo "$framework;CLX8280;${PRECISION};$model;Inference;Accuracy;${bs};${accuracy};${BUILD_URL}artifact/$benchmark_log_file" | tee -a ${WORKSPACE}/summary.log
bs=$(grep 'input_model throughput batch_size:' ${benchmark_log_file} | awk -F ' ' '{print $4}')
throughput=$(grep 'input_model throughput:' ${benchmark_log_file} | awk -F ' ' '{print $3}')
echo "${framework};CLX8280;${PRECISION};${model};Inference;Throughput;${bs};${throughput};${BUILD_URL}artifact/$benchmark_log_file" | tee -a ${WORKSPACE}/summary.log
