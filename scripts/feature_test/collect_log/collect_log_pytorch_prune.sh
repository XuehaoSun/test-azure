#!/bin/bash
set -x

echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"

test_status="check"

pruned_model_score=$(grep -c "Pruned model score is:" ${WORKSPACE}/pytorch_prune/pytorch_prune.log)

if [[ "${pruned_model_score}" == "1" ]]; then
    test_status="pass"
elif [[ "${pruned_model_score}" == "0" ]]; then
    test_status="fail"
fi

echo "PytorchPrune;${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}
