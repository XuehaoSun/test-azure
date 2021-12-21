#!/bin/bash
set -x

function main {
    echo "summaryLog: ${summaryLog}"
    echo "summaryLogLast: ${summaryLogLast}"
    echo "inferencerSummaryLog: ${inferencerSummaryLog}"
    echo "inferencerSummaryLogLast: ${inferencerSummaryLogLast}"
    echo "overviewLog: ${overviewLog}"
    echo "coverage_summary: ${coverage_summary}"
    echo "coverage_summary: ${coverage_summary}"
    echo "coverage_summary_base: ${coverage_summary_base}"

    generate_html_head
    generate_html_overview
    generate_benchmark_results
    generate_tuning_results
    generate_html_footer
}

function createOverview {

    jenkins_job_url="https://inteltf-jenk.sh.intel.com/job/"

    echo """
      <h2>Overview</h2>
      <table class=\"features-table\" style=\"width: 60%;margin: 0 auto 0 0;empty-cells: hide\">
        <tr>
            <th>Task</th>
            <th>Job</th>
            <th>Status</th>
        </tr>
    """ >> ${WORKSPACE}/report.html

    gtest=($(grep 'deep-engine_ut_gtest' ${overviewLog} |sed 's/,/ /g'))
    if [[ "${gtest[1]}" == *"FAIL"* ]]; then
        gtest_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${gtest[1]}" == *"SUCC"* ]]; then
        gtest_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        gtest_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi

    pytest=($(grep 'deep-engine_ut_pytest' ${overviewLog} |sed 's/,/ /g'))
    if [[ "${pytest[1]}" == *"FAIL"* ]]; then
        pytest_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${pytest[1]}" == *"SUCC"* ]]; then
        pytest_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        pytest_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi

    cpplint_scan=($(grep 'deep-engine-code-scan,cpplint' ${overviewLog} |sed 's/,/ /g'))
    if [[ "${cpplint_scan[2]}" == *"FAIL"* ]];then
        cpplint_scan_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${cpplint_scan[2]}" == *"SUCC"* ]];then
        cpplint_scan_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        cpplint_scan_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi

    pylint_scan=($(grep 'deep-engine-code-scan,pylint' ${overviewLog} |sed 's/,/ /g'))
    if [[ "${pylint_scan[2]}" == *"FAIL"* ]];then
        pylint_scan_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${pylint_scan[2]}" == *"SUCC"* ]];then
        pylint_scan_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        pylint_scan_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi

    bandit_scan=($(grep 'deep-engine-code-scan,bandit' ${overviewLog} |sed 's/,/ /g'))
    if [[ "${bandit_scan[2]}" == *"FAIL"* ]];then
        bandit_scan_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${bandit_scan[2]}" == *"SUCC"* ]];then
        bandit_scan_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        bandit_scan_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi

    spellcheck_scan=($(grep 'deep-engine-code-scan,pyspelling' ${overviewLog} |sed 's/,/ /g'))
    if [[ "${spellcheck_scan[2]}" == *"FAIL"* ]];then
        spellcheck_scan_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${spellcheck_scan[2]}" == *"SUCC"* ]];then
        spellcheck_scan_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        spellcheck_scan_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi

    copyright_check=($(grep 'copyright-check' ${overviewLog} |sed 's/,/ /g'))
    if [[ "${copyright_check[1]}" == *"FAIL"* ]];then
        copyright_check_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${copyright_check[1]}" == *"SUCC"* ]];then
        copyright_check_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        copyright_check_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi

    cat >> ${WORKSPACE}/report.html <<  eof

        $(
             if [ "${gtest[2]}" != "" ]; then
                 echo "<tr><td>gtest</td>"
                 echo "<td style=\"text-align:left\"><a href=\"${gtest[2]}\">ut_link</a></td>"
                 echo "${gtest_status}</tr>"
             fi

             if [ "${pytest[2]}" != "" ]; then
                 echo "<tr><td>pytest</td>"
                 echo "<td style=\"text-align:left\"><a href=\"${pytest[2]}\">ut_link</a></td>"
                 echo "${pytest_status}</tr>"
             fi

             if [ "${cpplint_scan[3]}" != "" ]; then
                 echo "<tr><td>cpplint Scan</td>"
                 echo "<td style=\"text-align:left\"><a href=\"${jenkins_job_url}${cpplint_scan[0]}/${cpplint_scan[3]}\">${cpplint_scan[0]}#${cpplint_scan[3]}</a></td>"
                 echo "${cpplint_scan_status}</tr>"
             fi

             if [ "${pylint_scan[3]}" != "" ]; then
                 echo "<tr><td>PyLint Scan</td>"
                 echo "<td style=\"text-align:left\"><a href=\"${jenkins_job_url}${pylint_scan[0]}/${pylint_scan[3]}\">${pylint_scan[0]}#${pylint_scan[3]}</a></td>"
                 echo "${pylint_scan_status}</tr>"
             fi

             if [ "${bandit_scan[3]}" != "" ]; then
                 echo "<tr><td>Bandit Scan</td>"
                 echo "<td style=\"text-align:left\"><a href=\"${jenkins_job_url}${bandit_scan[0]}/${bandit_scan[3]}\">${bandit_scan[0]}#${bandit_scan[3]}</a></td>"
                 echo "${bandit_scan_status}</tr>"
             fi

             if [ "${spellcheck_scan[3]}" != "" ]; then
                 echo "<tr><td>Spellcheck Scan</td>"
                 echo "<td style=\"text-align:left\"><a href=\"${jenkins_job_url}${spellcheck_scan[0]}/${spellcheck_scan[3]}\">${spellcheck_scan[0]}#${spellcheck_scan[3]}</a></td>"
                 echo "${spellcheck_scan_status}</tr>"
             fi

            if [ "${copyright_check[2]}" != "" ]; then
                 echo "<tr><td>Copyright Check</td>"
                 echo "<td style=\"text-align:left\"><a href=\"${jenkins_job_url}${copyright_check[0]}/${copyright_check[2]}/artifact/copyright_issue_summary.log\">${copyright_check[0]}#${copyright_check[2]}</a></td>"
                 echo "${copyright_check_status}</tr>"
            fi
        )
    </table>
eof
}

