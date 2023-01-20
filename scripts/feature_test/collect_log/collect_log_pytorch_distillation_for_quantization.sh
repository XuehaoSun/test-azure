#!/bin/bash
set -x

echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"

CPU_NAME=$(cat "${WORKSPACE}/${feature_name}/cpu_name.log")
test_status="check"

quantized_result=$(grep -c "eval_metric:" ${WORKSPACE}/${feature_name}/pytorch_distillation_for_quantizaton.log)

if [[ "${quantized_result}" == "1" ]]; then
    test_status="pass"
elif [[ "${quantized_result}" == "0" ]]; then
    test_status="fail"
fi

echo "${CPU_NAME};PytorchDistillQuant;${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}
