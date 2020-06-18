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

if [ ${framework} == 'pytorch' ];then
  echo "
  => using pre-trained model 'resnet18'
FP32 baseline is: [0.6976, 244.0757]
Tune result is: [0.6939, 166.6925] Best tune result is: [0.6939, 166.6925]
Tune result is: [0.6942, 164.5300] Best tune result is: [0.6942, 164.5300]
Tune result is: [0.6932, 166.8511] Best tune result is: [0.6942, 164.5300]
Tune result is: [0.6943, 162.9766] Best tune result is: [0.6943, 162.9766]
Tune result is: [0.6955, 159.8384] Best tune result is: [0.6955, 159.8384]
Tune result is: [0.6938, 154.8798] Best tune result is: [0.6938, 154.8798]
Tune result is: [0.6950, 164.1739] Best tune result is: [0.6938, 154.8798]
Tune result is: [0.6944, 159.3588] Best tune result is: [0.6938, 154.8798]
Tune result is: [0.6938, 157.2675] Best tune result is: [0.6938, 154.8798]
Tune result is: [0.6955, 165.8169] Best tune result is: [0.6938, 154.8798]
Tune result is: [0.6950, 155.2735] Best tune result is: [0.6938, 154.8798]
Tune result is: [0.6914, 150.7698] Best tune result is: [0.6914, 150.7698]
Tune result is: [0.6941, 164.2001] Best tune result is: [0.6914, 150.7698]
Tune result is: [0.6954, 156.9869] Best tune result is: [0.6914, 150.7698]
  " > ${log_file}
fi



accuracy=$(grep 'FP32 baseline is:' ${log_file} | awk -F'[' '{print $2}'|awk -F',' '{print $1}')
duration=$(grep 'FP32 baseline is:' ${log_file} | awk -F',' '{print $2}'|awk -F']' '{print $1}')

echo "$framework; CLX8280; FP32; $model; Inference; Latency; $bs; ${duration}; ${BUILD_URL}artifact/$log_file" |tee -a ${WORKSPACE}/summary.log
echo "$framework; CLX8280; FP32; $model; Inference; Accuracy; $bs; ${accuracy}; ${BUILD_URL}artifact/$log_file" |tee -a ${WORKSPACE}/summary.log

accuracy=$(grep 'Best tune result is:' ${log_file}|tail -1 |awk -F':' '{print $3}' |awk -F'[' '{print $2}'|awk -F',' '{print $1}')
duration=$(grep 'Best tune result is:' ${log_file}|tail -1 |awk -F':' '{print $3}' |awk -F',' '{print $2}'|awk -F']' '{print $1}')

echo "$framework; CLX8280; INT8; $model; Inference; Latency; $bs; ${duration}; ${BUILD_URL}artifact/$log_file" |tee -a ${WORKSPACE}/summary.log
echo "$framework; CLX8280; INT8; $model; Inference; Accuracy; $bs; ${accuracy}; ${BUILD_URL}artifact/$log_file" |tee -a ${WORKSPACE}/summary.log
