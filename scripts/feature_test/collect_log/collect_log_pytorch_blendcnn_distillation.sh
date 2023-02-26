#!/bin/bash
set -xe

# basic
echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"
CPU_NAME=$(cat "${WORKSPACE}/${feature_name}/cpu_name.log")

# log
blendcnn_distilling_log="${WORKSPACE}/${feature_name}/blendcnn-distilling-test.log"

# score
distillation_result=$(
    grep -c 'Training has been done properly.' ${WORKSPACE}/${feature_name}/blendcnn-distilling-test.log
)

if [[ "${distillation_result}" == "1" ]]; then
    test_status="pass"
elif [[ "${distillation_result}" == "0" ]]; then
    test_status="fail"
fi
# summary
echo "${CPU_NAME};${feature_name};${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}
