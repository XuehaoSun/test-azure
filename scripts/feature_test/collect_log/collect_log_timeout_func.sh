#!/bin/bash
set -x

echo "feature_name: ${feature}"
echo "summaryLog: ${summaryLog}"

CPU_NAME=$(cat "${WORKSPACE}/${feature_name}/cpu_name.log")
timeout_log="${WORKSPACE}/${feature_name}/test_timeout_.log"

# fetch valie
test_status="check"
exit_label=$(grep -c 'Specified timeout or max trials is reached' ${timeout_log})

best_acc_lpot=$(grep "Best tune result is:" ${timeout_log} |tail -1 |sed 's/.*Accuracy://;s/,.*//;s/ //g')
check_acc=$(grep -A 3 "accuracy mode benchmark result:" ${timeout_log} |grep "Accuracy is" |awk '{print $NF}')

# get result
if [ ${exit_label} -eq 1 ] && [ "${check_acc}" == "${best_acc_lpot}" ];then
    test_status="pass"
fi


echo "${CPU_NAME};timeout_function_test;${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}
