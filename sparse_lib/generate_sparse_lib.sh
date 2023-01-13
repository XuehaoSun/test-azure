#!/bin/bash

function main {
    script_dir=$(dirname "${BASH_SOURCE[0]}")
    echo "report_title: ${report_title}"
    echo "summary_dir: ${summary_dir}"
    echo "summary_dir_last: ${summary_dir_last}"
    echo "modelSummaryLog: ${modelSummaryLog}"
    echo "modelSummaryLogLast: ${modelSummaryLogLast}"
    echo "modelInferencerSummaryLog: ${modelInferencerSummaryLog}"
    echo "modelInferencerSummaryLogLast: ${modelInferencerSummaryLogLast}"
    echo "modelTunelog: ${modelTuneLog}"
    echo "modelTuneLogLast: ${modelTuneLogLast}"
    echo "overview_log: ${overview_log}"
    echo "script_dir: ${script_dir}"

    conda_env_name="sparse_lib"
    [[ -d ${HOME}/anaconda3/bin ]] && export PATH=${HOME}/anaconda3/bin/:$PATH
    [[ -d ${HOME}/miniconda3/bin ]] && export PATH=${HOME}/miniconda3/bin/:$PATH
    if [[ ! $(conda info -e | grep ${conda_env_name}) ]]; then
        conda create -n ${conda_env_name} python=3.8 -y
    fi
    conda activate ${conda_env_name} || source activate ${conda_env_name}
    pip install --upgrade pip
    pip install pandas==1.4.4 Jinja2==3.1.2 openpyxl==3.0.10 matplotlib==3.5.3 --proxy=http://child-prc.intel.com:913
    pip freeze

    generate_html_head
    generate_html_overview
    for caselog in $(find $summary_dir/*_summary.log); do
        local name=$(basename $caselog | sed 's/_summary.log//')
        echo "<h2>$name <a href=\"${name}_summary.xlsx\" style=\"font-size: initial\">${name}_summary.xlsx</a> </h2>" >>${WORKSPACE}/report.html
        python "$script_dir/generate_sparse_lib.py" $caselog ${summary_dir_last}/$(basename $caselog) \
            >>${WORKSPACE}/report.html \
            2>>${WORKSPACE}/perf_regression.log
    done

    generate_deploy_results "$modelSummaryLog" "$modelSummaryLogLast"

    generate_deploy_benchmark "$modelInferencerSummaryLog" "$modelInferencerSummaryLogLast"

    generate_html_footer
}

function createOverview {

    jenkins_job_url="https://inteltf-jenk.sh.intel.com/job/"

    echo """
      <h2>Overview</h2>
      <table class=\"features-table\" style=\"width: 60%; 0 0;empty-cells: hide\">
        <tr>
            <th>Task</th>
            <th>Job</th>
            <th>Status</th>
        </tr>
    """ >>${WORKSPACE}/report.html
    gtest=($(grep 'deep-engine_ut_gtest' ${overview_log} | sed 's/,/ /g'))
    if [[ "${gtest[1]}" == *"FAIL"* ]]; then
        gtest_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${gtest[1]}" == *"SUCC"* ]]; then
        gtest_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        gtest_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi
    win_gtest=($(grep 'sparselib_windows_ut_gtest' ${overview_log} | sed 's/,/ /g'))
    if [[ "${win_gtest[1]}" == *"FAIL"* ]]; then
        win_gtest_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${win_gtest[1]}" == *"SUCC"* ]]; then
        win_gtest_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        win_gtest_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi
    cpplint_scan=($(grep 'sparse-lib-format-scan,cpplint' ${overview_log} | sed 's/,/ /g'))
    if [[ "${cpplint_scan[2]}" == *"FAIL"* ]]; then
        cpplint_scan_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${cpplint_scan[2]}" == *"SUCC"* ]]; then
        cpplint_scan_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        cpplint_scan_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi
    pylint_scan=($(grep 'sparse-lib-format-scan,pylint' ${overview_log} | sed 's/,/ /g'))
    if [[ "${pylint_scan[2]}" == *"FAIL"* ]]; then
        pylint_scan_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${pylint_scan[2]}" == *"SUCC"* ]]; then
        pylint_scan_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        pylint_scan_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi
    bandit_scan=($(grep 'sparse-lib-format-scan,bandit' ${overview_log} | sed 's/,/ /g'))
    if [[ "${bandit_scan[2]}" == *"FAIL"* ]]; then
        bandit_scan_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${bandit_scan[2]}" == *"SUCC"* ]]; then
        bandit_scan_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        bandit_scan_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi
    spellcheck_scan=($(grep 'sparse-lib-format-scan,pyspelling' ${overview_log} | sed 's/,/ /g'))
    if [[ "${spellcheck_scan[2]}" == *"FAIL"* ]]; then
        spellcheck_scan_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${spellcheck_scan[2]}" == *"SUCC"* ]]; then
        spellcheck_scan_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        spellcheck_scan_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi
    copyright_check=($(grep 'nlp-toolkit-copyright-check' ${overview_log} | sed 's/,/ /g'))
    if [[ "${copyright_check[1]}" == *"FAIL"* ]]; then
        copyright_check_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${copyright_check[1]}" == *"SUCC"* ]]; then
        copyright_check_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        copyright_check_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi
    cat >>${WORKSPACE}/report.html <<eof
        $(
        if [ "${gtest[2]}" != "" ]; then
            echo "<tr><td>sparse_lib gtest</td>"
            echo "<td style=\"text-align:left\"><a href=\"${gtest[2]}\">ut_link</a></td>"
            echo "${gtest_status}</tr>"
        fi
        if [ "${win_gtest[2]}" != "" ]; then
            echo "<tr><td>sparse_lib windows gtest</td>"
            echo "<td style=\"text-align:left\"><a href=\"${win_gtest[2]}\">ut_link</a></td>"
            echo "${win_gtest_status}</tr>"
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
    echo generate_html_overview...

    PR_TITLE=''
    Test_Info_Title=''
    Test_Info=''

    commit_id=$(echo ${ghprbActualCommit} | awk '{print substr($1,1,7)}')

    PR_TITLE="[ <a href='${ghprbPullLink}'>PR-${ghprbPullId}</a> ]"
    Test_Info_Title="<th colspan="2">Source Branch</th> <th colspan="4">Target Branch</th> <th colspan="4">Commit</th> "
    Test_Info="<td colspan="2">${MR_source_branch}</td> <td colspan="4">${MR_target_branch}</td> <td colspan="4">${commit_id}"

    local pr_comment_opt=""
    if [[ -n $job_params ]]; then
        pr_comment_opt="<div class='job-params'><pre>PR comment options=${job_params}</pre></div>"
    fi

    cat >>${WORKSPACE}/report.html <<eof

<body>
    <div id="main">
        <h1 align="center">Sparse Lib Tests ${PR_TITLE}
        [ <a href="${RUN_DISPLAY_URL}">Job-${BUILD_NUMBER}</a> ]</h1>
      <h1 align="center">Test Status: ${Jenkins_job_status}</h1>
        <h2>Summary</h2>
        ${pr_comment_opt}
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
}

function generate_html_head {
    echo generate_html_head...

    local pr_title=''
    if [[ -n $ghprbPullId ]]; then pr_title=" PR-$ghprbPullId"; fi
    local title_html="${report_title} ${JOB_NAME}-${BUILD_NUMBER}${pr_title}"

    cat >${WORKSPACE}/report.html <<eof

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html lang="en">
<head>
    <meta http-equiv="content-type" content="text/html; charset=ISO-8859-1">
    <title>${title_html}</title>
    <style type="text/css">
        body
        {
            margin: 0;
            padding: 0;
            background: white no-repeat left top;
        }
        #main
        {
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
        .summary-wrapper
        {
            display: flex;
            flex-wrap: wrap;
        }
        .summary-wrapper pre
        {
            margin: 1em;
        }
        .features-table th.index_name {
            overflow-wrap: break-word;
            word-break: break-all;
            white-space: break-spaces;
            min-width: 4em;
        }
    </style>
</head>
eof
}

function generate_html_footer {
    if [[ -s ${WORKSPACE}/perf_regression.log ]]; then
        echo "<h2>Regression Details</h2><div class='regression-deatils-wrapper'><pre>" >>${WORKSPACE}/report.html
        cat ${WORKSPACE}/perf_regression.log >>${WORKSPACE}/report.html
        echo "</pre></div>" >>${WORKSPACE}/report.html
    fi
    cat >>${WORKSPACE}/report.html <<eof
    </div>
</body>
</html>
eof
}

function generate_deploy_results {
    echo generate_deploy_results...

    cat >>${WORKSPACE}/report.html <<eof
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
    local summary=$1
    local summaryLast=$2
    oses=$(sed '1d' ${summary} | cut -d';' -f1 | awk '!a[$0]++')

    for os in ${oses[@]}; do
        platforms=$(sed '1d' ${summary} | grep "^${os}" | cut -d';' -f2 | awk '!a[$0]++')
        for platform in ${platforms[@]}; do
            frameworks=$(sed '1d' ${summary} | grep "^${os};${platform};deploy" | cut -d';' -f4 | awk '!a[$0]++')
            for framework in ${frameworks[@]}; do
                fw_versions=$(sed '1d' ${summary} | grep "^${os};${platform};deploy;${framework}" | cut -d';' -f5 | awk '!a[$0]++')
                for fw_version in ${fw_versions[@]}; do
                    models=$(sed '1d' ${summary} | grep "^${os};${platform};deploy;${framework};${fw_version}" | cut -d';' -f7 | awk '!a[$0]++')
                    for model in ${models[@]}; do
                        current_values=$(generate_inference ${summary} "deploy")
                        last_values=$(generate_inference ${summaryLast} "deploy")

                        generate_tuning_core "deploy"
                    done
                done
            done
        done
    done

    cat >>${WORKSPACE}/report.html <<eof
            <tr>
                <td colspan="20"><font color="#d6776f">Note: </font>All data tested on INC Dedicated Server.</td>
                <td colspan="3" class="col-cell col-cell1 col-cellf"></td>
            </tr>
    </table>
eof
}

function generate_deploy_benchmark {
    echo generate_deploy_benchmark...

    cat >>${WORKSPACE}/report.html <<eof
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

    local summary=$1
    local summaryLast=$2
    mode='throughput'
    models=$(cat ${summary} | grep "${mode}," | cut -d',' -f3 | awk '!a[$0]++')
    for model in ${models[@]}; do
        seq_lens=$(cat ${summary} | grep "${mode},${model}," | cut -d',' -f4 | awk '!a[$0]++')
        for seq_len in ${seq_lens[@]}; do
            full_cores=$(cat ${summary} | grep "${mode},${model},${seq_len}," | cut -d',' -f5 | awk '!a[$0]++')
            for full_core in ${full_cores[@]}; do
                core_per_inss=$(cat ${summary} | grep "${mode},${model},${seq_len},${full_core}," | cut -d',' -f6 | awk '!a[$0]++')
                for core_per_ins in ${core_per_inss[@]}; do
                    bss=$(cat ${summary} | grep "${mode},${model},${seq_len},${full_core},${core_per_ins}," | cut -d',' -f7 | awk '!a[$0]++')
                    for bs in ${bss[@]}; do
                        benchmark_pattern="${mode},${model},${seq_len},${full_core},${core_per_ins},${bs}"
                        benchmark_int8=$(cat ${summary} | grep "${benchmark_pattern},int8" | cut -d',' -f9)
                        benchmark_int8_url=$(cat ${summary} | grep "${benchmark_pattern}," | tail -1 | cut -d',' -f10)
                        benchmark_fp32=$(cat ${summary} | grep "${benchmark_pattern},fp32" | cut -d',' -f9)
                        benchmark_fp32_url=$(cat ${summary} | grep "${benchmark_pattern},fp32" | cut -d',' -f10)
                        benchmark_bf16=$(cat ${summary} | grep "${benchmark_pattern},bf16" | cut -d',' -f9)
                        benchmark_bf16_url=$(cat ${summary} | grep "${benchmark_pattern},bf16" | cut -d',' -f10)
                        if [ $(cat ${summaryLast} | grep -c "${benchmark_pattern},int8") == 0 ]; then
                            benchmark_int8_last=nan
                            benchmark_int8_url_last=nan
                            benchmark_fp32_last=nan
                            benchmark_fp32_url_last=nan
                            benchmark_bf16_last=nan
                            benchmark_bf16_url_last=nan
                        else
                            benchmark_int8_last=$(cat ${summaryLast} | grep "${benchmark_pattern},int8" | cut -d',' -f9)
                            benchmark_int8_url_last=$(cat ${summaryLast} | grep "${benchmark_pattern},int8" | cut -d',' -f10)
                            benchmark_fp32_last=$(cat ${summaryLast} | grep "${benchmark_pattern},fp32" | cut -d',' -f9)
                            benchmark_fp32_url_last=$(cat ${summaryLast} | grep "${benchmark_pattern},fp32" | cut -d',' -f10)
                            benchmark_bf16_last=$(cat ${summaryLast} | grep "${benchmark_pattern},bf16" | cut -d',' -f9)
                            benchmark_bf16_url_last=$(cat ${summaryLast} | grep "${benchmark_pattern},bf16" | cut -d',' -f10)
                        fi
                        generate_perf_core
                    done
                done
            done
        done
    done
    cat >>${WORKSPACE}/report.html <<eof
        <tr>
            <td colspan="8"><font color="#d6776f">Note: </font>All data tested on INC Dedicated Server.</td>
            <td colspan="2" class="col-cell col-cell1 col-cellf"></td>
        </tr>
    </table>
eof
}

function generate_perf_core {
    echo "<tr><td rowspan=3>${model}</td><td rowspan=3>${seq_len}</td><td>New</td><td rowspan=2>${full_core}</td><td rowspan=2>${core_per_ins}</td><td rowspan=2>${bs}</td>" >>${WORKSPACE}/report.html

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

        function compare_new_last(a,b,required_a){
            if(a ~/[1-9]/ && b ~/[1-9]/) {
                target = a / b;
                if(target < 0.90) {
                    status_png = "background-color:#FFD2D2";
                    job_status = "fail"
                }else {
                    status_png = "background-color:#90EE90";
                }
                printf("<td style=\"%s\">%.2f</td>", status_png, target);
            }else{
                if(a == "" && required_a){
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
            compare_new_last(b_int8,b_int8_l,1)
            compare_new_last(b_fp32,b_fp32_l,0)
            compare_new_last(b_bf16,b_bf16_l,0)
            printf("</tr>\n");
        } END{
          printf("\n%s", job_status);
        }
    ' >>${WORKSPACE}/report.html
    job_state=$(tail -1 ${WORKSPACE}/report.html)
    sed -i '$s/.*//' ${WORKSPACE}/report.html
    if [ ${job_state} == 'fail' ]; then
        echo "performance regression" >>${WORKSPACE}/perf_regression.log
    fi
}

function generate_tuning_core {
    echo generate_tuning_core...
    local workflow=$1

    tuning_time=$(grep "^${os};${platform};${workflow};${framework};${fw_version};${model};" ${modelTuneLog} | awk -F';' '{print $7}')
    tuning_count=$(grep "^${os};${platform};${workflow};${framework};${fw_version};${model};" ${modelTuneLog} | awk -F';' '{print $8}')
    tuning_log=$(grep "^${os};${platform};${workflow};${framework};${fw_version};${model};" ${modelTuneLog} | awk -F';' '{print $9}')
    echo "<tr><td rowspan=3>${platform}</td><td rowspan=3>${os}</td><td rowspan=3>${framework}</td><td rowspan=3>${fw_version}</td><td rowspan=3>${model}</td><td>New</td>" >>${WORKSPACE}/report.html
    echo "<td><a href=${tuning_log}>${tuning_time}</a></td><td><a href=${tuning_log}>${tuning_count}</a></td>" >>${WORKSPACE}/report.html

    tuning_time=$(grep "^${os};${platform};${workflow};${framework};${fw_version};${model};" ${modelTuneLogLast} | awk -F';' '{print $7}')
    tuning_count=$(grep "^${os};${platform};${workflow};${framework};${fw_version};${model};" ${modelTuneLogLast} | awk -F';' '{print $8}')
    tuning_log=$(grep "^${os};${platform};${workflow};${framework};${fw_version};${model};" ${modelTuneLogLast} | awk -F';' '{print $9}')

    echo | awk -F ';' -v current_values="${current_values}" -v last_values="${last_values}" \
        -v tuning_time="${tuning_time}" \
        -v tuning_count="${tuning_count}" \
        -v tuning_log="${tuning_log}" \
        -v workflow="${workflow}" '

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
                    if(target >= 0.90) {
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
    ' >>${WORKSPACE}/report.html
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

main
