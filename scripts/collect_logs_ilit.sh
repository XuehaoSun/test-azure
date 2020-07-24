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
        *)
            echo "Error: No such parameter: ${var}"
            exit 1
        ;;
    esac
done

echo "---- $framework, $model ----"

log_file="${framework}/${model}/${framework}-${model}.log"

strategy=$(grep 'Tuning strategy:' ${log_file} |tail -1| awk -F ': ' '{print $2}')
tune_time=$(grep 'Tuning time spend:' ${log_file} | awk -F ' ' '{print $4}')
echo "${framework};${model};${strategy};${tune_time}" |tee -a ${WORKSPACE}/tuning_info.log

benchmark_log_file="${framework}/${model}/${framework}_${model}_${precision}_${mode}_benchmark.log"

if [ ${framework} = "tensorflow" ]; then

  if [ ${precision} = "fp32" ]; then
    PRECISION='FP32'
  else
    PRECISION='INT8'
  fi

  if [ ${mode} = "throughput" ]; then

    bs=$(grep 'input_model accuracy batch_size:' ${benchmark_log_file} | awk -F ' ' '{print $4}')
    accuracy=$(grep 'input_model accuracy:' ${benchmark_log_file} | awk -F ' ' '{print $3}')
    echo "$framework;CLX8280;${PRECISION};$model;Inference;Accuracy;${bs};${accuracy};${BUILD_URL}artifact/$benchmark_log_file" |tee -a ${WORKSPACE}/summary.log
    bs=$(grep 'input_model throughput batch_size:' ${benchmark_log_file} | awk -F ' ' '{print $4}')
    throughput=$(grep 'input_model throughput:' ${benchmark_log_file} | awk -F ' ' '{print $3}')
    echo "$framework;CLX8280;${PRECISION};$model;Inference;Throughput;${bs};${throughput};${BUILD_URL}artifact/$benchmark_log_file" |tee -a ${WORKSPACE}/summary.log

  else

    throughput=$(grep 'input_model throughput:' ${benchmark_log_file} | awk -F ' ' '{print $3}')
    latency=$(echo | awk -v t=$throughput '{ r = 1000 / t; printf("%.2f", r)}')
    echo "$framework;CLX8280;${PRECISION};$model;Inference;Latency;1;${latency};${BUILD_URL}artifact/$benchmark_log_file" |tee -a ${WORKSPACE}/summary.log

  fi

else

  bs=$(grep 'input_model throughput batch_size:' ${log_file} | awk -F ' ' '{print $4}')
  throughput=$(grep 'input_model throughput:' ${log_file} | awk -F ' ' '{print $3}')
  echo "$framework;CLX8280;FP32;$model;Inference;Throughput;${bs};${throughput};${BUILD_URL}artifact/$log_file" |tee -a ${WORKSPACE}/summary.log
  bs=$(grep 'q_model throughput batch_size:' ${log_file} | awk -F ' ' '{print $4}')
  throughput=$(grep 'q_model throughput:' ${log_file} | awk -F ' ' '{print $3}')
  echo "$framework;CLX8280;INT8;$model;Inference;Throughput;${bs};${throughput};${BUILD_URL}artifact/$log_file" |tee -a ${WORKSPACE}/summary.log

  bs=$(grep 'input_model accuracy batch_size:' ${log_file} | awk -F ' ' '{print $4}')
  accuracy=$(grep 'input_model accuracy:' ${log_file} | awk -F ' ' '{print $3}')
  echo "$framework;CLX8280;FP32;$model;Inference;Accuracy;${bs};${accuracy};${BUILD_URL}artifact/$log_file" |tee -a ${WORKSPACE}/summary.log
  bs=$(grep 'q_model accuracy batch_size:' ${log_file} | awk -F ' ' '{print $4}')
  accuracy=$(grep 'q_model accuracy:' ${log_file} | awk -F ' ' '{print $3}')
  echo "$framework;CLX8280;INT8;$model;Inference;Accuracy;${bs};${accuracy};${BUILD_URL}artifact/$log_file" |tee -a ${WORKSPACE}/summary.log

fi
