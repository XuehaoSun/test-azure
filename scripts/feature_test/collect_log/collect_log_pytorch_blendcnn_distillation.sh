#!/bin/bash
set -xe

# basic
echo "feature_name: ${feature_name}"
echo "summaryLog: ${summaryLog}"
CPU_NAME=$(cat "${WORKSPACE}/${feature_name}/cpu_name.log")

# log
blendcnn_distilling_log="${WORKSPACE}/${feature_name}/blendcnn-distilling-test.log"

# score
initial_score=$(
    grep 'initial model score is' ${blendcnn_distilling_log} |sed 's/.*is *//;s/. *$//' |tail -1
)

distilled_score=$(
    grep 'distilled model score is' ${blendcnn_distilling_log} |sed 's/.*is *//;s/. *$//' |tail -1
)

# conclusion
test_status=$(
    echo |awk -v x=${initial_score} -v y=${distilled_score} 'BEGIN {
        result = "check";
    }{
        if(x < y) {
            result = "pass";
        }else {
            result = "fail";
        }
    }END {
        printf("%s", result);
    }'
)

# summary
echo "${CPU_NAME};${feature_name};${test_status};${BUILD_URL}artifact/${feature_name}" | tee -a ${summaryLog}
