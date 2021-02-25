#!/bin/bash
set -x

echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"

test_status="check"

top1_acc_threshold="69.0"
top1_acc=$(grep "* Acc@1" ${WORKSPACE}/pytorch_prune/pytorch_prune.log | cut -d ' ' -f 4)

top1_threshold_met=$(awk -v top1=${top1_acc} -v threshold=${top1_acc_threshold} 'BEGIN { print (top1 >= threshold)}')
if [[ "${top1_threshold_met}" == "1" ]]; then
    test_status="pass"
elif [[ "${top1_threshold_met}" == "0" ]]; then
    test_status="fail"
fi

echo "PytorchPrune;${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}
