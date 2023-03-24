#!/bin/bash
set -x

echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"

CPU_NAME=$(cat "${WORKSPACE}/${feature_name}/cpu_name.log")
if [[ -z $CPU_NAME ]]; then
    CPU_NAME=$(env | grep CPU_NAME | head -1)
fi
test_status="check"

distilled_result=$(grep "distilled model score is" ${WORKSPACE}/${feature_name}/pytorch_distillation_sd.log)
if [[ "${distilled_result}" != "" ]]; then
    test_status="pass"
else
    test_status="fail"
fi

echo "${CPU_NAME};PytorchDistillationStableDiffusion;${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}




