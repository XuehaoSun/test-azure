#!/bin/bash

# WORKSPACE=.
# summaryLog=summary.log
# summaryLogLast=summary.log
# tuneLog=tuning_info.log
# tuneLogLast=tuning_info.log
# overview_log=summary_overview.log

PATTERN='[-a-zA-Z0-9_]*='
if [ $# != "2" ] ; then 
    echo 'ERROR:'
    echo "Expected 2 parameters got $#"
    printf 'Please use following parameters:
    --ref_dir=<path to reference build artifacts>
    --new_dir=<path to latest build artifacts>
    '
    exit 1
fi

for i in "$@"
do
    case $i in
        --ref_dir=*)
            REF_DIR=`echo $i | sed "s/${PATTERN}//"`;;
        --new_dir=*)
            NEW_DIR=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

function main {
    echo "summaryLog: ${summaryLog}"
    echo "last summaryLog: ${summaryLogLast}"
    echo "tunelog: ${tuneLog}"
    echo "last tunelog: ${tuneLogLast}"

    ref_build=($(cat ${REF_DIR}/build_info.txt |sed 's/,/ /g'))
    echo "Reference build: ${ref_build[0]} #${ref_build[1]}"

    new_build=($(cat ${NEW_DIR}/build_info.txt |sed 's/,/ /g'))
    echo "New build: ${new_build[0]} #${new_build[1]}"

    generate_html_head
    generate_html_body
    generate_results
    generate_html_footer

}

