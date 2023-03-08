#!/bin/bash
set -x

function main {
    echo "summaryLog: ${summaryLog}"
    echo "summaryLogLast: ${summaryLogLast}"
    echo "inferencerSummaryLog: ${inferencerSummaryLog}"
    echo "inferencerSummaryLogLast: ${inferencerSummaryLogLast}"
    echo "overviewLog: ${overviewLog}"
    echo "tunelog: ${tuneLog}"
    echo "last tunelog: ${tuneLogLast}"
    echo "coverage_summary_optimize: ${coverage_summary_optimize}"
    echo "coverage_summary_deploy: ${coverage_summary_deploy}"
    echo "coverage_summary_optimize_base: ${coverage_summary_optimize_base}"
    echo "coverage_summary_deploy_base: ${coverage_summary_deploy_base}"
    echo "launcherSummaryLog: ${launcherSummaryLog}"
    echo "launcherSummaryLogLast: ${launcherSummaryLogLast}"
    echo "feature_tests_summary: ${feature_tests_summary}"

    generate_html_head
    generate_html_overview
    generate_optimize_results
    generate_deploy_results
    [[ -f ${launcherSummaryLog} ]] && generate_launcher_benchmark
    generate_deploy_benchmark
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
    
    uts=$(sed '1d' ${overviewLog} | grep "^unit_test" | cut -d',' -f1 | awk '!a[$0]++')

    for ut in ${uts[@]}
    do
      ut_info_list=($(grep "${ut}" ${overviewLog} |sed 's/,/ /g'))
      ut_name="${ut_info_list[0]}"
      ut_status="${ut_info_list[1]}"
      ut_link="${ut_info_list[2]}"
      if [[ "${ut_status}" == *"FAIL"* ]];then
        unit_test_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
      elif [[ "${ut_status}" == *"SUCC"* ]];then
        unit_test_status="<td style=\"background-color:#90EE90\">Pass</td>"
      else
        unit_test_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
      fi

      echo """
      <tr>
      <td>${ut_name}</td>
      <td style=\"text-align:left\"><a href=\"${ut_link}\">ut_link</a></td>
      ${unit_test_status}
      </tr>
      """ >> ${WORKSPACE}/report.html
    done  
    gtests=$(grep 'engine_ut_gtest' ${overviewLog} | cut -d',' -f1 | awk '!a[$0]++')
    for ut in ${gtests[@]}
    do
      ut_info_list=($(grep "${ut}" ${overviewLog} |sed 's/,/ /g'))
      ut_name="${ut_info_list[0]}"
      ut_status="${ut_info_list[1]}"
      ut_link="${ut_info_list[2]}"
      if [[ "${ut_status}" == *"FAIL"* ]];then
        unit_test_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
      elif [[ "${ut_status}" == *"SUCC"* ]];then
        unit_test_status="<td style=\"background-color:#90EE90\">Pass</td>"
      else
        unit_test_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
      fi

      echo """
      <tr>
      <td>${ut_name}</td>
      <td style=\"text-align:left\"><a href=\"${ut_link}\">ut_link</a></td>
      ${unit_test_status}
      </tr>
      """ >> ${WORKSPACE}/report.html
    done 

    pytests=$(grep 'engine_ut_pytest' ${overviewLog} | cut -d',' -f1 | awk '!a[$0]++')
    for ut in ${pytests[@]}
    do
      ut_info_list=($(grep "${ut}" ${overviewLog} |sed 's/,/ /g'))
      ut_name="${ut_info_list[0]}"
      ut_status="${ut_info_list[1]}"
      ut_link="${ut_info_list[2]}"
      if [[ "${ut_status}" == *"FAIL"* ]];then
        unit_test_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
      elif [[ "${ut_status}" == *"SUCC"* ]];then
        unit_test_status="<td style=\"background-color:#90EE90\">Pass</td>"
      else
        unit_test_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
      fi

      echo """
      <tr>
      <td>${ut_name}</td>
      <td style=\"text-align:left\"><a href=\"${ut_link}\">ut_link</a></td>
      ${unit_test_status}
      </tr>
      """ >> ${WORKSPACE}/report.html
    done 

    format_scan=($(grep 'nlp-format-scan-localtest,format' ${overviewLog} |sed 's/,/ /g'))
    if [[ "${format_scan[2]}" == *"FAIL"* ]];then
        format_scan_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${format_scan[2]}" == *"SUCC"* ]];then
        format_scan_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        format_scan_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi

    cpplint_scan=($(grep 'nlp-format-scan-localtest,cpplint' ${overviewLog} |sed 's/,/ /g'))
    if [[ "${cpplint_scan[2]}" == *"FAIL"* ]];then
        cpplint_scan_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${cpplint_scan[2]}" == *"SUCC"* ]];then
        cpplint_scan_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        cpplint_scan_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi

    pylint_scan=($(grep 'nlp-format-scan-localtest,pylint' ${overviewLog} |sed 's/,/ /g'))
    if [[ "${pylint_scan[2]}" == *"FAIL"* ]];then
        pylint_scan_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${pylint_scan[2]}" == *"SUCC"* ]];then
        pylint_scan_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        pylint_scan_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi

    bandit_scan=($(grep 'nlp-format-scan-localtest,bandit' ${overviewLog} |sed 's/,/ /g'))
    if [[ "${bandit_scan[2]}" == *"FAIL"* ]];then
        bandit_scan_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${bandit_scan[2]}" == *"SUCC"* ]];then
        bandit_scan_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        bandit_scan_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi

    spellcheck_scan=($(grep 'nlp-format-scan-localtest,pyspelling' ${overviewLog} |sed 's/,/ /g'))
    if [[ "${spellcheck_scan[2]}" == *"FAIL"* ]];then
        spellcheck_scan_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${spellcheck_scan[2]}" == *"SUCC"* ]];then
        spellcheck_scan_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        spellcheck_scan_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi

    copyright_check=($(grep 'nlp-toolkit-copyright-check' ${overviewLog} |sed 's/,/ /g'))
    if [[ "${copyright_check[1]}" == *"FAIL"* ]];then
        copyright_check_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${copyright_check[1]}" == *"SUCC"* ]];then
        copyright_check_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        copyright_check_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi

    cat >> ${WORKSPACE}/report.html <<  eof

        $(
             if [ "${format_scan[3]}" != "" ]; then
                 echo "<tr><td>clang-format Scan</td>"
                 echo "<td style=\"text-align:left\"><a href=\"${jenkins_job_url}${format_scan[0]}/${format_scan[3]}\">${format_scan[0]}#${format_scan[3]}</a></td>"
                 echo "${format_scan_status}</tr>"
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
                 echo "<td style=\"text-align:left\"><a href=\"${jenkins_job_url}intel-lpot-copyright-check/${copyright_check[2]}/artifact/copyright_issue_summary.log\">${copyright_check[0]}#${copyright_check[2]}</a></td>"
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
Test_Info="<th colspan="4">${nlp_branch}</th> <th colspan="4">${nlp_commit}</th> "

cat >> ${WORKSPACE}/report.html << eof

<body>
    <div id="main">
        <h1 align="center">NLP-TOOLKIT Tests
        [ <a href="${RUN_DISPLAY_URL}">Job-${BUILD_NUMBER}</a> ]</h1>
      <h1 align="center">Test Status: ${Jenkins_job_status}</h1>
        <h2>Summary</h2>
        <table class="features-table">
            <tr>
              <th>Repo</th>
              ${Test_Info_Title}
              </tr>
              <tr>
                    <td><a href="https://github.com/intel-innersource/frameworks.ai.nlp-toolkit.intel-nlp-toolkit.git">NLP-TOOLKIT</a></td>
              ${Test_Info}
                </tr>
        </table>
eof

createOverview
createCoverageOverview
createFeatureTestsOverview

}

function createCoverageOverview {
    echo "Generating coverage overview for NLP-TOOLKIT."
    if [ ! -f "${coverage_summary_optimize}" ] || [ $(wc -l ${coverage_summary_optimize} | awk '{ print $1 }') -le 1 ]; then
        return 0
    fi
    
    lines_coverage=($(grep 'lines_coverage' ${coverage_summary_optimize} |sed 's/,/ /g'))
    branches_coverage=($(grep 'branches_coverage' ${coverage_summary_optimize} |sed 's/,/ /g'))

    echo """
        <h2>Code Coverage for NLP-TOOLKIT Optimize</h2>
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
    echo "Generating coverage overview for NLP-TOOLKIT."
    if [ ! -f "${coverage_summary_deploy}" ] || [ $(wc -l ${coverage_summary_deploy} | awk '{ print $1 }') -le 1 ]; then
        return 0
    fi
    
    lines_coverage=($(grep 'lines_coverage' ${coverage_summary_deploy} |sed 's/,/ /g'))
    branches_coverage=($(grep 'branches_coverage' ${coverage_summary_deploy} |sed 's/,/ /g'))

    echo """
        <h2>Code Coverage for NLP-TOOLKIT Backends</h2>
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

function createFeatureTestsOverview {
    pass_status="<td style=\"background-color:#90EE90\">Pass</td>"
    fail_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    verify_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    echo "Generating feature tests overview."
    if [ ! -f ${feature_tests_summary} ]; then
        return 0
    fi

    echo """
        <h2>Feature Test</h2>
        <table class=\"features-table\" style=\"width: auto;margin: 0 auto 0 0;\">
        <tr>
            <th style=\"padding: 5px 40px;\">Task</th>
            <th style=\"padding: 5px 40px;\">Platform</th>
            <th style=\"padding: 5px 20px;\">Status</th>
        </tr>
    """ >> ${WORKSPACE}/report.html

    features=$(sed '1d' ${feature_tests_summary} | cut -d';' -f2 | awk '!a[$0]++')

    for feature in ${features[@]}
    do
        feature_result=($(grep ${feature} ${feature_tests_summary} |sed 's/;/ /g'))
        feature_url=${feature_result[3]}
        platform=${feature_result[0]}
        if [[ "${feature_result[2]}" == "fail" ]];then
            feature_status=${fail_status}
        elif [[ "${feature_result[2]}" == "pass" ]];then
            feature_status=${pass_status}
        else
            feature_status=${verify_status}
        fi

        if [ "${feature_result[2]}" != "" ]; then
            echo """
            <tr>
            <td style=\"text-align:left;padding: 0 40px;\"><a href=\"${feature_url}\">${feature}</a></td>
            <td style=\"text-align:center;padding: 0 40px;\">${platform}</td>
            ${feature_status}
            </tr>
            """ >> ${WORKSPACE}/report.html
        fi
    done
    echo "</table>" >> ${WORKSPACE}/report.html
}

function generate_deploy_benchmark {

cat >> ${WORKSPACE}/report.html << eof
    <h2>Deploy Inferencer</h2>
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
          <th>BF16</th>
          <th colspan="2" class="col-cell col-cell1 col-cellh">Ratio</th>
        </tr>
        <tr>
          <th>throughput</th>
          <th>throughput</th>
          <th>throughput</th>
          <th colspan="2" class="col-cell col-cell1"><font size="2px">FP32/INT8</font></th>
        </tr>
eof

    mode='throughput'
    models=$(cat ${inferencerSummaryLog} |grep "${mode}," |cut -d',' -f3 |awk '!a[$0]++')
    for model in ${models[@]}
    do
        seq_lens=$(cat ${inferencerSummaryLog} |grep "${mode},${model}," |cut -d',' -f4 |awk '!a[$0]++')
        for seq_len in ${seq_lens[@]}
        do
            full_cores=$(cat ${inferencerSummaryLog} |grep "${mode},${model},${seq_len}," |cut -d',' -f5 |awk '!a[$0]++')
            for full_core in ${full_cores[@]}
            do
                core_per_inss=$(cat ${inferencerSummaryLog} |grep "${mode},${model},${seq_len},${full_core}," |cut -d',' -f6 |awk '!a[$0]++')
                for core_per_ins in ${core_per_inss[@]}
                do
                    bss=$(cat ${inferencerSummaryLog} |grep "${mode},${model},${seq_len},${full_core},${core_per_ins}," |cut -d',' -f7 |awk '!a[$0]++')
                    for bs in ${bss[@]}
                    do
                        benchmark_pattern="${mode},${model},${seq_len},${full_core},${core_per_ins},${bs}"
                        benchmark_int8=$(cat ${inferencerSummaryLog} |grep "${benchmark_pattern},int8" |cut -d',' -f9)
                        benchmark_int8_url=$(cat ${inferencerSummaryLog} |grep "${benchmark_pattern}," | tail -1 | cut -d',' -f10)
                        benchmark_fp32=$(cat ${inferencerSummaryLog} |grep "${benchmark_pattern},fp32" |cut -d',' -f9)
                        benchmark_fp32_url=$(cat ${inferencerSummaryLog} |grep "${benchmark_pattern},fp32" |cut -d',' -f10)
                        benchmark_bf16=$(cat ${inferencerSummaryLog} |grep "${benchmark_pattern},bf16" |cut -d',' -f9)
                        benchmark_bf16_url=$(cat ${inferencerSummaryLog} |grep "${benchmark_pattern},bf16" |cut -d',' -f10)
                        if [ $(cat ${inferencerSummaryLogLast} |grep -c "${benchmark_pattern},int8") == 0 ]; then
                            benchmark_int8_last=nan
                            benchmark_int8_url_last=nan
                            benchmark_fp32_last=nan
                            benchmark_fp32_url_last=nan
                            benchmark_bf16_last=nan
                            benchmark_bf16_url_last=nan
                        else
                            benchmark_int8_last=$(cat ${inferencerSummaryLogLast} |grep "${benchmark_pattern},int8" |cut -d',' -f9)
                            benchmark_int8_url_last=$(cat ${inferencerSummaryLogLast} |grep "${benchmark_pattern},int8" |cut -d',' -f10)
                            benchmark_fp32_last=$(cat ${inferencerSummaryLogLast} |grep "${benchmark_pattern},fp32" |cut -d',' -f9)
                            benchmark_fp32_url_last=$(cat ${inferencerSummaryLogLast} |grep "${benchmark_pattern},fp32" |cut -d',' -f10)
                            benchmark_bf16_last=$(cat ${inferencerSummaryLogLast} |grep "${benchmark_pattern},bf16" |cut -d',' -f9)
                            benchmark_bf16_url_last=$(cat ${inferencerSummaryLogLast} |grep "${benchmark_pattern},bf16" |cut -d',' -f10)
                        fi
                        generate_perf_core
                    done
                done
            done
        done
    done
    cat >> ${WORKSPACE}/report.html << eof
        <tr>
            <td colspan="8"><font color="#d6776f">Note: </font>All data tested on INC Dedicated Server.</td>
            <td colspan="2" class="col-cell col-cell1 col-cellf"></td>
        </tr>
    </table>
eof
}

function generate_launcher_benchmark {

cat >> ${WORKSPACE}/report.html << eof
    <h2>Deploy Launcher</h2>
      <table class="features-table">
        <tr>
          <th rowspan="2">Model</th>
          <th rowspan="2">Launcher_mode</th>
          <th rowspan="2">VS</th>
          <th rowspan="2">Batch_size</th>
          <th rowspan="2">NCores<br>per Instance</th>
          <th>INT8</th>
          <th>FP32</th>
          <th>BF16</th>
          <th class="col-cell col-cell1 col-cellh">Ratio</th>
        </tr>
        <tr>
          <th>throughput</th>
          <th>throughput</th>
          <th>throughput</th>
          <th class="col-cell col-cell1"><font size="2px">FP32/INT8</font></th>
        </tr>
eof

    framework='engine'
    models=$(cat ${launcherSummaryLog} |grep "${framework}," |cut -d',' -f3 |awk '!a[$0]++')
    for model in ${models[@]}
    do 
        modes=$(cat ${launcherSummaryLog} |grep "${framework},.*,${model}," |cut -d',' -f2 |awk '!a[$0]++')
        for mode in ${modes[@]}
        do
            batch_size=$(cat ${launcherSummaryLog} |grep "${mode},${model}," |cut -d',' -f4 |awk '!a[$0]++')
            for batch in ${batch_size[@]}
            do
                core_per_ins_int8=$(cat ${launcherSummaryLog} |grep "${mode},${model},${batch},.*,.*,int8" |cut -d',' -f5 |awk '!a[$0]++')
                benchmark_pattern_int8="${mode},${model},${batch},${core_per_ins_int8}"
                benchmark_int8=$(cat ${launcherSummaryLog} |grep "${benchmark_pattern_int8},.*,int8" |cut -d',' -f6)
                benchmark_int8_url=$(cat ${launcherSummaryLog} |grep "${benchmark_pattern_int8},.*,int8," | tail -1 | cut -d',' -f8)
                core_per_ins_fp32=$(cat ${launcherSummaryLog} |grep "${mode},${model},${batch},.*,.*,fp32" |cut -d',' -f5 |awk '!a[$0]++')
                benchmark_pattern_fp32="${mode},${model},${batch},${core_per_ins_fp32}"
                benchmark_fp32=$(cat ${launcherSummaryLog} |grep "${benchmark_pattern_fp32},.*,fp32" |cut -d',' -f6)
                benchmark_fp32_url=$(cat ${launcherSummaryLog} |grep "${benchmark_pattern_fp32},.*,fp32," |cut -d',' -f8)
                core_per_ins_bf16=$(cat ${launcherSummaryLog} |grep "${mode},${model},${batch},.*,.*,bf16" |cut -d',' -f5 |awk '!a[$0]++')
                benchmark_pattern_bf16="${mode},${model},${batch},${core_per_ins_bf16}"
                benchmark_bf16=$(cat ${launcherSummaryLog} |grep "${benchmark_pattern_bf16},.*,bf16" |cut -d',' -f6)
                benchmark_bf16_url=$(cat ${launcherSummaryLog} |grep "${benchmark_pattern_bf16},.*,bf16," |cut -d',' -f8)
                core_per_ins=$core_per_ins_int8
                if [ $(cat ${launcherSummaryLogLast} |grep -c "${benchmark_pattern_int8},.*,int8") == 0 ]; then
                    benchmark_int8_last=nan
                    benchmark_int8_url_last=nan
                    benchmark_fp32_last=nan
                    benchmark_fp32_url_last=nan
                    benchmark_bf16_last=nan
                    benchmark_bf16_url_last=nan
                else
                    benchmark_int8_last=$(cat ${launcherSummaryLogLast} |grep "${benchmark_pattern_int8},.*,int8" |cut -d',' -f6)
                    benchmark_int8_url_last=$(cat ${launcherSummaryLogLast} |grep "${benchmark_pattern_int8},.*,int8" |cut -d',' -f8)
                    benchmark_fp32_last=$(cat ${launcherSummaryLogLast} |grep "${benchmark_pattern_fp32},.*,fp32" |cut -d',' -f6)
                    benchmark_fp32_url_last=$(cat ${launcherSummaryLogLast} |grep "${benchmark_pattern_fp32},.*,fp32" |cut -d',' -f8)
                    benchmark_bf16_last=$(cat ${launcherSummaryLogLast} |grep "${benchmark_pattern_bf16},.*,bf16" |cut -d',' -f6)
                    benchmark_bf16_url_last=$(cat ${launcherSummaryLogLast} |grep "${benchmark_pattern_bf16},.*,bf16" |cut -d',' -f8)
                fi
                generate_launcher_core
            done
        done
    done
    cat >> ${WORKSPACE}/report.html << eof
        <tr>
            <td colspan="8"><font color="#d6776f">Note: </font>All data tested on INC Dedicated Server.</td>
            <td class="col-cell col-cell1 col-cellf"></td>
        </tr>
    </table>
eof
}

function generate_launcher_core {
    echo "<tr><td rowspan=2>${model}</td><td rowspan=2>${mode}</td><td>New</td><td rowspan=2>${batch}</td><td rowspan=2>${core_per_ins}</td>" >> ${WORKSPACE}/report.html

    echo | awk -v b_int8=${benchmark_int8} -v b_int8_url=${benchmark_int8_url} -v b_fp32=${benchmark_fp32} -v b_fp32_url=${benchmark_fp32_url} -v b_bf16=${benchmark_bf16} -v b_bf16_url=${benchmark_bf16_url} -v b_int8_l=${benchmark_int8_last} -v b_int8_url_l=${benchmark_int8_url_last} -v b_fp32_l=${benchmark_fp32_last} -v b_fp32_url_l=${benchmark_fp32_url_last} -v b_bf16_l=${benchmark_bf16_last} -v b_bf16_url_l=${benchmark_bf16_url_last} '
        function show_benchmark(a,b) {
            if(a ~/[1-9]/) {
                    printf("<td><a href=%s>%.2f</a></td>\n",b,a);
            }else {
                if(a == "") {
                    printf("<td><a href=%s>%s</a></td>\n",b,a);
                }else{
                    printf("<td></td>\n");
                }
            }
        }

        function compare_current(a,b) {

            if(a ~/[1-9]/ && b ~/[1-9]/) {
                target = a / b;
                if(target >= 2) {
                   printf("<td rowspan=2 style=\"background-color:#90EE90\">%.2f</td>", target);
                }else if(target < 1) {
                   printf("<td rowspan=2 style=\"background-color:#FFD2D2\">%.2f</td>", target);
                }else{
                   printf("<td rowspan=2>%.2f</td>", target);
                }
            }else{
                printf("<td rowspan=2></td>");
            }

        }


        BEGIN {
            job_status = "pass"
        }{
            // current
            show_benchmark(b_int8,b_int8_url)
            show_benchmark(b_fp32,b_fp32_url)
            show_benchmark(b_bf16,b_bf16_url)

            // current comparison
            compare_current(b_int8,b_fp32)

            // Last
            printf("</tr>\n<tr><td>Last</td>")
            show_benchmark(b_int8_l,b_int8_url_l)
            show_benchmark(b_fp32_l,b_fp32_url_l)
            show_benchmark(b_bf16_l,b_bf16_url_l)
            
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

function generate_perf_core {
    echo "<tr><td rowspan=3>${model}</td><td rowspan=3>${seq_len}</td><td>New</td><td rowspan=2>${full_core}</td><td rowspan=2>${core_per_ins}</td><td rowspan=2>${bs}</td>" >> ${WORKSPACE}/report.html

    echo | awk -v b_int8=${benchmark_int8} -v b_int8_url=${benchmark_int8_url} -v b_fp32=${benchmark_fp32} -v b_fp32_url=${benchmark_fp32_url} -v b_bf16=${benchmark_bf16} -v b_bf16_url=${benchmark_bf16_url} -v b_int8_l=${benchmark_int8_last} -v b_int8_url_l=${benchmark_int8_url_last} -v b_fp32_l=${benchmark_fp32_last} -v b_fp32_url_l=${benchmark_fp32_url_last} -v b_bf16_l=${benchmark_bf16_last} -v b_bf16_url_l=${benchmark_bf16_url_last} '
        function show_benchmark(a,b) {
            if(a ~/[1-9]/) {
                    printf("<td><a href=%s>%.2f</a></td>\n",b,a);
            }else {
                if(a == "") {
                    printf("<td><a href=%s>%s</a></td>\n",b,a);
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
            show_benchmark(b_bf16,b_bf16_url)

            // current comparison
            compare_current(b_int8,b_fp32)

            // Last
            printf("</tr>\n<tr><td>Last</td>")
            show_benchmark(b_int8_l,b_int8_url_l)
            show_benchmark(b_fp32_l,b_fp32_url_l)
            show_benchmark(b_bf16_l,b_bf16_url_l)

            // current vs last
            printf("</tr>\n<tr><td>New/Last</td><td colspan=3 class=\"col-cell3\"></td>");
            compare_new_last(b_int8,b_int8_l)
            compare_new_last(b_fp32,b_fp32_l)
            compare_new_last(b_bf16,b_bf16_l)
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

function generate_optimize_results {

cat >> ${WORKSPACE}/report.html << eof
    <h2>Optimize Result</h2>
      <table class="features-table">
          <tr>
                <th rowspan="2">Platform</th>
                <th rowspan="2">System</th>
                <th rowspan="2">Framework</th>
                <th rowspan="2">Version</th>
                <th rowspan="2">Model</th>
                <th rowspan="2">VS</th>
                <th rowspan="2">Tuning<br>Time(s)</th>
                <th rowspan="2">Tuning<br>Count</th>
                <th colspan="4">INT8/BF16</th>
                <th colspan="4">FP32</th>
                <th colspan="2" class="col-cell col-cell1 col-cellh">Ratio</th>
          </tr>
          <tr>
                <th>bs</th>
                <th>imgs/s</th>
                <th>bs</th>
                <th>top1</th>

                <th>bs</th>
                <th>imgs/s</th>
                <th>bs</th>
                <th>top1</th>

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
            frameworks=$(sed '1d' ${summaryLog} |grep "^${os};${platform};optimize" |cut -d';' -f4 | awk '!a[$0]++')
            for framework in ${frameworks[@]}
            do
                fw_versions=$(sed '1d' ${summaryLog} |grep "^${os};${platform};optimize;${framework}" |cut -d';' -f5 | awk '!a[$0]++')
                for fw_version in ${fw_versions[@]}
                do
                    models=$(sed '1d' ${summaryLog} |grep "^${os};${platform};optimize;${framework};${fw_version}" |cut -d';' -f7 | awk '!a[$0]++')
                    for model in ${models[@]}
                    do
                        current_values=$(generate_inference ${summaryLog} "optimize")
                        last_values=$(generate_inference ${summaryLogLast} "optimize")

                        generate_tuning_core "optimize"
                    done
                done
            done
        done
    done

    cat >> ${WORKSPACE}/report.html << eof
            <tr>
                <td colspan="17"><font color="#d6776f">Note: </font>All data tested on INC Dedicated Server.</td>
                <td colspan="3" class="col-cell col-cell1 col-cellf"></td>
            </tr>
    </table>
eof
}

function generate_deploy_results {

cat >> ${WORKSPACE}/report.html << eof
    <h2>Deploy Result</h2>
      <table class="features-table">
          <tr>
                <th rowspan="2">Platform</th>
                <th rowspan="2">System</th>
                <th rowspan="2">Framework</th>
                <th rowspan="2">Version</th>
                <th rowspan="2">Model</th>
                <th rowspan="2">VS</th>
                <th rowspan="2">Tuning<br>Time(s)</th>
                <th rowspan="2">Tuning<br>Count</th>
                <th colspan="4">INT8</th>
                <th colspan="4">FP32</th>
                <th colspan="4">BF16</th>
                <th colspan="2" class="col-cell col-cell1 col-cellh">Ratio</th>
          </tr>
          <tr>
                <th>bs</th>
                <th>imgs/s</th>
                <th>bs</th>
                <th>top1</th>

                <th>bs</th>
                <th>imgs/s</th>
                <th>bs</th>
                <th>top1</th>

                <th>bs</th>
                <th>imgs/s</th>
                <th>bs</th>
                <th>top1</th>

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
            frameworks=$(sed '1d' ${summaryLog} |grep "^${os};${platform};deploy" |cut -d';' -f4 | awk '!a[$0]++')
            for framework in ${frameworks[@]}
            do
                fw_versions=$(sed '1d' ${summaryLog} |grep "^${os};${platform};deploy;${framework}" |cut -d';' -f5 | awk '!a[$0]++')
                for fw_version in ${fw_versions[@]}
                do
                    models=$(sed '1d' ${summaryLog} |grep "^${os};${platform};deploy;${framework};${fw_version}" |cut -d';' -f7 | awk '!a[$0]++')
                    for model in ${models[@]}
                    do
                        current_values=$(generate_inference ${summaryLog} "deploy")
                        last_values=$(generate_inference ${summaryLogLast} "deploy")

                        generate_tuning_core "deploy"
                    done
                done
            done
        done
    done

    cat >> ${WORKSPACE}/report.html << eof
            <tr>
                <td colspan="17"><font color="#d6776f">Note: </font>All data tested on INC Dedicated Server.</td>
                <td colspan="3" class="col-cell col-cell1 col-cellf"></td>
            </tr>
    </table>
eof
}

function generate_inference {
    local workflow=$2
    awk -v framework="${framework}" -v workflow="${workflow}" -v fw_version="${fw_version}" -v model="${model}" -v os="${os}" -v platform=${platform} -F ';' '
        BEGINE {
            fp32_perf_bs = "nan";
            fp32_perf_value = "nan";
            fp32_perf_url = "nan";
            fp32_acc_bs = "nan";
            fp32_acc_value = "nan";
            fp32_acc_url = "nan";

            int8_perf_bs = "nan";
            int8_perf_value = "nan";
            int8_perf_url = "nan";
            int8_acc_bs = "nan";
            int8_acc_value = "nan";
            int8_acc_url = "nan";

            bf16_perf_bs = "nan";
            bf16_perf_value = "nan";
            bf16_perf_url = "nan";
            bf16_acc_bs = "nan";
            bf16_acc_value = "nan";
            bf16_acc_url = "nan";
        }{
            if($1 == os && $2 == platform && $3 == workflow && $4 == framework && $5 == fw_version && $7 == model) {
                // FP32
                if($6 == "FP32") {
                    // Performance
                    if($9 == "Performance") {
                        fp32_perf_bs = $10;
                        fp32_perf_value = $11;
                        fp32_perf_url = $12;
                    }
                    // Accuracy
                    if($9 == "Accuracy") {
                        fp32_acc_bs = $10;
                        fp32_acc_value = $11;
                        fp32_acc_url = $12;
                    }
                }

                // INT8
                if($6 == "INT8") {
                    // Performance
                    if($9 == "Performance") {
                        int8_perf_bs = $10;
                        int8_perf_value = $11;
                        int8_perf_url = $12;
                    }
                    // Accuracy
                    if($9 == "Accuracy") {
                        int8_acc_bs = $10;
                        int8_acc_value = $11;
                        int8_acc_url = $12;
                    }
                }
                if($6 == "BF16") {
                    // Performance
                    if($9 == "Performance") {
                        bf16_perf_bs = $10;
                        bf16_perf_value = $11;
                        bf16_perf_url = $12;
                    }
                    // Accuracy
                    if($9 == "Accuracy") {
                        bf16_acc_bs = $10;
                        bf16_acc_value = $11;
                        bf16_acc_url = $12;
                    }
                }
            }
        }END {
            printf("%s;%s;%s;%s;", int8_perf_bs,int8_perf_value,int8_acc_bs,int8_acc_value);
            printf("%s;%s;%s;%s;", fp32_perf_bs,fp32_perf_value,fp32_acc_bs,fp32_acc_value);
            printf("%s;%s;%s;%s;", int8_perf_url,int8_acc_url,fp32_perf_url,fp32_acc_url);
            printf("%s;%s;%s;%s;%s;%s;", bf16_perf_bs,bf16_perf_value,bf16_perf_url,bf16_acc_bs,bf16_acc_value,bf16_acc_url);
        }
    ' $1
}

function generate_tuning_core {
    local workflow=$1
    tuning_time=$(grep "^${os};${platform};${workflow};${framework};${fw_version};${model};" ${tuneLog} |awk -F';' '{print $7}')
    tuning_count=$(grep "^${os};${platform};${workflow};${framework};${fw_version};${model};" ${tuneLog} |awk -F';' '{print $8}')
    tuning_log=$(grep "^${os};${platform};${workflow};${framework};${fw_version};${model};" ${tuneLog} |awk -F';' '{print $9}')
    echo "<tr><td rowspan=3>${platform}</td><td rowspan=3>${os}</td><td rowspan=3>${framework}</td><td rowspan=3>${fw_version}</td><td rowspan=3>${model}</td><td>New</td>" >> ${WORKSPACE}/report.html
    echo "<td><a href=${tuning_log}>${tuning_time}</a></td><td><a href=${tuning_log}>${tuning_count}</a></td>" >> ${WORKSPACE}/report.html

    tuning_time=$(grep "^${os};${platform};${workflow};${framework};${fw_version};${model};" ${tuneLogLast} |awk -F';' '{print $7}')
    tuning_count=$(grep "^${os};${platform};${workflow};${framework};${fw_version};${model};" ${tuneLogLast} |awk -F';' '{print $8}')
    tuning_log=$(grep "^${os};${platform};${workflow};${framework};${fw_version};${model};" ${tuneLogLast} |awk -F';' '{print $9}')

    echo |awk -F ';' -v current_values="${current_values}" -v last_values="${last_values}" \
              -v tuning_time="${tuning_time}" \
              -v tuning_count="${tuning_count}" -v tuning_log="${tuning_log}" -v workflow="${workflow}"  '

        function abs(x) { return x < 0 ? -x : x }

        function show_new_last(batch, link, value, metric) {
            if(value ~/[1-9]/) {
                if (metric == "perf") {
                    printf("<td>%s</td> <td><a href=%s>%.2f</a></td>\n",batch,link,value);
                } else {
                    if (value <= 1){
                        printf("<td>%s</td> <td><a href=%s>%.2f%</a></td>\n",batch,link,value*100);
                    }else{
                        printf("<td>%s</td> <td><a href=%s>%.2f</a></td>\n",batch,link,value);
                    }
                }
            } else {
                if(link == "" || value == "N/A") {
                    printf("<td></td> <td></td>\n");
                } else {
                    printf("<td>%s</td> <td><a href=%s>Failure</a></td>\n",batch,link);
                }
            }
        }

        function compare_current(int8_result, fp32_result, metric) {

            if(int8_result ~/[1-9]/ && fp32_result ~/[1-9]/) {
                if(metric == "acc") {
                    target = (int8_result - fp32_result) / fp32_result;
                    if(target >= -0.01) {
                       printf("<td rowspan=3 style=\"background-color:#90EE90\">%.2f%</td>", target*100);
                    }else if(target < -0.05) {
                       printf("<td rowspan=3 style=\"background-color:#FFD2D2\">%.2f%</td>", target*100);
                    }else{
                       printf("<td rowspan=3>%.2f%</td>", target*100);
                    }
                } else if(metric == "perf") {
                    target = int8_result / fp32_result;
                    if(target >= 2) {
                       printf("<td rowspan=3 style=\"background-color:#90EE90\">%.2f</td>", target);
                    }else if(target < 1) {
                       printf("<td rowspan=3 style=\"background-color:#FFD2D2\">%.2f</td>", target);
                    }else{
                       printf("<td rowspan=3>%.2f</td>", target);
                    }
                }else {
                    // Compare model size
                    target = int8_result / fp32_result;
                    if(target > 1) {
                       printf("<td rowspan=3 style=\"background-color:#FFD2D2\">%s/%s/%s</td>", int8_result, fp32_result, metric);
                    }else{
                       printf("<td rowspan=3>%s/%s/%s</td>", int8_result, fp32_result, metric);
                    }
                }
            }else {
                printf("<td rowspan=3></td>");
            }
        }

        function compare_result(new_result, previous_result, metric) {

            if (new_result ~/[1-9]/ && previous_result ~/[1-9]/) {
                if(metric == "acc") {
                    target = new_result - previous_result;
                    if(target >= -0.0001 && target <= 0.0001) {
                        status_png = "background-color:#90EE90";
                    } else {
                        status_png = "background-color:#FFD2D2";
                    }
                    if (new_result <= 1){
                        printf("<td style=\"%s\" colspan=2>%.2f%</td>", status_png, target*100);
                    }else{
                        printf("<td style=\"%s\" colspan=2>%.2f</td>", status_png, target);
                    }
                } else {
                    target = new_result / previous_result;
                    if(target >= 0.95) {
                        status_png = "background-color:#90EE90";
                    } else {
                        status_png = "background-color:#FFD2D2";
                    }
                    printf("<td style=\"%s\" colspan=2>%.2f</td>", status_png, target);
                }
            } else {
                if(new_result == "nan" || previous_result == "nan") {
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

            // INT8 Performance results
            int8_perf_batch=current_value[1]
            int8_perf_value=current_value[2]
            int8_perf_url=current_value[9]
            show_new_last(int8_perf_batch, int8_perf_url, int8_perf_value, "perf");

            // INT8 Accuracy results
            int8_acc_batch=current_value[3]
            int8_acc_value=current_value[4]
            int8_acc_url=current_value[10]
            show_new_last(int8_acc_batch, int8_acc_url, int8_acc_value, "acc");

            // FP32 Performance results
            fp32_perf_batch=current_value[5]
            fp32_perf_value=current_value[6]
            fp32_perf_url=current_value[11]
            show_new_last(fp32_perf_batch, fp32_perf_url, fp32_perf_value, "perf");

            // FP32 Accuracy results
            fp32_acc_batch=current_value[7]
            fp32_acc_value=current_value[8]
            fp32_acc_url=current_value[12]
            show_new_last(fp32_acc_batch, fp32_acc_url, fp32_acc_value, "acc");

            // BF16 Performance results
            if (workflow == "deploy") {
                bf16_perf_batch=current_value[13]
                bf16_perf_value=current_value[14]
                bf16_perf_url=current_value[15]
                show_new_last(bf16_perf_batch, bf16_perf_url, bf16_perf_value, "perf");
    
                // BF16 Accuracy results
                bf16_acc_batch=current_value[16]
                bf16_acc_value=current_value[17]
                bf16_acc_url=current_value[18]
                show_new_last(bf16_acc_batch, bf16_acc_url, bf16_acc_value, "acc");
            }
            

            // Compare Current
            compare_current(int8_perf_value, fp32_perf_value, "perf");
            compare_current(int8_acc_value, fp32_acc_value, "acc");

            // Last values
            split(last_values,last_value,";");

            // Last
            printf("</tr>\n<tr><td>Last</td><td><a href=%3$s>%1$s</a></td><td><a href=%3$s>%2$s</a></td>", tuning_time, tuning_count, tuning_log);

            // Show last INT8 Performance results
            last_int8_perf_batch=last_value[1]
            last_int8_perf_value=last_value[2]
            last_int8_perf_url=last_value[9]
            show_new_last(last_int8_perf_batch, last_int8_perf_url, last_int8_perf_value, "perf");

            // Show last INT8 Accuracy results
            last_int8_acc_batch=last_value[3]
            last_int8_acc_value=last_value[4]
            last_int8_acc_url=last_value[10]
            show_new_last(last_int8_acc_batch, last_int8_acc_url, last_int8_acc_value, "acc");

            // Show last FP32 Performance results
            last_fp32_perf_batch=last_value[5]
            last_fp32_perf_value=last_value[6]
            last_fp32_perf_url=last_value[11]
            show_new_last(last_fp32_perf_batch, last_fp32_perf_url, last_fp32_perf_value, "perf");

            // Show last FP32 Accuracy results
            last_fp32_acc_batch=last_value[7]
            last_fp32_acc_value=last_value[8]
            last_fp32_acc_url=last_value[12]
            show_new_last(last_fp32_acc_batch, last_fp32_acc_url, last_fp32_acc_value, "acc");

            if (workflow == "deploy") {
                // Show last BF16 Performance results
                last_bf16_perf_batch=last_value[13]
                last_bf16_perf_value=last_value[14]
                last_bf16_perf_url=last_value[15]
                show_new_last(last_bf16_perf_batch, last_bf16_perf_url, last_bf16_perf_value, "perf");

                // Show last BF16 Accuracy results
                last_bf16_acc_batch=last_value[16]
                last_bf16_acc_value=last_value[17]
                last_bf16_acc_url=last_value[18]
                show_new_last(last_bf16_acc_batch, last_bf16_acc_url, last_bf16_acc_value, "acc");
            }

            // current vs last
            printf("</tr>\n<tr><td>New/Last</td><td colspan=2 class=\"col-cell3\"></td>");

            // Compare INT8 Performance results
            compare_result(int8_perf_value, last_int8_perf_value,"perf");
            
            // Compare INT8 Accuracy results
            compare_result(int8_acc_value, last_int8_acc_value, "acc");

            // Compare FP32 Performance results
            compare_result(fp32_perf_value, last_fp32_perf_value, "perf");

            // Compare FP32 Performance results
            compare_result(fp32_acc_value, last_fp32_acc_value, "acc");

            // Compare BF16 Performance results
            compare_result(bf16_perf_value, last_bf16_perf_value, "perf");

            // Compare BF16 Performance results
            compare_result(bf16_acc_value, last_bf16_acc_value, "acc");
            
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
