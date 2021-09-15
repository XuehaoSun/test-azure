#!/bin/bash
set -x

function main {
    echo "summaryLog: ${summaryLog}"
    echo "summaryLogLast: ${summaryLogLast}"
    echo "overviewLog: ${overviewLog}"
    echo "Jenkins_job_status: ${Jenkins_job_status}"

    generate_html_head
    generate_html_overview
    generate_accuracy_results
    generate_benchmark_results
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

    benchmark=($(grep 'deep-engine_benchmark' ${overviewLog} |sed 's/,/ /g'))
    if [[ "${benchmark[1]}" == *"FAIL"* ]];then
        benchmark_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${benchmark[1]}" == *"SUCC"* ]];then
        benchmark_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        benchmark_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
    fi

    accuracy=($(grep 'deep-engine_accuracy' ${overviewLog} |sed 's/,/ /g'))
    if [[ "${accuracy[1]}" == *"FAIL"* ]];then
        accuracy_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
    elif [[ "${accuracy[1]}" == *"SUCC"* ]];then
        accuracy_status="<td style=\"background-color:#90EE90\">Pass</td>"
    else
        accuracy_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
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
        accuracy_status="<td style=\"background-color:#f2ea0a\">Verify</td>"
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

             if [ "${benchmark[2]}" != "" ]; then
                 echo "<tr><td>Benchmark</td>"
                 echo "<td style=\"text-align:left\"><a href=\"${benchmark[2]}\">benchmark_summary</a></td>"
                 echo "${benchmark_status}</tr>"
             fi

             if [ "${accuracy[2]}" != "" ]; then
                 echo "<tr><td>Accuracy</td>"
                 echo "<td style=\"text-align:left\"><a href=\"${accuracy[2]}\">accuracy_summary</a></td>"
                 echo "${accuracy_status}</tr>"
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
Test_Info="<th colspan="4">${deepengine_branch}</th> <th colspan="4">${deepengine_commit}</th> "

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

}

function generate_accuracy_results {
cat >> ${WORKSPACE}/report.html << eof
    <h2>Accuracy</h2>
      <table class="features-table">
        <tr>
          <th rowspan="2">Model</th>
          <th rowspan="2">VS</th>
          <th>INT8</th>
          <th>FP32</th>
          <th colspan="2" class="col-cell col-cell1 col-cellh">Ratio</th>
        </tr>
        <tr>
          <th>accuracy</th>
          <th>accuracy</th>
          <th colspan="2" class="col-cell col-cell1"><font size="2px">(INT8-FP32)/FP32</font></th>
        </tr>
eof

    mode='accuracy'
    models=$(cat ${summaryLog} |grep "^${mode}" |cut -d',' -f2 |awk '!a[$0]++')
    for model in ${models[@]}
    do
        accuracy_int8=$(cat ${summaryLog} |grep "^${mode},${model},int8" | cut -d',' -f4)
        accuracy_int8_url=$(cat ${summaryLog} |grep "^${mode},${model},int8" | cut -d',' -f5)
        accuracy_fp32=$(cat ${summaryLog} |grep "^${mode},${model},fp32" | cut -d',' -f4)
        accuracy_fp32_url=$(cat ${summaryLog} |grep "^${mode},${model},fp32" | cut -d',' -f5)
        if [ $(cat ${summaryLogLast} |grep -c "^${mode},${model},int8") == 0 ]; then
            accuracy_int8_last=nan
            accuracy_int8_url_last=nan
            accuracy_fp32_last=nan
            accuracy_fp32_url_last=nan
        else
            accuracy_int8_last=$(cat ${summaryLogLast} |grep "^${mode},${model},int8" | cut -d',' -f4)
            accuracy_int8_url_last=$(cat ${summaryLogLast} |grep "^${mode},${model},int8" | cut -d',' -f5)
            accuracy_fp32_last=$(cat ${summaryLogLast} |grep "^${mode},${model},fp32" | cut -d',' -f4)
            accuracy_fp32_url_last=$(cat ${summaryLogLast} |grep "^${mode},${model},fp32" | cut -d',' -f5)
        fi
        generate_acc_core
    done

    cat >> ${WORKSPACE}/report.html << eof
        <tr>
            <td colspan="4"><font color="#d6776f">Note: </font>All data tested on TensorFlow Dedicated Server.</td>
            <td colspan="2" class="col-cell col-cell1 col-cellf"></td>
        </tr>
    </table>
eof
}

