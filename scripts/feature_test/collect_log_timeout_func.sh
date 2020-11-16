#!/bin/bash
set -x

echo "feature_name: ${feature}"
echo "summaryLog: ${summaryLog}"

test_status="check"
if [ $(grep -c 'Inference is done.' ${WORKSPACE}/helloworld_keras.log) == 0 ];then
    test_status="fail"
elif [ $(grep -c 'Inference is done.' ${WORKSPACE}/helloworld_pb.log) == 0 ];then
    test_status="fail"
elif [ $(grep -c 'Inference is done.' ${WORKSPACE}/helloworld_pb.log) != 2 ]; then
    test_status="fail"
else
    test_status="pass"
fi


echo "Helloworld&timeout_function_test;${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}