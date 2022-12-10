#!/bin/bash
set -x

echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"

CPU_NAME=$(cat "${WORKSPACE}/${feature_name}/cpu_name.log")
if [[ -z $CPU_NAME ]]; then
    CPU_NAME=$(env | grep CPU_NAME | head -1)
fi
test_status="check"

inversion_result=$(ls | grep dicoo_model)
if [[ "${inversion_result}" != "" ]]; then
    test_status="pass"
else
    test_status="fail"
fi

echo "${CPU_NAME};TextualInversion;${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}




