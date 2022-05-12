#!/bin/bash
set -x

echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"

CPU_NAME=$(cat "${WORKSPACE}/${feature_name}/cpu_name.log")
test_status="check"

pruned_model_score=$(grep -c "Pruned model score is" ${WORKSPACE}/${feature_name}/pytorch_prune_OFA_stage1.log)

if [[ "${pruned_model_score}" == "1" ]]; then
    test_status="pass"
elif [[ "${pruned_model_score}" == "0" ]]; then
    test_status="fail"
fi

quantized_result=$(grep -c "Evaluated model score" ${WORKSPACE}/${feature_name}/pytorch_prune_OFA_stage2.log)
if [[ "$quantized_result" == "1" ]]; then
    test_status=${test_status}"pass"
elif [[ "$quantized_result" == "0" ]]; then
    test_status=${test_status}"fail"
fi
[[ $(grep "fail" ${test_status}) ]] && final_status="fail" || final_status="pass"
echo "${CPU_NAME};PytorchPruneOFA;${final_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}
