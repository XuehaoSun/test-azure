#!/bin/bash

# WORKSPACE=.
# modelExportSummaryLog=summary_test.log
# tuneLog=tuning_info.log
# tuneLogLast=tuning_info.log
# overview_log=summary_overview.log
# coverage_summary=coverage_summary.log
# nc_code_lines_summary=nc_code_lines_summary.csv
# engine_code_lines_summary=engine_code_lines_summary.csv

lines_coverage_threshold=80
branches_coverage_threshold=75

pass_status="<td style=\"background-color:#90EE90\">Pass</td>"
fail_status="<td style=\"background-color:#FFD2D2\">Fail</td>"
verify_status="<td style=\"background-color:#f2ea0a\">Verify</td>"

function main {
    echo "summaryLog: ${modelExportSummaryLog}"
    echo "tunelog: ${tuneLog}"
    echo "last tunelog: ${tuneLogLast}"
    echo "overview log: ${overview_log}"
    echo "coverage_summary: ${coverage_summary}"
    echo "export_tests_summary: ${export_tests_summary}"
    echo "inc_branch: ${inc_branch}",
    echo "lpot_commit: ${lpot_commit}",
    generate_html_head
    generate_html_body
    generate_results
    generate_html_footer
}

function generate_model_export_inference {
    awk -v framework="${framework}" -v model="${model}" -v os="${os}" -v platform=${platform} -v data_source=${data_source} -F ';' '
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
        }{
            if($1 == os && $2 == platform && $3 == framework && $6 == model && $7 == data_source) {
                // FP32
                if($5 == "FP32") {
                    // Performance
                    if($8 == "Performance") {
                        fp32_perf_bs = $9;
                        fp32_perf_value = $10;
                        fp32_perf_url = $11;
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
                    // Performance
                    if($8 == "Performance") {
                        int8_perf_bs = $9;
                        int8_perf_value = $10;
                        int8_perf_url = $11;
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
            printf("%s;%s;%s;%s;", int8_perf_bs,int8_perf_value,int8_acc_bs,int8_acc_value);
            printf("%s;%s;%s;%s;", fp32_perf_bs,fp32_perf_value,fp32_acc_bs,fp32_acc_value);
            printf("%s;%s;%s;%s;", int8_perf_url,int8_acc_url,fp32_perf_url,fp32_acc_url);
        }
    ' "$1"
}

function generate_html_core {
    echo "<tr><td rowspan=3>${platform}</td><td rowspan=3>${os}</td><td rowspan=3>${framework}</td><td rowspan=3>${model}</td><td>INT8</td>" >> ${WORKSPACE}/report.html
    echo | awk -F ';' -v target_values="${target_values}" -v source_values="${source_values}" \ '
        function abs(x) { return x < 0 ? -x : x }

        function show_value(batch, link, value, metric) {
            if (value ~/[1-9]/) {
                if (metric == "perf") {
                    printf("<td>%s</td> <td><a href=%s>%.2f</a></td>\n",batch,link,value);
                } else {
                    if (value <= 1){
                        printf("<td>%s</td> <td><a href=%s>%.2f%%</a></td>\n",batch,link,value*100);
                    } else {
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

        function compare_int8_result(target_result, source_result, metric) {

            if (target_result ~/[1-9]/ && source_result ~/[1-9]/) {
                if(metric == "acc") {
                    target = (target_result - source_result) / source_result;
                    if(target < -0.01) {
                       printf("<td style=\"background-color:#FFD2D2\">%.2f%%</td>", target*100);
                    }else{
                       printf("<td>%.2f%%</td>", target*100);
                    }
                }else if(metric == "perf") {
                    target = target_result / source_result;
                    if(target <= 0.5) {
                       printf("<td style=\"background-color:#FFD2D2\">%.2f</td>", target);
                    }else{
                       printf("<td>%.2f</td>", target);
                    }
                }
            }else {
                printf("<td></td>");
            }
        }

        function compare_result(int8_result, fp32_result, metric) {
            if (int8_result ~/[1-9]/ && fp32_result ~/[1-9]/) {
                if (metric == "acc") {
                    target = int8_result - fp32_result;
                    if(target >= -0.01) {
                        status_png = "background-color:#90EE90";
                    }else {
                        status_png = "background-color:#FFD2D2";
                    }
                    if (int8_result <= 1){
                        printf("<td style=\"%s\" colspan=2>%.2f%%</td>", status_png, target*100);
                    }else{
                        printf("<td style=\"%s\" colspan=2>%.2f</td>", status_png, target);
                    }

                } else {
                    target = int8_result / fp32_result;
                    if(target >= 0.95) {
                        status_png = "background-color:#90EE90";
                    }else {
                        status_png = "background-color:#FFD2D2";
                    }
                    printf("<td style=\"%s\" colspan=2>%.2f</td>", status_png, target);
                }
            }else {
                if(int8_result == "nan" || fp32_result == "nan") {
                    printf("<td class=\"col-cell col-cell3\" colspan=2></td>");
                }else {
                    printf("<td style=\"col-cell col-cell3\" colspan=2></td>");
                    job_red++;
                }
            }
        }

        function compare_ratio(target_fp32, source_fp32, target_int8, source_int8, mode) {
            if (target_fp32 ~/[1-9]/ && source_fp32 ~/[1-9]/ && target_int8 ~/[1-9]/ && source_int8 ~/[1-9]/ && mode=="perf") {
                fp32_ratio_result = target_fp32 / source_fp32
                int8_ratio_result = target_int8 / source_int8
                target = fp32_ratio_result / int8_ratio_result;
                gap = 1
                if (target <= 1+gap) {
                    status_png = "background-color:#90EE90";
                } else {
                    status_png = "background-color:#FFD2D2";
                }
                printf("<td style=\"%s\">%.2f</td>", status_png, target);
            } else {
                if (fp32_ratio_result == nan && int8_ratio_result == nan) {
                    printf("<td class=\"col-cell col-cell3\"></td>");
                } else {
                    if (fp32_ratio_result == nan) {
                        status_png = "background-color:#FFD2D2";
                        printf("<td style=\"%s\"></td>", status_png);
                    } else {
                        printf("<td class=\"col-cell col-cell3\"></td>");
                    }
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
            split(target_values,target_value,";");

            // Last values
            split(source_values,source_value,";");

            // Show Source INT8 Performance results
            source_int8_perf_batch=source_value[1]
            source_int8_perf_value=source_value[2]
            source_int8_perf_url=source_value[9]
            show_value(source_int8_perf_batch, source_int8_perf_url, source_int8_perf_value, "perf");

            // Show Source INT8 Accuracy results
            source_int8_acc_batch=source_value[3]
            source_int8_acc_value=source_value[4]
            source_int8_acc_url=source_value[10]
            show_value(source_int8_acc_batch, source_int8_acc_url, source_int8_acc_value, "acc");

            // INT8 Target Performance results
            target_int8_perf_batch=target_value[1];
            target_int8_perf_value=target_value[2];
            target_int8_perf_url=target_value[9];
            show_value(target_int8_perf_batch, target_int8_perf_url, target_int8_perf_value, "perf");

            // INT8 Target Accuracy results
            target_int8_acc_batch=target_value[3]
            target_int8_acc_value=target_value[4]
            target_int8_acc_url=target_value[10]
            show_value(target_int8_acc_batch, target_int8_acc_url, target_int8_acc_value, "acc");

            // Compare Current
            compare_int8_result(target_int8_perf_value, source_int8_perf_value, "perf");
            compare_int8_result(target_int8_acc_value, source_int8_acc_value, "acc");

            printf("</tr>\n<tr><td>FP32</td>");

            // Show Source FP32 Performance results
            source_fp32_perf_batch=source_value[5]
            source_fp32_perf_value=source_value[6]
            source_fp32_perf_url=source_value[11]
            show_value(source_fp32_perf_batch, source_fp32_perf_url, source_fp32_perf_value, "perf");

            // Show Source FP32 Accuracy results
            source_fp32_acc_batch=source_value[7]
            source_fp32_acc_value=source_value[8]
            source_fp32_acc_url=source_value[12]
            show_value(source_fp32_acc_batch, source_fp32_acc_url, source_fp32_acc_value, "acc");


            // FP32 Target Performance results
            target_fp32_perf_batch=target_value[5]
            target_fp32_perf_value=target_value[6]
            target_fp32_perf_url=target_value[11]
            show_value(target_fp32_perf_batch, target_fp32_perf_url, target_fp32_perf_value, "perf");

            // FP32 Target Accuracy results
            target_fp32_acc_batch=target_value[7]
            target_fp32_acc_value=target_value[8]
            target_fp32_acc_url=target_value[12]
            show_value(target_fp32_acc_batch, target_fp32_acc_url, target_fp32_acc_value, "acc");

            compare_int8_result(target_fp32_perf_value, source_fp32_perf_value, "perf");
            compare_int8_result(target_fp32_acc_value, source_fp32_acc_value, "acc");

            printf("</tr>")
            // INT8 vs FP32
            printf("</tr>\n<tr><td>INT8/FP32</td>");

            // Compare Source Performance results
            compare_result(source_int8_perf_value, source_fp32_perf_value,"perf");
            
            // Compare Source Accuracy results
            compare_result(source_int8_acc_value, source_fp32_acc_value, "acc");

            // Compare Target Performance results
            compare_result(target_int8_perf_value, target_fp32_perf_value, "perf");

            // Compare Target Performance results
            compare_result(target_int8_acc_value, target_fp32_acc_value, "acc");

            printf("<td style=\"%s\"></td>", "background-color:white");
            printf("<td style=\"%s\"></td>", "background-color:white");

            printf("</tr>\n");
        }
    ' >>${WORKSPACE}/report.html
}

function generate_results {
    #OS;Platform;Framework;Version;Precision;Model;Type;BS;Value
    oses=$(sed '1d' ${modelExportSummaryLog} | cut -d';' -f1 | awk '!a[$0]++')
    for os in ${oses[@]}; do
        platforms=$(sed '1d' ${modelExportSummaryLog} | grep "^${os}" | cut -d';' -f2 | awk '!a[$0]++')
        for platform in ${platforms[@]}; do
            frameworks=$(sed '1d' ${modelExportSummaryLog} | grep "^${os};${platform}" | cut -d';' -f3 | awk '!a[$0]++')
            for framework in ${frameworks[@]}; do
                models=$(sed '1d' ${modelExportSummaryLog} | grep "^${os};${platform};${framework}" | cut -d';' -f6 | awk '!a[$0]++')
                for model in ${models[@]}; do
                    data_source="Target"
                    target_values=$(generate_model_export_inference ${modelExportSummaryLog})
                    data_source="Source"
                    source_values=$(generate_model_export_inference ${modelExportSummaryLog})
                    generate_html_core
                done
            done
        done
    done
}

function generate_html_body {
    MR_TITLE=''
    Test_Info_Title=''
    Test_Info=''

    Test_Info_Title="<th colspan="4">Test Branch</th> <th colspan="4">Commit ID</th> "
    Test_Info="<th colspan="4">${inc_branch}</th> <th colspan="4">${lpot_commit}</th> "

    cat >>${WORKSPACE}/report.html <<eof

<body>
    <div id="main">
        <h1 align="center">Neural Compressor Model Export Tests ${MR_TITLE}
            [ <a href="${RUN_DISPLAY_URL}">Job-${BUILD_NUMBER}</a> ]</h1>

        <h2>Summary</h2>
        <table class="features-table">
            <tr>
                <th>Python</th>
                <th>Repo</th>
                ${Test_Info_Title}
            </tr>
            <tr>
                <td>${python_version}</td>
                <td><a href="https://github.com/intel/neural-compressor">neural-compressor</a></td>
                ${Test_Info}
            </tr>
        </table>
eof

    echo "Generating benchmarks title"
    cat >>${WORKSPACE}/report.html <<eof
        <h2>Benchmark</h2>
        <table class="features-table">
            <tr>
                <th rowspan="2">Platform</th>
                <th rowspan="2">System</th>
                <th rowspan="2">Framework</th>
                <th rowspan="2">Model</th>
                <th rowspan="2">VS</th>
                <th colspan="4">Source</th>
                <th colspan="4">Target</th>
                <th colspan="2" class="col-cell col-cell1 col-cellh">Export Ratio</th>
            </tr>
            <tr>
                <th>bs</th>
                <th>imgs/s</th>
                <th>bs</th>
                <th>acc</th>
                <th>bs</th>
                <th>imgs/s</th>
                <th>bs</th>
                <th>acc</th>
                <th class="col-cell col-cell1">Throughput<br>
                    <font size="2px"></font>
                </th>
                <th class="col-cell col-cell1">Accuracy<br>
                    <font size="2px"></font>
                </th>
            </tr>
eof
}

function generate_html_footer {

    cat >>${WORKSPACE}/report.html <<eof
            <tr>
                <td colspan="22"><font color="#d6776f">Note: </font>All data tested on INC Dedicated Server.</td>
            </tr>
        </table>
    </div>
</body>
</html>
eof
}

function generate_html_head {

    cat >${WORKSPACE}/report.html <<eof

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>INC Model Export Test Report - Jenkins</title>
    <style type="text/css">
        body {
            margin: 0;
            padding: 0;
            background: white no-repeat left top;
            min-width: 100%;
        }

        #main {
            margin: 20px auto 10px auto;
            background: white;
            border-radius: 8px;
            -moz-border-radius: 8px;
            -webkit-border-radius: 8px;
            padding: 0 30px 30px 30px;
            border: 1px solid #adaa9f;
            box-shadow: 0 2px 2px #9c9c9c;
            -moz-box-shadow: 0 2px 2px #9c9c9c;
            -webkit-box-shadow: 0 2px 2px #9c9c9c;

        }

        .features-table {
            width: 100%;
            margin: 0 auto;
            border-collapse: separate;
            border-spacing: 0;
            text-shadow: 0 1px 0 #fff;
            color: #2a2a2a;
            background: #fafafa;
            background-image: -moz-linear-gradient(top, #fff, #eaeaea, #fff);
            /* Firefox 3.6 */
            background-image: -webkit-gradient(linear, center bottom, center top, from(#fff), color-stop(0.5, #eaeaea), to(#fff));
            font-family: Verdana, Arial, Helvetica
        }

        .features-table th,
        td {
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

        .no-border th {
            box-shadow: none;
            -moz-box-shadow: none;
            -webkit-box-shadow: none;
        }

        .col-cell {
            text-align: center;
            width: 150px;
            font: normal 1em Verdana, Arial, Helvetica;
        }

        .col-cell3 {
            background: #efefef;
            background: rgba(144, 144, 144, 0.15);
        }

        .col-cell1,
        .col-cell2 {
            background: #B0C4DE;
            background: rgba(176, 196, 222, 0.3);
        }

        .col-cellh {
            font: bold 1.3em 'trebuchet MS', 'Lucida Sans', Arial;
            -moz-border-radius-topright: 10px;
            -moz-border-radius-topleft: 10px;
            border-top-right-radius: 10px;
            border-top-left-radius: 10px;
            border-top: 1px solid #eaeaea !important;
        }

        .col-cellf {
            font: bold 1.4em Georgia;
            -moz-border-radius-bottomright: 10px;
            -moz-border-radius-bottomleft: 10px;
            border-bottom-right-radius: 10px;
            border-bottom-left-radius: 10px;
            border-bottom: 1px solid #dadada !important;
        }

        .bench-features-table {
            /* display: flex; */
            width: 100%;
            margin: 0 auto;
            border-collapse: separate;
            border-spacing: 0;
            text-shadow: 0 1px 0 #fff;
            color: #2a2a2a;
            background: #fafafa;
            background-image: -moz-linear-gradient(top, #fff, #eaeaea, #fff);
            /* Firefox 3.6 */
            background-image: -webkit-gradient(linear, center bottom, center top, from(#fff), color-stop(0.5, #eaeaea), to(#fff));
            font-family: Verdana, Arial, Helvetica;
            min-width: 100%;
        }

        .bench-features-table th,
        td {
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
    </style>
</head>

eof

}

main
