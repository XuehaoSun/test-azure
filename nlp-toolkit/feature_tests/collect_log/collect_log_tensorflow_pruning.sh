#!/bin/bash
set -x

echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"

CPU_NAME=$(cat "${WORKSPACE}/${feature_name}/cpu_name.log")
if [[ -z $CPU_NAME ]]; then
    CPU_NAME=$(env | grep CPU_NAME | head -1)
fi
final_status="check"
# score
initial_score=$(
    grep "Baseline model's score is" ${WORKSPACE}/${feature_name}/tensorflow_pruning.log |sed 's/.*is *//;s/. *$//' |tail -1
)

distilled_score=$(
    grep "Pruned model score is" ${WORKSPACE}/${feature_name}/tensorflow_pruning.log |sed 's/.*is *//;s/. *$//' |tail -1
)

# conclusion
test_status=$(
    echo |awk -v x=${initial_score} -v y=${distilled_score} 'BEGIN {
        result = "check";
    }{
        if(x > y) {
            result = "fail";
        }else {
            result = "pass";
        }
    }END {
        printf("%s", result);
    }'
)
tuning_status=$(grep -c "Model pruning is done" ${WORKSPACE}/${feature_name}/tensorflow_pruning.log)
accuracy=$(grep -c "metric (sst2) Accuracy:" ${WORKSPACE}/${feature_name}/tensorflow_pruning.log)
throughput=$(grep -c "Throughput: " ${WORKSPACE}/${feature_name}/tensorflow_pruning.log)
if [[ "${tuning_status}" == "1" ]] && [[ "${accuracy}" == "2" ]] && [[ "${throughput}" = "2" ]] && [[ "${test_status}" == "pass" ]]; then
    final_status="pass"
else
    final_status="fail"
fi

echo "${CPU_NAME};TensorflowPruning;${final_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}