function generate_html_overview {
Test_Info_Title=''
Test_Info=''

Test_Info_Title="<th colspan="4">Test Branch</th> <th colspan="4">Commit ID</th> "
Test_Info="<th colspan="4">${lpot_branch}</th> <th colspan="4">${lpot_commit}</th> "

cat >> ${WORKSPACE}/report.html << eof

<body>
    <div id="main">
        <h1 align="center">DeepEngine Tests
        [ <a href="${RUN_DISPLAY_URL}">Job-${BUILD_NUMBER}</a> ]</h1>
      <h1 align="center">Test Status: ${Jenkins_job_status}</h1>
        <h2>Summary</h2>
        <table class="features-table">
            <tr>
              <th>Repo</th>
              ${Test_Info_Title}
              </tr>
              <tr>
                    <td><a href="https://github.com/intel-innersource/frameworks.ai.deep-engine.intel-deep-engine.git">DeepEngine</a></td>
              ${Test_Info}
                </tr>
        </table>
eof

createOverview
createCoverageOverview

}

function createCoverageOverview {
    echo "Generating coverage overview."
    if [ ! -f "${coverage_summary}" ] || [ $(wc -l ${coverage_summary} | awk '{ print $1 }') -le 1 ]; then
        return 0
    fi
    lines_coverage=($(grep 'lines_coverage' ${coverage_summary} |sed 's/,/ /g'))
    branches_coverage=($(grep 'branches_coverage' ${coverage_summary} |sed 's/,/ /g'))

    echo """
        <h2>Code Coverage</h2>
        <table class=\"features-table\" style=\"width: 60%;margin: 0 auto 0 0;empty-cells: hide\">
        <tr>
            <th></th>
            <th>Covered</th>
            <th>Total</th>
            <th>Coverage</th>
        </tr>
        <tr>
            <td>Lines</td>
            <td>${lines_coverage[1]}</td>
            <td>${lines_coverage[2]}</td>
    """ >> ${WORKSPACE}/report.html

    awk -v lines_coverage="${lines_coverage[3]}" -v lines_coverage_threshold="${lines_coverage_threshold}" -F ';' '
    BEGIN {
        if(lines_coverage < lines_coverage_threshold) {
            printf("<td style=\"background-color:#FFD2D2\">%.2f%</td>", lines_coverage);
        } else {
            printf("<td style=\"background-color:#90EE90\">%.2f%</td>", lines_coverage);
        }
    }' >> ${WORKSPACE}/report.html

    echo """
        </tr>
        <tr>
            <td>Branches</td>
            <td>${branches_coverage[1]}</td>
            <td>${branches_coverage[2]}</td>
    """ >> ${WORKSPACE}/report.html

    awk -v branches_coverage="${branches_coverage[3]}" -v branches_coverage_threshold="${branches_coverage_threshold}" -F ';' '
    BEGIN {
        if(branches_coverage < branches_coverage_threshold) {
            printf("<td style=\"background-color:#FFD2D2\">%.2f%</td>", branches_coverage);
        } else {
            printf("<td style=\"background-color:#90EE90\">%.2f%</td>", branches_coverage);
        }
    }' >> ${WORKSPACE}/report.html

    echo """
        </tr>
        </table>
    """ >> ${WORKSPACE}/report.html
}

