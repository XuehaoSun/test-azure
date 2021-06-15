#!/bin/bash
set -x

echo "feature_name: ${feature}"
echo "summaryLog: ${summaryLog}"

test_status="check"
if [ $(grep -c 'Terminated' ${WORKSPACE}/helloworld_timeout.log) == 1 ];then
    test_status="pass"
fi


echo "timeout_function_test;${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}