function generate_inference {
    awk -v framework="${framework}" -v model="${model}" -v os="${os}" -F ';' '
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
            if($1 == os && $3 == framework && $6 == model) {
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
    platform=$(grep "^${os};.*;${framework};.*;${model};" ${tuneLog} |awk -F';' '{print $2}')
    pb_size=$(grep "^${os};${platform};${framework};.*;${model};" ${tuneLog} |awk -F ';' '{printf("%s;%s;%s", $10,$11,$12)}')
    fw_version=$(grep "^${os};${platform};${framework};.*;${model};" ${tuneLog} |awk -F';' '{print $4}')
    tuning_strategy=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLog} |awk -F';' '{print $6}')
    tuning_time=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLog} |awk -F';' '{print $7}')
    tuning_count=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLog} |awk -F';' '{print $8}')
    tuning_log=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLog} |awk -F';' '{print $9}')
    echo "<tr><td>${platform}</td><td rowspan=3>${os}</td><td rowspan=3>${framework}</td><td>${fw_version}</td><td rowspan=3>${model}</td><td><a href=\"${JENKINS_URL}/job/${new_build[0]}/${new_build[1]}\">${new_build[0]} #${new_build[1]}</a></td><td><a href=${tuning_log}>${tuning_strategy}</a></td>" >> ${WORKSPACE}/report.html
    echo "<td><a href=${tuning_log}>${tuning_time}</a></td><td><a href=${tuning_log}>${tuning_count}</a></td>" >> ${WORKSPACE}/report.html

    ref_platform=$(grep "^${os};.*;${framework};.*;${model};" ${tuneLogLast} |awk -F';' '{print $2}')
    last_pb_size=$(grep "^${os};${ref_platform};${framework};.*;${model};" ${tuneLogLast} |awk -F ';' '{printf("%s;%s;%s", $10,$11,$12)}')
    ref_fw_ver=$(grep "^${os};${ref_platform};${framework};.*;${model};" ${tuneLogLast} |awk -F';' '{print $4}')
    tuning_strategy=$(grep "^${os};${ref_platform};${framework};${ref_fw_ver};${model};" ${tuneLogLast} |awk -F';' '{print $6}')
    tuning_time=$(grep "^${os};${ref_platform};${framework};${ref_fw_ver};${model};" ${tuneLogLast} |awk -F';' '{print $7}')
    tuning_count=$(grep "^${os};${ref_platform};${framework};${ref_fw_ver};${model};" ${tuneLogLast} |awk -F';' '{print $8}')
    tuning_log=$(grep "^${os};${ref_platform};${framework};${ref_fw_ver};${model};" ${tuneLogLast} |awk -F';' '{print $9}')


    echo |awk -F ';' -v current_values="${current_values}" -v last_values="${last_values}" \
              -v pb_size="${pb_size}" -v last_pb_size="${last_pb_size}" \
              -v tuning_strategy="${tuning_strategy}" -v tuning_time="${tuning_time}" \
              -v tuning_count="${tuning_count}" -v tuning_log="${tuning_log}" \
              -v ref_build_name="${ref_build[0]}" -v ref_build_number="${ref_build[1]}" \
              -v new_build_name="${new_build[0]}" -v new_build_number="${new_build[1]}" \
              -v ref_fw_ver="${ref_fw_ver}" \
              -v ref_platform="${ref_platform}" \
              -v jenkins_url="${JENKINS_URL}" '

        function abs(x) { return x < 0 ? -x : x }

        function show_new_last(batch, link, value, metric) {
            if (value ~/[1-9]/) {
                if (metric == "perf") {
                    printf("<td>%s</td> <td><a href=%s>%.2f</a></td>\n",batch,link,value);
                } else {
                    if (value <= 1){
                        printf("<td>%s</td> <td><a href=%s>%.2f%</a></td>\n",batch,link,value*100);
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

        function compare_current(int8_result, fp32_result, metric) {

            if (int8_result ~/[1-9]/ && fp32_result ~/[1-9]/) {
                if(metric == "acc") {
                    target = (int8_result - fp32_result) / fp32_result;
                    if(target >= -0.01) {
                       printf("<td rowspan=3 style=\"background-color:#90EE90\">%.2f%</td>", target*100);
                    }else if(target < -0.05) {
                       printf("<td rowspan=3 style=\"background-color:#FFD2D2\">%.2f%</td>", target*100);
                    }else{
                       printf("<td rowspan=3>%.2f%</td>", target*100);
                    }
                }else if(metric == "perf") {
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
                if (metric == "acc") {
                    target = new_result - previous_result;
                    if(target >= -0.0001 && target <= 0.0001) {
                        status_png = "background-color:#90EE90";
                    }else {
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
                    }else {
                        status_png = "background-color:#FFD2D2";
                    }
                    printf("<td style=\"%s\" colspan=2>%.2f</td>", status_png, target);
                }
            }else {
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

            // Compare Current
            compare_current(int8_perf_value, fp32_perf_value, "perf");
            compare_current(int8_acc_value, fp32_acc_value, "acc");

            // Last values
            split(last_values,last_value,";");

            // Last
            printf("</tr>\n<tr><td>%9$s</td><td>%8$s</td><td><a href=\"%1$s/job/%2$s/%3$s\">%2$s #%3$s</a></td><td><a href=%7$s>%4$s</a></td><td><a href=%7$s>%5$s</a></td><td><a href=%7$s>%6$s</a></td>", jenkins_url, ref_build_name, ref_build_number, tuning_strategy, tuning_time, tuning_count, tuning_log, ref_fw_ver, ref_platform);
            if(last_pb_size_[1] ~/[1-9]/ && last_pb_size_[2] ~/[1-9]/) {
                printf("<td>%.2fx</td>", last_pb_size_[1]/last_pb_size_[2]);
            }else {
                printf("<td>NaN</td>");
            }

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
            printf("</tr>")

            // current vs last
            printf("</tr>\n<tr><td>-</td><td>-</td><td>%s... #%s/%s... #%s</td><td colspan=4>Mem Peak:%s</td>",
                substr(new_build_name,0,5),
                substr(new_build_number,0,5),
                substr(ref_build_name,0,5),
                substr(ref_build_number,0,5),
                pb_size_[3]);

            // Compare INT8 Performance results
            compare_result(int8_perf_value, last_int8_perf_value,"perf");

            // Compare INT8 Accuracy results
            compare_result(int8_acc_value, last_int8_acc_value, "acc");

            // Compare FP32 Performance results
            compare_result(fp32_perf_value, last_fp32_perf_value, "perf");

            // Compare INT8 Performance results
            compare_result(fp32_acc_value, last_fp32_acc_value, "acc");

            printf("</tr>\n");

        }
    ' >> ${WORKSPACE}/report.html
}

function generate_results {
    oses=$(sed '1d' ${summaryLog} |cut -d';' -f1 | awk '!a[$0]++')

    for os in ${oses[@]}
    do
        frameworks=$(sed '1d' ${summaryLog} |grep "^${os};" |cut -d';' -f3 | awk '!a[$0]++')
        for framework in ${frameworks[@]}
        do
            models=$(sed '1d' ${summaryLog} |grep "^${os};.*;${framework}" |cut -d';' -f6 | awk '!a[$0]++')
            for model in ${models[@]}
            do
                current_values=$(generate_inference ${summaryLog})
                last_values=$(generate_inference ${summaryLogLast})

                generate_html_core
            done
        done
    done
}

function generate_html_body {

cat >> ${WORKSPACE}/report.html << eof

<body>
    <div id="main">
        <h1 align="center">Neural Compressor Results Comparison
        [ <a href="${RUN_DISPLAY_URL}">Job-${BUILD_NUMBER}</a> ]</h1>
eof

cat >> ${WORKSPACE}/report.html << eof
        <h2>Comparison</h2>
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
                      <th colspan="4">INT8</th>
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
}

function generate_html_footer {

    cat >> ${WORKSPACE}/report.html << eof
            <tr>
                <td colspan="20"><font color="#d6776f">Note: </font>All data tested on INC Dedicated Server.</td>
                <td colspan="2" class="col-cell col-cell1 col-cellf"></td>
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
            min-width: 1920px;
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