function generate_benchmark_results {

cat >> ${WORKSPACE}/report.html << eof
    <h2>Benchmark</h2>
      <table class="features-table">
        <tr>
          <th rowspan="2">Model</th>
          <th rowspan="2">Seq_len</th>
          <th rowspan="2">VS</th>
          <th rowspan="2">Full<br>Cores</th>
          <th rowspan="2">NCores<br>per Instance</th>
          <th rowspan="2">BS</th>
          <th>INT8</th>
          <th>FP32</th>
          <th colspan="2" class="col-cell col-cell1 col-cellh">Ratio</th>
        </tr>
        <tr>
          <th>throughput</th>
          <th>throughput</th>
          <th colspan="2" class="col-cell col-cell1"><font size="2px">FP32/INT8</font></th>
        </tr>
eof

    mode='throughput'
    models=$(cat ${inferencerSummaryLog} |grep "^${mode}," |cut -d',' -f2 |awk '!a[$0]++')
    for model in ${models[@]}
    do
        seq_lens=$(cat ${inferencerSummaryLog} |grep "^${mode},${model}," |cut -d',' -f3 |awk '!a[$0]++')
        for seq_len in ${seq_lens[@]}
        do
            full_cores=$(cat ${inferencerSummaryLog} |grep "^${mode},${model},${seq_len}," |cut -d',' -f4 |awk '!a[$0]++')
            for full_core in ${full_cores[@]}
            do
                core_per_inss=$(cat ${inferencerSummaryLog} |grep "^${mode},${model},${seq_len},${full_core}," |cut -d',' -f5 |awk '!a[$0]++')
                for core_per_ins in ${core_per_inss[@]}
                do
                    bss=$(cat ${inferencerSummaryLog} |grep "^${mode},${model},${seq_len},${full_core},${core_per_ins}," |cut -d',' -f6 |awk '!a[$0]++')
                    for bs in ${bss[@]}
                    do
                        benchmark_pattern="^${mode},${model},${seq_len},${full_core},${core_per_ins},${bs}"
                        benchmark_int8=$(cat ${inferencerSummaryLog} |grep "${benchmark_pattern},int8" |cut -d',' -f8)
                        benchmark_int8_url=$(cat ${inferencerSummaryLog} |grep "${benchmark_pattern},int8" |cut -d',' -f9)
                        benchmark_fp32=$(cat ${inferencerSummaryLog} |grep "${benchmark_pattern},fp32" |cut -d',' -f8)
                        benchmark_fp32_url=$(cat ${inferencerSummaryLog} |grep "${benchmark_pattern},fp32" |cut -d',' -f9)
                        if [ $(cat ${inferencerSummaryLogLast} |grep -c "${benchmark_pattern},int8") == 0 ]; then
                            benchmark_int8_last=nan
                            benchmark_int8_url_last=nan
                            benchmark_fp32_last=nan
                            benchmark_fp32_url_last=nan
                        else
                            benchmark_int8_last=$(cat ${inferencerSummaryLogLast} |grep "${benchmark_pattern},int8" |cut -d',' -f8)
                            benchmark_int8_url_last=$(cat ${inferencerSummaryLogLast} |grep "${benchmark_pattern},int8" |cut -d',' -f9)
                            benchmark_fp32_last=$(cat ${inferencerSummaryLogLast} |grep "${benchmark_pattern},fp32" |cut -d',' -f8)
                            benchmark_fp32_url_last=$(cat ${inferencerSummaryLogLast} |grep "${benchmark_pattern},fp32" |cut -d',' -f9)
                        fi
                        generate_perf_core
                    done
                done
            done
        done
    done
    cat >> ${WORKSPACE}/report.html << eof
        <tr>
            <td colspan="8"><font color="#d6776f">Note: </font>All data tested on TensorFlow Dedicated Server.</td>
            <td colspan="2" class="col-cell col-cell1 col-cellf"></td>
        </tr>
    </table>
eof
}

