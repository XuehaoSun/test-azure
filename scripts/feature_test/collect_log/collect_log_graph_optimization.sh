#!/bin/bash
set -x

echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"

function main {
    check_graph_optimization_fp32
    check_graph_optimization_bf16
    check_graph_optimization_auto-mix
}

function check_graph_optimization_fp32 {
    control_phrase="Found a converted model which meet accuracy goal."

    if [ $(grep "${control_phrase}" ${WORKSPACE}/${feature_name}/graph_optimization_fp32.log | wc -l) == 1 ];then
        test_status="pass"
    else
        test_status="fail"
    fi

    echo "graph_optimization_fp32_cpx;${test_status};${BUILD_URL}artifact/${feature_name}/graph_optimization_fp32.log" | tee -a ${summaryLog}
}

function check_graph_optimization_bf16 {
    control_phrase="Found a converted model which meet accuracy goal."

    if [ $(grep "${control_phrase}" ${WORKSPACE}/${feature_name}/graph_optimization_bf16.log | wc -l) == 1 ];then
        test_status="pass"
    else
        test_status="fail"
    fi

    echo "graph_optimization_bf16_cpx;${test_status};${BUILD_URL}artifact/${feature_name}/graph_optimization_bf16.log" | tee -a ${summaryLog}
}

function check_graph_optimization_auto-mix {
    control_phrase="Found a converted model which meet accuracy goal."

    if [ $(grep "${control_phrase}" ${WORKSPACE}/${feature_name}/graph_optimization_auto-mix.log | wc -l) == 1 ];then
        test_status="pass"
    else
        test_status="fail"
    fi

    echo "graph_optimization_auto-mix_cpx;${test_status};${BUILD_URL}artifact/${feature_name}/graph_optimization_auto-mix.log" | tee -a ${summaryLog}
}


main