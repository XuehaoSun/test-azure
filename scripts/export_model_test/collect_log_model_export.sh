#!/bin/bash
set -x

echo "model_name: ${model_name}"
echo "model_export_summary_log: ${model_export_summary_log}"

function get_file {
    for file in "${WORKSPACE}/${model_name}/summary.log"; do
        if [ -f $file ] && [ $file=~"summary.log" ]; then
            echo "check file: $file"
            filename=${file##*/}
            params=${filename%.*}
            get_result $file
        fi
    done
}

function get_result {
    file=$1
    for line in $(cat ${file}); do
        echo $line | tee -a ${model_export_summary_log}
    done
}

get_file