function generate_perf_core {
    echo "<tr><td rowspan=3>${model}</td><td rowspan=3>${seq_len}</td><td>New</td><td rowspan=2>${full_core}</td><td rowspan=2>${core_per_ins}</td><td rowspan=2>${bs}</td>" >> ${WORKSPACE}/report.html

    echo | awk -v b_int8=${benchmark_int8} -v b_int8_url=${benchmark_int8_url} -v b_fp32=${benchmark_fp32} -v b_fp32_url=${benchmark_fp32_url} -v b_int8_l=${benchmark_int8_last} -v b_int8_url_l=${benchmark_int8_url_last} -v b_fp32_l=${benchmark_fp32_last} -v b_fp32_url_l=${benchmark_fp32_url_last} '
        function show_benchmark(a,b) {
            if(a ~/[1-9]/) {
                    printf("<td><a href=%s>%.2f</a></td>\n",b,a);
            }else {
                if(a == "") {
                    printf("<td><a href=%s>Failure</a></td>\n",b,a);
                }else{
                    printf("<td></td>\n");
                }
            }
        }

        function compare_current(a,b) {

            if(a ~/[1-9]/ && b ~/[1-9]/) {
                target = a / b;
                if(target >= 2) {
                   printf("<td rowspan=3 style=\"background-color:#90EE90\">%.2f</td>", target);
                }else if(target < 1) {
                   printf("<td rowspan=3 style=\"background-color:#FFD2D2\">%.2f</td>", target);
                }else{
                   printf("<td rowspan=3>%.2f</td>", target);
                }
            }else{
                printf("<td rowspan=3></td>");
            }

        }

        function compare_new_last(a,b){
            if(a ~/[1-9]/ && b ~/[1-9]/) {
                target = a / b;
                if(target >= 0.945) {
                    status_png = "background-color:#90EE90";
                }else {
                    status_png = "background-color:#FFD2D2";
                    job_status = "fail"
                }
                printf("<td style=\"%s\">%.2f</td>", status_png, target);
            }else{
                if(a == ""){
                    job_status = "fail"
                    status_png = "background-color:#FFD2D2";
                    printf("<td style=\"%s\"></td>", status_png);
                }else{
                    printf("<td class=\"col-cell col-cell3\"></td>");
                }
            }
        }


        BEGIN {
            job_status = "pass"
        }{
            // current
            show_benchmark(b_int8,b_int8_url)
            show_benchmark(b_fp32,b_fp32_url)

            // current comparison
            compare_current(b_int8,b_fp32)

            // Last
            printf("</tr>\n<tr><td>Last</td>")
            show_benchmark(b_int8_l,b_int8_url_l)
            show_benchmark(b_fp32_l,b_fp32_url_l)

            // current vs last
            printf("</tr>\n<tr><td>New/Last</td><td colspan=3 class=\"col-cell3\"></td>");
            compare_new_last(b_int8,b_int8_l)
            compare_new_last(b_fp32,b_fp32_l)
            printf("</tr>\n");
        } END{
          printf("\n%s", job_status);
        }
    ' >> ${WORKSPACE}/report.html
    job_state=$(tail -1 ${WORKSPACE}/report.html)
    sed -i '$s/.*//' ${WORKSPACE}/report.html
    if [ ${job_state} == 'fail' ]; then
      echo "performance regression" >> ${WORKSPACE}/perf_regression.log
    fi
}

