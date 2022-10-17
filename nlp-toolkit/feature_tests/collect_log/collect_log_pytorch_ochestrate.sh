#!/bin/bash
set -x

echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"

CPU_NAME=$(cat "${WORKSPACE}/${feature_name}/cpu_name.log")
if [[ -z $CPU_NAME ]]; then
    CPU_NAME=$(env | grep CPU_NAME | head -1)
fi
test_status="check"

distilled_result=$(grep "Throughput:" ${WORKSPACE}/${feature_name}/pytorch_ochestrate_optimizations.log)
metric_result=$(grep "initial model score is" ${WORKSPACE}/${feature_name}/pytorch_ochestrate_optimizations.log)
if [[ "${distilled_result}" != "" ]] && [[ "${metric_result}" != "" ]]; then
    test_status="pass"
else
    test_status="fail"
fi

echo "${CPU_NAME};PytorchOchestrateOpt;${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}




