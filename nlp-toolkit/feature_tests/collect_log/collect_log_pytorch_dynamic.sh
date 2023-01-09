#!/bin/bash
set -x

echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"

CPU_NAME=$(cat "${WORKSPACE}/${feature_name}/cpu_name.log")
test_status="check"
control_phrase="model which meet accuracy goal."
lat_phrase="Finally Eval eval_f1 Accuracy:"
quantization_score=$(grep "${control_phrase}" ${WORKSPACE}/${feature_name}/pytorch_dynamic.log | wc -l)
lat_score=$(grep "${lat_phrase}" ${WORKSPACE}/${feature_name}/pytorch_dynamic.log | wc -l)
if [[ "${quantization_score}" == "1" ]] && [[ "${lat_score}" == "1" ]]; then
    test_status="pass"
else
    test_status="fail"
fi


echo "${CPU_NAME};PytorchDynamic;${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}