function generate_tuning_results {

cat >> ${WORKSPACE}/report.html << eof
    <h2>Tuning</h2>
      <table class="features-table">
            <tr>
                <th rowspan="2">Platform</th>
                <th rowspan="2">System</th>
                <th rowspan="2">Framework</th>
                <th rowspan="2">Version</th>
                <th rowspan="2">Model</th>
                <th rowspan="2">VS</th>
                <th rowspan="2">Tuning<br>Strategy</th>
                <th rowspan="2">Tuning<br>Time(s)</th>
                <th rowspan="2">Tuning<br>Count</th>
                <th rowspan="2">Models Size<br>FP32/INT8</th>
			          <th colspan="6">INT8(BF16)</th>
			          <th colspan="6">FP32</th>
			          <th colspan="3" class="col-cell col-cell1 col-cellh">Ratio</th>
		        </tr>
		        <tr>
                <th>bs</th>
                <th>ms</th>
                <th>bs</th>
                <th>imgs/s</th>
                <th>bs</th>
                <th>top1</th>
                <th>bs</th>
                <th>ms</th>
                <th>bs</th>
                <th>imgs/s</th>
                <th>bs</th>
                <th>top1</th>
                <th class="col-cell col-cell1">Latency<br><font size="2px">FP32/INT8>=1.5</font></th>
                <th class="col-cell col-cell1">Throughput<br><font size="2px">INT8/FP32>=2</font></th>
                <th class="col-cell col-cell1">Accuracy<br><font size="2px">(INT8-FP32)/FP32>=-0.01</font></th>
		        </tr>
eof

    oses=$(sed '1d' ${summaryLog} |cut -d';' -f1 | awk '!a[$0]++')

    for os in ${oses[@]}
    do
        platforms=$(sed '1d' ${summaryLog} |grep "^${os}" |cut -d';' -f2 | awk '!a[$0]++')
        for platform in ${platforms[@]}
        do
            frameworks=$(sed '1d' ${summaryLog} |grep "^${os};${platform}" |cut -d';' -f3 | awk '!a[$0]++')
            for framework in ${frameworks[@]}
            do
                fw_versions=$(sed '1d' ${summaryLog} |grep "^${os};${platform};${framework}" |cut -d';' -f4 | awk '!a[$0]++')
                for fw_version in ${fw_versions[@]}
                do
                    models=$(sed '1d' ${summaryLog} |grep "^${os};${platform};${framework};${fw_version}" |cut -d';' -f6 | awk '!a[$0]++')
                    for model in ${models[@]}
                    do
                        current_values=$(generate_inference ${summaryLog})
                        last_values=$(generate_inference ${summaryLogLast})

                        # model size
                        pb_size=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLog} |awk -F ';' '{printf("%s;%s;%s", $10,$11,$12)}')
                        last_pb_size=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLogLast} |awk -F ';' '{printf("%s;%s;%s", $10,$11,$12)}')

                        generate_tuning_core
                    done
                done
            done
        done
    done

    cat >> ${WORKSPACE}/report.html << eof
		    <tr>
			    <td colspan="22"><font color="#d6776f">Note: </font>All data tested on TensorFlow Dedicated Server.</td>
			    <td colspan="3" class="col-cell col-cell1 col-cellf"></td>
		    </tr>
    </table>
eof
}

function generate_inference {
    awk -v framework="${framework}" -v fw_version="${fw_version}" -v model="${model}" -v os="${os}" -v platform=${platform} -F ';' '
        BEGINE {
            fp32_ms_bs = "nan";
            fp32_ms_value = "nan";
            fp32_ms_url = "nan";
            fp32_fps_bs = "nan";
            fp32_fps_value = "nan";
            fp32_fps_url = "nan";
            fp32_acc_bs = "nan";
            fp32_acc_value = "nan";
            fp32_acc_url = "nan";

            int8_ms_bs = "nan";
            int8_ms_value = "nan";
            int8_ms_url = "nan";
            int8_fps_bs = "nan";
            int8_fps_value = "nan";
            int8_fps_url = "nan";
            int8_acc_bs = "nan";
            int8_acc_value = "nan";
            int8_acc_url = "nan";
        }{
            if($1 == os && $2 == platform && $3 == framework && $4 == fw_version && $6 == model) {
                // FP32
                if($5 == "FP32") {
                    // Latency
                    if($8 == "Latency") {
                        fp32_ms_bs = $9;
                        fp32_ms_value = $10;
                        fp32_ms_url = $11;
                    }
                    // Throughput
                    if($8 == "Throughput") {
                        fp32_fps_bs = $9;
                        fp32_fps_value = $10;
                        fp32_fps_url = $11;
                    }
                    // Accuracy
                    if($8 == "Accuracy") {
                        fp32_acc_bs = $9;
                        fp32_acc_value = $10;
                        fp32_acc_url = $11;
                    }
                }

                // INT8
                if($5 == "INT8") {
                    // Latency
                    if($8 == "Latency") {
                        int8_ms_bs = $9;
                        int8_ms_value = $10;
                        int8_ms_url = $11;
                    }
                    // Throughput
                    if($8 == "Throughput") {
                        int8_fps_bs = $9;
                        int8_fps_value = $10;
                        int8_fps_url = $11;
                    }
                    // Accuracy
                    if($8 == "Accuracy") {
                        int8_acc_bs = $9;
                        int8_acc_value = $10;
                        int8_acc_url = $11;
                    }
                }
            }
        }END {
            printf("%s;%s;%s;%s;%s;%s;", int8_ms_bs,int8_ms_value,int8_fps_bs,int8_fps_value,int8_acc_bs,int8_acc_value);
            printf("%s;%s;%s;%s;%s;%s;", fp32_ms_bs,fp32_ms_value,fp32_fps_bs,fp32_fps_value,fp32_acc_bs,fp32_acc_value);
            printf("%s;%s;%s;%s;%s;%s;", int8_ms_url,int8_fps_url,int8_acc_url,fp32_ms_url,fp32_fps_url,fp32_acc_url);
        }
    ' $1
}

