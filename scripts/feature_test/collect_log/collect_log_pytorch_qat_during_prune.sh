#!/bin/bash
set -x

echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"

CPU_NAME=$(cat "${WORKSPACE}/${feature_name}/cpu_name.log")
test_status="check"

pruned_model_score=$(grep -c "model score is:" ${WORKSPACE}/${feature_name}/pytorch_qat_during_prune.log)

if [[ "${pruned_model_score}" == "1" ]]; then
    test_status="pass"
elif [[ "${pruned_model_score}" == "0" ]]; then
    test_status="fail"
fi

echo "${CPU_NAME};PytorchQatDuringPrune;${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}
