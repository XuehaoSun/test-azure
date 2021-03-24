#!/bin/bash
set -x

echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"

function main {
    for i in `seq 5`
    do
        check_tf_example_status ${i}
    done
}

function check_tf_example_status {
    example_number=${1}
    test_status="check"
    test_phrase="Tune [0-9]* result is: \[[0-9]*\.[0-9]*\, [0-9]*\.[0-9]*\] Best tune result is: \[[0-9]*\.[0-9]*\, [0-9]*\.[0-9]*\]"
    if [[ ${example_number} == 2 ]]; then
        test_phrase="Inference is done."
    fi

    if [ $(grep -c "${test_phrase}" ${WORKSPACE}/${feature_name}/tf_example${example_number}.log) == 1 ]; then
        status="pass"
    else
        status="fail"
    fi

    if [[ ${example_number} == 5 ]]; then
        accuracy_count=$(grep -c 'Accuracy is [0-9]*\.[0-9]*' ${WORKSPACE}/${feature_name}/tf_example${example_number}.log)
        latency_count=$(grep -c 'Latency: [0-9]*\.[0-9]*' ${WORKSPACE}/${feature_name}/tf_example${example_number}.log)
        if [[ ${status} == "pass" ]] && [[ ${accuracy_count} == 2 ]] && [[ ${latency_count} == 2 ]]; then
            test_status="pass"
        else
            test_status="fail"
        fi
    else
        test_status=${status}
    fi

    echo "Helloworld_tf_example${example_number};${test_status};${BUILD_URL}artifact/${feature_name}/tf_example${example_number}.log" | tee -a ${summaryLog}
}

main