function generate_tuning_core {

    tuning_strategy=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLog} |awk -F';' '{print $6}')
    tuning_time=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLog} |awk -F';' '{print $7}')
    tuning_count=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLog} |awk -F';' '{print $8}')
    tuning_log=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLog} |awk -F';' '{print $9}')
    echo "<tr><td rowspan=3>${platform}</td><td rowspan=3>${os}</td><td rowspan=3>${framework}</td><td rowspan=3>${fw_version}</td><td rowspan=3>${model}</td><td>New</td><td><a href=${tuning_log}>${tuning_strategy}</a></td>" >> ${WORKSPACE}/report.html
    echo "<td><a href=${tuning_log}>${tuning_time}</a></td><td><a href=${tuning_log}>${tuning_count}</a></td>" >> ${WORKSPACE}/report.html

    tuning_strategy=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLogLast} |awk -F';' '{print $6}')
    tuning_time=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLogLast} |awk -F';' '{print $7}')
    tuning_count=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLogLast} |awk -F';' '{print $8}')
    tuning_log=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLogLast} |awk -F';' '{print $9}')

    echo |awk -F ';' -v current_values="${current_values}" -v last_values="${last_values}" \
              -v pb_size="${pb_size}" -v last_pb_size="${last_pb_size}" \
              -v ts="${tuning_strategy}" -v tt="${tuning_time}" -v tc="${tuning_count}" -v tl="${tuning_log}" '

        function abs(x) { return x < 0 ? -x : x }

        function show_new_last(batch,link,value,metric) {
            if(value ~/[1-9]/) {
                if (metric == "ms") {
                    printf("<td>%s</td> <td><a href=%s>%.2f</a></td>\n",batch,link,value);
                }else if(metric == "fps") {
                    printf("<td>%s</td> <td><a href=%s>%.2f</a></td>\n",batch,link,value);
                }else {
                    if (value <= 1){
                        printf("<td>%s</td> <td><a href=%s>%.2f%</a></td>\n",batch,link,value*100);
                    }else{
                        printf("<td>%s</td> <td><a href=%s>%.2f</a></td>\n",batch,link,value);
                    }
                }
            }else {
                if(link == "" || value == "N/A") {
                    printf("<td></td> <td></td>\n");
                }else
                {
                    printf("<td>%s</td> <td><a href=%s>Failure</a></td>\n",batch,link);
                }
            }
        }

        function compare_current(a,b,c) {

            if(a ~/[1-9]/ && b ~/[1-9]/) {
                if(c == "acc") {
                    target = (a - b) / b;
                    if(target >= -0.01) {
                       printf("<td rowspan=3 style=\"background-color:#90EE90\">%.2f%</td>", target*100);
                    }else if(target < -0.05) {
                       printf("<td rowspan=3 style=\"background-color:#FFD2D2\">%.2f%</td>", target*100);
                    }else{
                       printf("<td rowspan=3>%.2f%</td>", target*100);
                    }
                }else if(c == "ms") {
                    target = a / b;
                    if(target >= 1.5) {
                       printf("<td rowspan=3 style=\"background-color:#90EE90\">%.2f</td>", target);
                    }else if(target < 1) {
                       printf("<td  rowspan=3 style=\"background-color:#FFD2D2\">%.2f</td>", target);
                    }else{
                       printf("<td rowspan=3>%.2f</td>", target);
                    }
                }else if(c == "fps") {
                    target = a / b;
                    if(target >= 2) {
                       printf("<td rowspan=3 style=\"background-color:#90EE90\">%.2f</td>", target);
                    }else if(target < 1) {
                       printf("<td rowspan=3 style=\"background-color:#FFD2D2\">%.2f</td>", target);
                    }else{
                       printf("<td rowspan=3>%.2f</td>", target);
                    }
                }else {
                    // Compare model size
                    target = a / b;
                    if(target > 1) {
                       printf("<td rowspan=3 style=\"background-color:#FFD2D2\">%s/%s/%s</td>", a,b,c);
                    }else{
                       printf("<td rowspan=3>%s/%s/%s</td>", a,b,c);
                    }
                }
            }else {
                printf("<td rowspan=3></td>");
            }
        }

        function compare_result(a,b,c) {

            if(a ~/[1-9]/ && b ~/[1-9]/) {
                if(c == "acc") {
                    target = a - b;
                    if(target >= -0.0001 && target <= 0.0001) {
                        status_png = "background-color:#90EE90";
                    }else {
                        status_png = "background-color:#FFD2D2";
                    }
                    if (a <= 1){
                        printf("<td style=\"%s\" colspan=2>%.2f%</td>", status_png, target*100);
                    }else{
                        printf("<td style=\"%s\" colspan=2>%.2f</td>", status_png, target);
                    }

                }else {
                    target = a / b;
                    if(target >= 0.95) {
                        status_png = "background-color:#90EE90";
                    }else {
                        status_png = "background-color:#FFD2D2";
                    }
                    printf("<td style=\"%s\" colspan=2>%.2f</td>", status_png, target);
                }
            }else {
                if(a == "nan" || b == "nan") {
                    printf("<td class=\"col-cell col-cell3\" colspan=2></td>");
                }else {
                    printf("<td style=\"col-cell col-cell3\" colspan=2></td>");
                    job_red++;
                }
            }
        }

        BEGIN {

            // issue list
            jira_mobilenet = "https://jira01.devtools.intel.com/browse/PADDLEQ-384";
            jira_resnext = "https://jira01.devtools.intel.com/browse/PADDLEQ-387";
            jira_ssdmobilenet = "https://jira01.devtools.intel.com/browse/PADDLEQ-406";
        }{
            // Current values
            split(current_values,current_value,";");

            // model size
            split(pb_size, pb_size_, ";")
            split(last_pb_size, last_pb_size_, ";")

            // current
            if(pb_size_[1] ~/[1-9]/ && pb_size_[2] ~/[1-9]/) {
                if(pb_size_[1] < pb_size_[2]) {
                    printf("<td style=\"background-color:#FFD2D2\">%.2fx</td>", pb_size_[1]/pb_size_[2]);
                }else {
                    printf("<td>%.2fx</td>", pb_size_[1]/pb_size_[2]);
                }
            } else {
                printf("<td>NaN</td>");
            }
            show_new_last(current_value[1],current_value[13],current_value[2],"ms");
            show_new_last(current_value[3],current_value[14],current_value[4],"fps");
            show_new_last(current_value[5],current_value[15],current_value[6],"acc");
            show_new_last(current_value[7],current_value[16],current_value[8],"ms");
            show_new_last(current_value[9],current_value[17],current_value[10],"fps");
            show_new_last(current_value[11],current_value[18],current_value[12],"acc");

            // Compare Current
            compare_current(current_value[8],current_value[2],"ms");
            compare_current(current_value[4],current_value[10],"fps");
            compare_current(current_value[6],current_value[12],"acc");

            // Last values
            split(last_values,last_value,";");

            // Last
            printf("</tr>\n<tr><td>Last</td><td><a href=%4$s>%1$s</a></td><td><a href=%4$s>%2$s</a></td><td><a href=%4$s>%3$s</a></td>", ts, tt, tc, tl);
            if(last_pb_size_[1] ~/[1-9]/ && last_pb_size_[2] ~/[1-9]/) {
                printf("<td>%.2fx</td>", last_pb_size_[1]/last_pb_size_[2]);
            }else {
                printf("<td>NaN</td>");
            }
            show_new_last(last_value[1],last_value[13],last_value[2],"ms");
            show_new_last(last_value[3],last_value[14],last_value[4],"fps");
            show_new_last(last_value[5],last_value[15],last_value[6],"acc");
            show_new_last(last_value[7],last_value[16],last_value[8],"ms");
            show_new_last(last_value[9],last_value[17],last_value[10],"fps");
            show_new_last(last_value[11],last_value[18],last_value[12],"acc");
            printf("</tr>")

            // current vs last
            printf("</tr>\n<tr><td>New/Last</td><td colspan=4>Mem Peak:%s</td>", pb_size_[3]);

            compare_result(last_value[2],current_value[2],"ms");
            compare_result(current_value[4],last_value[4],"fps");
            compare_result(current_value[6],last_value[6],"acc");
            compare_result(last_value[8],current_value[8],"ms");
            compare_result(current_value[10],last_value[10],"fps");
            compare_result(current_value[12],last_value[12],"acc");
            printf("</tr>\n");

        }
    ' >> ${WORKSPACE}/report.html
}