function generate_acc_core {
    echo "<tr><td rowspan=3>${model}</td><td>New</td>" >> ${WORKSPACE}/report.html
    echo | awk -v a_int8=${accuracy_int8} -v a_int8_url=${accuracy_int8_url} -v a_fp32=${accuracy_fp32} -v a_fp32_url=${accuracy_fp32_url} -v a_int8_l=${accuracy_int8_last} -v a_int8_url_l=${accuracy_int8_url_last} -v a_fp32_l=${accuracy_fp32_last} -v a_fp32_url_l=${accuracy_fp32_url_last} '
        function show_accuracy(a,b) {
            if(a ~/[1-9]/) {
                if (a <= 1){
                    printf("<td><a href=%s>%.2f %</a></td>\n",b,a*100);
                }else{
                    printf("<td><a href=%s>%.2f</a></td>\n",b,a);
                }
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
                target = (a - b) / b;
                if(target >= -0.01) {
                   printf("<td rowspan=3 style=\"background-color:#90EE90\">%.2f %</td>", target*100);
                }else if(target < -0.05) {
                   printf("<td rowspan=3 style=\"background-color:#FFD2D2\">%.2f %</td>", target*100);
                   job_status = "fail"
                }else{
                   printf("<td rowspan=3>%.2f %</td>", target*100);
                }
            }else{
                printf("<td rowspan=3></td>");
            }
        }

        function compare_new_last(a,b) {
            if(a ~/[1-9]/ && b ~/[1-9]/) {
                target = a - b;
                if(target > -0.00001 && target < 0.00001) {
                    status_png = "background-color:#90EE90";
                }else {
                    status_png = "background-color:#FFD2D2";
                    job_status = "fail"
                }
                if (a <= 1){
                    printf("<td style=\"%s\">%.2f%</td>", status_png, target*100);
                }else{
                    printf("<td style=\"%s\">%.2f</td>", status_png, target);
                }
            }else {
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
            show_accuracy(a_int8, a_int8_url)
            show_accuracy(a_fp32, a_fp32_url)

            compare_current(a_int8, a_fp32)

            printf("</tr>\n<tr><td>Last</td>")
            show_accuracy(a_int8_l, a_int8_url_l)
            show_accuracy(a_fp32_l, a_fp32_url_l)

            printf("</tr>\n<tr><td>New/Last</td>");
            compare_new_last(a_int8,a_int8_l)
            compare_new_last(a_fp32,a_fp32_l)
            printf("</tr>\n");
        } END{
            printf("\n%s", job_status);
        }
    ' >> ${WORKSPACE}/report.html
    job_state=$(tail -1 ${WORKSPACE}/report.html)
    sed -i '$s/.*//' ${WORKSPACE}/report.html
    if [ ${job_state} == 'fail' ]; then
      echo "accuracy regression" >> ${WORKSPACE}/perf_regression.log
    fi
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
    models=$(cat ${summaryLog} |grep "^${mode}," |cut -d',' -f2 |awk '!a[$0]++')
    for model in ${models[@]}
    do
        seq_lens=$(cat ${summaryLog} |grep "^${mode},${model}," |cut -d',' -f3 |awk '!a[$0]++')
        for seq_len in ${seq_lens[@]}
        do
            full_cores=$(cat ${summaryLog} |grep "^${mode},${model},${seq_len}," |cut -d',' -f4 |awk '!a[$0]++')
            for full_core in ${full_cores[@]}
            do
                core_per_inss=$(cat ${summaryLog} |grep "^${mode},${model},${seq_len},${full_core}," |cut -d',' -f5 |awk '!a[$0]++')
                for core_per_ins in ${core_per_inss[@]}
                do
                    bss=$(cat ${summaryLog} |grep "^${mode},${model},${seq_len},${full_core},${core_per_ins}," |cut -d',' -f6 |awk '!a[$0]++')
                    for bs in ${bss[@]}
                    do
                        benchmark_pattern="^${mode},${model},${seq_len},${full_core},${core_per_ins},${bs}"
                        benchmark_int8=$(cat ${summaryLog} |grep "${benchmark_pattern},int8" |cut -d',' -f8)
                        benchmark_int8_url=$(cat ${summaryLog} |grep "${benchmark_pattern},int8" |cut -d',' -f9)
                        benchmark_fp32=$(cat ${summaryLog} |grep "${benchmark_pattern},fp32" |cut -d',' -f8)
                        benchmark_fp32_url=$(cat ${summaryLog} |grep "${benchmark_pattern},fp32" |cut -d',' -f9)
                        if [ $(cat ${summaryLogLast} |grep -c "${benchmark_pattern},int8") == 0 ]; then
                            benchmark_int8_last=nan
                            benchmark_int8_url_last=nan
                            benchmark_fp32_last=nan
                            benchmark_fp32_url_last=nan
                        else
                            benchmark_int8_last=$(cat ${summaryLogLast} |grep "${benchmark_pattern},int8" |cut -d',' -f8)
                            benchmark_int8_url_last=$(cat ${summaryLogLast} |grep "${benchmark_pattern},int8" |cut -d',' -f9)
                            benchmark_fp32_last=$(cat ${summaryLogLast} |grep "${benchmark_pattern},fp32" |cut -d',' -f8)
                            benchmark_fp32_url_last=$(cat ${summaryLogLast} |grep "${benchmark_pattern},fp32" |cut -d',' -f9)
                        fi
                        generate_html_core
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

function generate_html_core {
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