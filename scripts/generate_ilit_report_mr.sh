#!/bin/bash

# WORKSPACE=.
# summaryLog=summary.log
# summaryLogLast=summary.log
# tuneLog=tuning_info.log
# tuneLogLast=tuning_info.log
# overview_log=summary_overview.log

function main {
    echo "summaryLog: ${summaryLog}"
    echo "last summaryLog: ${summaryLogLast}"
    echo "tunelog: ${tuneLog}"
    echo "last tunelog: ${tuneLogLast}"
    echo "overview_log: ${overview_log}"
    echo "Jenkins_job_status: ${Jenkins_job_status}"
    generate_html_head
    generate_html_body
    generate_results
    generate_html_footer

}

function createOverview {

    jenkins_job_url="https://inteltf-jenk.sh.intel.com/job/"
    png_path="http://mlpc.intel.com/static/doc/tensorflow/images/24x24"

    # unit test
    unit_test=($(grep 'unit-test' ${overview_log} |sed 's/,/ /g'))
    if [[ "${unit_test[1]}" == *"FAIL"* ]];then
        unit_test_status="<img src=${png_path}/red.png></img>"
    elif [[ "${unit_test[1]}" == *"SUCC"* ]];then
        unit_test_status="<img src=${png_path}/blue.png></img>"
    else
        unit_test_status="<img src=${png_path}/yellow.png></img>"
    fi

    pylint_scan=($(grep 'format-scan,pylint' ${overview_log} |sed 's/,/ /g'))
    if [[ "${pylint_scan[2]}" == *"FAIL"* ]];then
        pylint_scan_status="<img src=${png_path}/red.png></img>"
    elif [[ "${pylint_scan[2]}" == *"SUCC"* ]];then
        pylint_scan_status="<img src=${png_path}/blue.png></img>"
    else
        pylint_scan_status="<img src=${png_path}/yellow.png></img>"
    fi

    bandit_scan=($(grep 'format-scan,bandit' ${overview_log} |sed 's/,/ /g'))
    if [[ "${bandit_scan[2]}" == *"FAIL"* ]];then
        bandit_scan_status="<img src=${png_path}/red.png></img>"
    elif [[ "${bandit_scan[2]}" == *"SUCC"* ]];then
        bandit_scan_status="<img src=${png_path}/blue.png></img>"
    else
        bandit_scan_status="<img src=${png_path}/yellow.png></img>"
    fi

    cat >> ${WORKSPACE}/report.html <<  eof

    <h2>Overview</h2>
    <table class="features-table" style="width: 60%;margin: 0 auto 0 0;">
        <tr>
            <th>Task</th>
            <th>Job</th>
            <th>Status</th>
        </tr>
        $(
             if [ "${unit_test[2]}" != "" ];then
                 echo "<tr><td>Unit Test</td>"
                 echo "<td style=\"text-align:left\"><a href=\"${jenkins_job_url}${unit_test[0]}/${unit_test[2]}\">${unit_test[0]}#${unit_test[2]}</a></td>"
                 echo "<td>${unit_test_status}</td></tr>"
             fi

             if [ "${pylint_scan[3]}" != "" ]; then
                 echo "<tr><td>PyLint Scan</td>"
                 echo "<td style=\"text-align:left\"><a href=\"${jenkins_job_url}${pylint_scan[0]}/${pylint_scan[3]}\">${pylint_scan[0]}#${pylint_scan[3]}</a></td>"
                 echo "<td>${pylint_scan_status}</td></tr>"
             fi

             if [ "${bandit_scan[3]}" != "" ]; then
                 echo "<tr><td>Bandit Scan</td>"
                 echo "<td style=\"text-align:left\"><a href=\"${jenkins_job_url}${bandit_scan[0]}/${bandit_scan[3]}\">${bandit_scan[0]}#${bandit_scan[3]}</a></td>"
                 echo "<td>${bandit_scan_status}</td></tr>"
             fi
        )
    </table>
eof
}