function generate_html_head {

cat > ${WORKSPACE}/report.html << eof

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html lang="en">
<head>
    <meta http-equiv="content-type" content="text/html; charset=ISO-8859-1">
    <title>Daily Tests - TensorFlow - Jenkins</title>
    <style type="text/css">
        body
        {
            margin: 0;
            padding: 0;
            background: white no-repeat left top;
        }
        #main
        {
            // width: 100%;
            margin: 20px auto 10px auto;
            background: white;
            -moz-border-radius: 8px;
            -webkit-border-radius: 8px;
            padding: 0 30px 30px 30px;
            border: 1px solid #adaa9f;
            -moz-box-shadow: 0 2px 2px #9c9c9c;
            -webkit-box-shadow: 0 2px 2px #9c9c9c;
        }
        .features-table
        {
          width: 100%;
          margin: 0 auto;
          border-collapse: separate;
          border-spacing: 0;
          text-shadow: 0 1px 0 #fff;
          color: #2a2a2a;
          background: #fafafa;
          background-image: -moz-linear-gradient(top, #fff, #eaeaea, #fff); /* Firefox 3.6 */
          background-image: -webkit-gradient(linear,center bottom,center top,from(#fff),color-stop(0.5, #eaeaea),to(#fff));
          font-family: Verdana,Arial,Helvetica
        }
        .features-table th,td
        {
          text-align: center;
          height: 25px;
          line-height: 25px;
          padding: 0 8px;
          border: 1px solid #cdcdcd;
          box-shadow: 0 1px 0 white;
          -moz-box-shadow: 0 1px 0 white;
          -webkit-box-shadow: 0 1px 0 white;
          white-space: nowrap;
        }
        .no-border th
        {
          box-shadow: none;
          -moz-box-shadow: none;
          -webkit-box-shadow: none;
        }
        .col-cell
        {
          text-align: center;
          width: 150px;
          font: normal 1em Verdana, Arial, Helvetica;
        }
        .col-cell3
        {
          background: #efefef;
          background: rgba(144,144,144,0.15);
        }
        .col-cell1, .col-cell2
        {
          background: #B0C4DE;
          background: rgba(176,196,222,0.3);
        }
        .col-cellh
        {
          font: bold 1.3em 'trebuchet MS', 'Lucida Sans', Arial;
          -moz-border-radius-topright: 10px;
          -moz-border-radius-topleft: 10px;
          border-top-right-radius: 10px;
          border-top-left-radius: 10px;
          border-top: 1px solid #eaeaea !important;
        }
        .col-cellf
        {
          font: bold 1.4em Georgia;
          -moz-border-radius-bottomright: 10px;
          -moz-border-radius-bottomleft: 10px;
          border-bottom-right-radius: 10px;
          border-bottom-left-radius: 10px;
          border-bottom: 1px solid #dadada !important;
        }
    </style>
</head>
eof
}

function generate_html_footer {

    cat >> ${WORKSPACE}/report.html << eof
    </div>
</body>
</html>
eof
}

main
