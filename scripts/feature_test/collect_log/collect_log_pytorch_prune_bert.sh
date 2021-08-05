#!/bin/bash
set -x

echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"

CPU_NAME=$(cat "${WORKSPACE}/${feature_name}/cpu_name.log")
test_status="check"

pruned_model_score=$(grep -c "Pruned model score is:" ${WORKSPACE}/${feature_name}/pytorch_prune_bert.log)

if [[ "${pruned_model_score}" == "1" ]]; then
    test_status="pass"
elif [[ "${pruned_model_score}" == "0" ]]; then
    test_status="fail"
fi

echo "${CPU_NAME};PytorchPruneBert;${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}
