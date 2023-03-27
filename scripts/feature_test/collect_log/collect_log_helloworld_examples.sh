#!/bin/bash
set -x

echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"

function main {
    CPU_NAME=$(cat "${WORKSPACE}/${feature_name}/cpu_name.log")
    for i in `seq 7`
    do
        check_tf_example_status ${i}
    done
}

function check_tf_example_status {
    example_number=${1}
    test_status="check"
    test_phrase="Tune [1-9]* result is: \[Accuracy.*: [0-9]*.[0-9]*|[0-9]*\.[0-9]*, Duration.*: [0-9]*\.[0-9]*|[0-9]*\.[0-9]*\], Best tune result is: \[Accuracy: [0-9]*\.[0-9]*, Duration.*: [0-9]*\.[0-9]*\]"
    if [[ ${example_number} == 2 ]]; then
        test_phrase="Inference is done."
    fi

    if [[ ${example_number} == 4 ]]; then
        test_phrase="Found a quantized model which meet accuracy goal."
    fi

    if [[ $(grep -c "${test_phrase}" ${WORKSPACE}/${feature_name}/tf_example${example_number}.log) == 1 ]]; then
        status="pass"
    else
        status="fail"
    fi
    test_status=${status}

    if [[ "${example_number}" == 5 ]]; then
        accuracy_count=$(grep -c 'Throughput sum \[samples/second\].*[0-9]*\.[0-9]*' ${WORKSPACE}/${feature_name}/tf_example${example_number}.log)
        if [[ ${status} == "pass" ]] && [[ ${accuracy_count} == 1 ]]; then
            test_status="pass"
        else
            test_status="fail"
        fi
    fi

    if [[ "${example_number}" == 6 ]]; then
        performance_result_count=$(grep -c 'Throughput: ' ${WORKSPACE}/${feature_name}/tf_example${example_number}.log)
        if [[ ${status} == "pass" ]] && [[ ${performance_result_count} == 1 ]]; then
            test_status="pass"
        else
            test_status="fail"
        fi
    fi

    echo "${CPU_NAME};Helloworld_tf_example${example_number};${test_status};${BUILD_URL}artifact/${feature_name}/tf_example${example_number}.log" | tee -a ${summaryLog}
}

main
