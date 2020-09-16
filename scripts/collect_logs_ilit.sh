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

tuning_file="${framework}/${model}/${framework}-${model}-tune.log"

if [ "${mode}" == "tuning" ]; then
  strategy=$(grep 'Tuning strategy:' ${tuning_file} | tail -1 | awk -F ': ' '{print $2}')
  tune_count=$(grep -F 'Tune result is: [' ${tuning_file} | wc -l)
  tune_time=$(grep 'Tuning time spend:' ${tuning_file} | awk -F ' ' '{print $4}'| sed 's/.$//g')
  fp32_pb_size=$(grep 'The input PB size is:' ${tuning_file} |sed 's/[^0-9]//g')
  int8_pb_size=$(grep 'The output PB size is:' ${tuning_file} |sed 's/[^0-9]//g')
  total_mem_size=$(grep 'Total resident size' ${tuning_file} |sed 's/[^0-9]//g')
  max_mem_size=$(grep 'Maximum resident set size' ${tuning_file} |sed 's/[^0-9]//g')
  mem_percentage=$(echo |awk -v total=${total_mem_size} -v max=${max_mem_size} '{printf("%.0f%", max / total * 100)}')
  echo "${framework};${model};${strategy};${tune_time};${tune_count};${BUILD_URL}artifact/$tuning_file;${fp32_pb_size};${int8_pb_size};${mem_percentage}" | tee -a ${WORKSPACE}/tuning_info.log

  if [ "${framework}" == "pytorch" ] && [ "${mr}" == "" ]; then
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
      
    # Read latency result
    if [ "${framework}" != 'pytorch' ]; then
      benchmark_mode="latency"
      log_file="${framework}/${model}/${framework}_${model}_int8_${benchmark_mode}"

      bs=$(grep 'Batch size =' $(ls ${log_file}* | head -1) | awk -F ' ' '{print $4}')
      latency=$(grep "Latency: " ${log_file}*  | sed -e s"/.*: //" | sed -e s"; ms;;" | awk 'BEGIN{sum=0}{sum+=$1}END{printf("%.3f\n",sum/NR)}')
      echo "${framework};CLX8280;INT8;${model};Inference;Latency;${bs};${latency};${BUILD_URL}artifact/$(ls ${log_file}* | head -1)" | tee -a ${WORKSPACE}/summary.log

      log_file="${framework}/${model}/${framework}_${model}_fp32_${benchmark_mode}"

      bs=$(grep 'Batch size =' $(ls ${log_file}* | head -1) | awk -F ' ' '{print $4}')
      latency_fp32=$(grep "Latency: " ${log_file}*  | sed -e s"/.*: //" | sed -e s"; ms;;" | awk 'BEGIN{sum=0}{sum+=$1}END{printf("%.3f\n",sum/NR)}')
      echo "${framework};CLX8280;FP32;${model};Inference;Latency;${bs};${latency_fp32};${BUILD_URL}artifact/$(ls ${log_file}* | head -1)" | tee -a ${WORKSPACE}/summary.log
      # for test
      yum -y install bc
      if [ $(echo "$latency > $latency_fp32"|bc) -eq 1 ];then
        echo "performance regression" > ${WORKSPACE}/perf_regression.log
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
  bs=$(for param in $(grep 'batch_size=' ${log_file}); do if [[ ${param} =~ "batch_size" ]]; then echo ${param} | cut -f 2 -d =; fi ; done)
  accuracy=$(grep 'Accuracy: ' ${log_file} | awk -F ' ' '{print $2}')
  echo "$framework;CLX8280;${PRECISION};$model;Inference;Accuracy;${bs};${accuracy};${BUILD_URL}artifact/${log_file}" | tee -a ${WORKSPACE}/summary.log
fi
