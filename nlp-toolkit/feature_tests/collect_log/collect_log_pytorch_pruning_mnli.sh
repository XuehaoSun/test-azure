#!/bin/bash
set -x

echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"

CPU_NAME=$(cat "${WORKSPACE}/${feature_name}/cpu_name.log")
if [[ -z $CPU_NAME ]]; then
    CPU_NAME=$(env | grep CPU_NAME | head -1)
fi
test_status="check"

pruned_result=$(grep -c "Evaluated model score is" ${WORKSPACE}/${feature_name}/pytorch_pruning_mnli.log)
if [[ "${pruned_result}" == "1" ]]; then
    test_status="pass"
elif [[ "${pruned_result}" == "0" ]]; then
    test_status="fail"
fi

echo "${CPU_NAME};PytorchPruningMNLI;${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}
