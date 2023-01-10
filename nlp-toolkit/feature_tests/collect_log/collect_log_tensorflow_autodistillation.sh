#!/bin/bash
set -x

echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"

CPU_NAME=$(cat "${WORKSPACE}/${feature_name}/cpu_name.log")
if [[ -z $CPU_NAME ]]; then
    CPU_NAME=$(env | grep CPU_NAME | head -1)
fi
test_status="check"


autodistilled_result=$(grep -c "distilled model score is" ${WORKSPACE}/${feature_name}/tensorflow_autodistillation.log)
accuracy=$(grep -c "metric (sst2) Accuracy:" ${WORKSPACE}/${feature_name}/tensorflow_autodistillation.log | tail -1)
throughput=$(grep -c "Throughput: " ${WORKSPACE}/${feature_name}/tensorflow_autodistillation.log | tail -1)

if [[ $autodistilled_result == 3 ]] && [[ "${accuracy}" == "2" ]] && [[ "${throughput}" = "2" ]]; then
    test_status="pass"
else
    test_status="fail"
fi
echo "${CPU_NAME};TensorflowAutoDistillation;${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}
