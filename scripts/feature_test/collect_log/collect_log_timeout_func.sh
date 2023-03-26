#!/bin/bash
set -x

echo "feature_name: ${feature}"
echo "summaryLog: ${summaryLog}"

CPU_NAME=$(cat "${WORKSPACE}/${feature_name}/cpu_name.log")
timeout_log="${WORKSPACE}/${feature_name}/test_timeout_.log"

# fetch valie
test_status="check"
exit_label=$(grep -c '\[ERROR\] Specified timeout or max trials is reached! Not found any quantized model which meet accuracy goal.' ${timeout_log})

# get result
if [ ${exit_label} -eq 1 ];then
    test_status="pass"
fi

echo "${CPU_NAME};timeout_function_test;${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}