function generate_inference {

    awk -v framework="${framework}" -v model="${model}" -F ';' '
        BEGINE {
            fp32_ms_bs = nan;
            fp32_ms_value = nan;
            fp32_ms_url = nan;
            fp32_fps_bs = nan;
            fp32_fps_value = nan;
            fp32_fps_url = nan;
            fp32_acc_bs = nan;
            fp32_acc_value = nan;
            fp32_acc_url = nan;

            int8_ms_bs = nan;
            int8_ms_value = nan;
            int8_ms_url = nan;
            int8_fps_bs = nan;
            int8_fps_value = nan;
            int8_fps_url = nan;
            int8_acc_bs = nan;
            int8_acc_value = nan;
            int8_acc_url = nan;
        }{
            if($1 == framework && $4 == model) {
                // FP32
                if($3 == "FP32") {
                    // Latency
                    if($6 == "Latency") {
                        if( $8 ~/[0-9]/) {
                            fp32_ms_bs = $7;
                            fp32_ms_value = $8;
                        }
                        fp32_ms_url = $9;
                    }
                    // Throughput
                    if($6 == "Throughput") {
                        if($8 ~/[0-9]/) {
                            fp32_fps_bs = $7;
                            fp32_fps_value = $8;
                        }
                        fp32_fps_url = $9;
                    }
                    // Accuracy
                    if($6 == "Accuracy") {
                        if($8 ~/[0-9]/) {
                            fp32_acc_bs = $7;
                            fp32_acc_value = $8;
                        }
                        fp32_acc_url = $9;
                    }
                }

                // INT8
                if($3 == "INT8") {
                    // Latency
                    if($6 == "Latency") {
                        if($8 ~/[0-9]/) {
                            int8_ms_bs = $7;
                            int8_ms_value = $8;
                        }
                        int8_ms_url = $9;
                    }
                    // Throughput
                    if($6 == "Throughput") {
                        if($8 ~/[0-9]/) {
                            int8_fps_bs = $7;
                            int8_fps_value = $8;
                        }
                        int8_fps_url = $9;
                    }
                    // Accuracy
                    if($6 == "Accuracy") {
                        if($8 ~/[0-9]/) {
                            int8_acc_bs = $7;
                            int8_acc_value = $8;
                        }
                        int8_acc_url = $9;
                    }
                }
            }
        }END {
            printf("%s;%.5f;%s;%.5f;%s;%s;", int8_ms_bs,int8_ms_value,int8_fps_bs,int8_fps_value,int8_acc_bs,int8_acc_value);
            printf("%s;%.5f;%s;%.5f;%s;%s;", fp32_ms_bs,fp32_ms_value,fp32_fps_bs,fp32_fps_value,fp32_acc_bs,fp32_acc_value);
            printf("%s;%s;%s;%s;%s;%s", int8_ms_url,int8_fps_url,int8_acc_url,fp32_ms_url,fp32_fps_url,fp32_acc_url);
        }
    ' $1
}

