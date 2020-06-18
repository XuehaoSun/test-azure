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



echo "$framework; CLX8280; FP32; $model; Inference; Throughput; $bs; $value; ${BUILD_URL}artifact/$log_file" |tee -a ${WORKSPACE}/summary.log
echo "$framework; CLX8280; FP32; $model; Inference; Accuracy; $bs; $value; ${BUILD_URL}artifact/$log_file" |tee -a ${WORKSPACE}/summary.log
echo "$framework; CLX8280; INT8; $model; Inference; Throughput; $bs; $value; ${BUILD_URL}artifact/$log_file" |tee -a ${WORKSPACE}/summary.log
echo "$framework; CLX8280; INT8; $model; Inference; Accuracy; $bs; $value; ${BUILD_URL}artifact/$log_file" |tee -a ${WORKSPACE}/summary.log
