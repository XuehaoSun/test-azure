#!/bin/bash
set -x

echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"

CPU_NAME=$(cat "${WORKSPACE}/${feature_name}/cpu_name.log")
test_status="check"
tuning_status=$(grep -c "Model distillation is done" ${WORKSPACE}/${feature_name}/tensorflow_distillation.log)
accuracy=$(grep -c "metric (sst2) Accuracy:" ${WORKSPACE}/${feature_name}/tensorflow_distillation.log | tail -1)
throughput=$(grep -c "Throughput: " ${WORKSPACE}/${feature_name}/tensorflow_distillation.log | tail -1)
if [[ $tuning_status == 1 ]] && [[ "${accuracy}" == "2" ]] && [[ "${throughput}" = "2" ]]; then
    test_status="pass"
else
    test_status="fail"
fi

echo "${CPU_NAME};TensorflowDistillation;${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}