function generate_html_core {

    tuning_strategy=$(grep "^${framework};${model};" ${tuneLog} |awk -F';' '{print $3}')
    tuning_time=$(grep "^${framework};${model};" ${tuneLog} |awk -F';' '{print $4}')
    echo "<tr><td rowspan=3>${framework}</td><td rowspan=3>${model}</td><td>New</td><td>${tuning_strategy}</td><td>${tuning_time}</td>" >> ${WORKSPACE}/report.html

    tuning_strategy=$(grep "^${framework};${model};" ${tuneLogLast} |awk -F';' '{print $3}')
    tuning_time=$(grep "^${framework};${model};" ${tuneLogLast} |awk -F';' '{print $4}')

    echo |awk -v current_values=${current_values} -v last_values=${last_values} -v ts=${tuning_strategy} -v tt=${tuning_time} -F ';' '

        function abs(x) { return x < 0 ? -x : x }

        function show_new_last(a,b,c,d) {
            if(c ~/[1-9]/) {
                if (d == "ms") {
                    printf("<td>%s</td> <td><a href=%s>%.2f</a></td>\n",a,b,c);
                }else if(d == "fps") {
                    printf("<td>%s</td> <td><a href=%s>%.2f</a></td>\n",a,b,c);
                }else {
                    printf("<td>%s</td> <td><a href=%s>%.4f</a></td>\n",a,b,c);
                }
            }else {
                if(b == "" || c == "N/A") {
                    printf("<td></td> <td></td>\n");
                }else
                {
                    printf("<td>%s</td> <td><a href=%s>Failure</a></td>\n",a,b);
                }
            }
        }

        function compare_current(a,b,c) {

            if(a ~/[1-9]/ && b ~/[1-9]/) {
                if(c == "acc") {
                    target = (a - b) / b;
                    if(target >= -0.01) {
                       printf("<td rowspan=3 style='background-color:#90EE90'>%.4f</td>", target);
                    }else if(target < -0.05) {
                       printf("<td rowspan=3 style='background-color:#FFD2D2'>%.4f</td>", target);
                       job_status = "fail"
                    }else{
                       printf("<td rowspan=3>%.4f</td>", target);
                    }
                }else if(c == "ms") {
                    target = a / b;
                    if(target >= 1.5) {
                       printf("<td rowspan=3 style='background-color:#90EE90'>%.4f</td>", target);
                    }else if(target < 1) {
                       printf("<td  rowspan=3 style='background-color:#FFD2D2'>%.4f</td>", target);
                       job_status = "fail"
                    }else{
                       printf("<td rowspan=3>%.4f</td>", target);
                    }
                }
                else {
                    target = a / b;
                    if(target >= 2) {
                       printf("<td rowspan=3 style='background-color:#90EE90'>%.4f</td>", target);
                    }else if(target < 1) {
                       printf("<td rowspan=3 style='background-color:#FFD2D2'>%.4f</td>", target);
                       job_status = "fail"
                    }else{
                       printf("<td rowspan=3>%.4f</td>", target);
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
                }else {
                    target = a / b;
                    if(target >= 0.95) {
                        status_png = "background-color:#90EE90";
                    }else {
                        status_png = "background-color:#FFD2D2";
                        job_status = "fail"
                    }
                }
                printf("<td style=\"%s\" colspan=2>%.4f</td>", status_png, target);
            }else {
              if(a == "nan" || b == "nan") {
                printf("<td class=\"col-cell col-cell3\" colspan=2></td>");
              }else {
                status_png = "background-color:#FFD2D2";
                printf("<td style=\"%s\" colspan=2></td>", status_png);
              }
            }
        }

        BEGIN {
            job_status = "pass"
            // issue list
            jira_mobilenet = "https://jira01.devtools.intel.com/browse/PADDLEQ-384";
            jira_resnext = "https://jira01.devtools.intel.com/browse/PADDLEQ-387";
            jira_ssdmobilenet = "https://jira01.devtools.intel.com/browse/PADDLEQ-406";
        }{
            // Current values
            split(current_values,current_value,";");

            // current
            show_new_last(current_value[1],current_value[13],current_value[2],"ms");
            show_new_last(current_value[5],current_value[15],current_value[6],"acc");

            show_new_last(current_value[7],current_value[16],current_value[8],"ms");
            show_new_last(current_value[11],current_value[18],current_value[12],"acc");

            // Compare Current

            compare_current(current_value[8],current_value[2],"ms");
            compare_current(current_value[6],current_value[12],"acc");

            // Last values
            split(last_values,last_value,";");

            // Last
            printf("</tr>\n<tr><td>Last</td><td>%s</td><td>%s</td>", ts, tt);

            show_new_last(last_value[1],last_value[13],last_value[2],"ms");
            show_new_last(last_value[5],last_value[15],last_value[6],"acc");

            show_new_last(last_value[7],last_value[16],last_value[8],"ms");
            show_new_last(last_value[11],last_value[18],last_value[12],"acc");
            printf("</tr>")

            // current vs last
            printf("</tr>\n<tr><td>New/Last</td><td colspan=2 class=\"col-cell3\"></td>");

            compare_result(last_value[2],current_value[2],"ms");
            compare_result(current_value[6],last_value[6],"acc");

            compare_result(last_value[8],current_value[8],"ms");
            compare_result(current_value[12],last_value[12],"acc");
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

function generate_results {

    frameworks=$(sed '1d' ${summaryLog} |cut -d';' -f1 | awk '!a[$0]++')

    for framework in ${frameworks[@]}
    do
        models=$(sed '1d' ${summaryLog} |grep "^${framework}" |cut -d';' -f4 | awk '!a[$0]++')
        for model in ${models[@]}
        do
            current_values=$(generate_inference ${summaryLog})
            last_values=$(generate_inference ${summaryLogLast})

            generate_html_core
        done
    done
}

function generate_html_body {
MR_TITLE=''
Test_Info_Title=''
Test_Info=''
if [ "${qtools_branch}" == "" ];
then
  commit_id=$(echo ${gitlabMergeRequestLastCommit} |awk '{print substr($1,1,7)}')

  MR_TITLE="[ <a href='${gitlabSourceRepoHomepage}/merge_requests/${gitlabMergeRequestIid}'>MR-${gitlabMergeRequestIid}</a> ]"
  Test_Info_Title="<th colspan="2">Source Branch</th> <th colspan="4">Target Branch</th> <th colspan="4">Commit</th> "
  Test_Info="<td colspan="2">${gitlabSourceBranch}</td> <td colspan="4">${gitlabTargetBranch}</td> <td colspan="4">${commit_id}"
else
  Test_Info_Title="<th colspan="4">Test Branch</th> <th colspan="4">Commit ID</th> "
  Test_Info="<th colspan="4">${qtools_branch}</th> <th colspan="4">${qtools_commit}</th> "
fi
cat >> ${WORKSPACE}/report.html << eof

<body>
    <div id="main">
	    <h1 align="center">ILit Tuning Tests ${MR_TITLE}
        [ <a href="${RUN_DISPLAY_URL}">Job-${BUILD_NUMBER}</a> ] ${Jenkins_job_status}</h1>

        <h2>Summary</h2>
	    <table class="features-table">
	        <tr>
              <th>Platform</th>
              <th>TensorFlow Version</th>
              <th>PyTorch Version</th>
              <th>MXNet Version</th>
              <th>Repo</th>
              ${Test_Info_Title}
		      </tr>
		      <tr>
			        <td>CLX8280</td>
                    <td>${tensorflow_version}</td>
                    <td>${pytorch_version}</td>
                    <td>${mxnet_version}</td>
			        <td><a href="https://gitlab.devtools.intel.com/intelai/LowPrecisionInferenceTool">ILIT</a></td>
              ${Test_Info}
			    </tr>
	    </table>
eof

createOverview

cat >> ${WORKSPACE}/report.html << eof
        <h2>Benchmark</h2>
		  <table class="features-table">
            <tr>
                <th rowspan="2">Framework</th>
                <th rowspan="2">Model</th>
                <th rowspan="2">VS</th>
                <th rowspan="2">Tuning Strategy</th>
                <th rowspan="2">Tuning time(s)</th>
			          <th colspan="4">INT8</th>
			          <th colspan="4">FP32</th>
			          <th colspan="2" class="col-cell col-cell1 col-cellh">Ratio</th>
		        </tr>
		        <tr>

                <th>bs</th>
                <th>ms</th>
                <th>bs</th>
                <th>top1</th>

                <th>bs</th>
                <th>ms</th>
                <th>bs</th>
                <th>top1</th>

                <th class="col-cell col-cell1">Latency<br><font size="2px">FP32/INT8>=1.5</font></th>
                <th class="col-cell col-cell1">Accuracy<br><font size="2px">(INT8-FP32)/FP32>=-0.01</font></th>
		        </tr>
eof
}

function generate_html_footer {

    cat >> ${WORKSPACE}/report.html << eof
		    <tr>
			    <td colspan="13"><font color="#d6776f">Note: </font>All data tested on TensorFlow Dedicated Server.</td>
			    <td colspan="3" class="col-cell col-cell1 col-cellf"></td>
		    </tr>
	    </table>
	</div>
</body>
</html>
eof
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

main
