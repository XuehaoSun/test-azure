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
        *)
            echo "Error: No such parameter: ${var}"
            exit 1
        ;;
    esac
done

echo "---- $framework, $model ----"

log_file="${framework}/${model}/${framework}-${model}.log"

if [ ${framework} == 'tensorflow' ] || [ ${framework} == 'mxnet' ] || [ ${framework} == 'pytorch' ]; then

  latency=$(grep 'input_model latency:' ${log_file} | awk -F ' ' '{print $3}')
  echo "$framework;CLX8280;FP32;$model;Inference;Latency;1;${latency};${BUILD_URL}artifact/$log_file" |tee -a ${WORKSPACE}/summary.log
  latency=$(grep 'q_model latency:' ${log_file} | awk -F ' ' '{print $3}')
  echo "$framework;CLX8280;INT8;$model;Inference;Latency;1;${latency};${BUILD_URL}artifact/$log_file" |tee -a ${WORKSPACE}/summary.log

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

else

  accuracy=$(grep 'FP32 baseline is:' ${log_file} | awk -F'[' '{print $2}'|awk -F',' '{print $1}')
  duration=$(grep 'FP32 baseline is:' ${log_file} | awk -F',' '{print $2}'|awk -F']' '{print $1}')

  echo "$framework;CLX8280;FP32;$model;Inference;Latency;$bs;${duration};${BUILD_URL}artifact/$log_file" |tee -a ${WORKSPACE}/summary.log
  echo "$framework;CLX8280;FP32;$model;Inference;Accuracy;$bs;${accuracy};${BUILD_URL}artifact/$log_file" |tee -a ${WORKSPACE}/summary.log

  accuracy=$(grep 'Best tune result is:' ${log_file}|tail -1 |awk -F':' '{print $3}' |awk -F'[' '{print $2}'|awk -F',' '{print $1}')
  echo "$framework;CLX8280;INT8;$model;Inference;Accuracy;$bs;${accuracy};${BUILD_URL}artifact/$log_file" |tee -a ${WORKSPACE}/summary.log

fi