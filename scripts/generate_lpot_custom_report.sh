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
    awk -v framework="${framework}" -v model="${model}" -v os="${os}" -v platform=${platform} -F ';' '
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
            if($1 == os && $2 == platform && $3 == framework && $6 == model) {
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

function generate_html_core {
    fw_version=$(grep "^${os};${platform};${framework};.*;${model};" ${tuneLog} |awk -F';' '{print $4}')
    tuning_strategy=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLog} |awk -F';' '{print $6}')
    tuning_time=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLog} |awk -F';' '{print $7}')
    tuning_count=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLog} |awk -F';' '{print $8}')
    tuning_log=$(grep "^${os};${platform};${framework};${fw_version};${model};" ${tuneLog} |awk -F';' '{print $9}')
    echo "<tr><td rowspan=3>${platform}</td><td rowspan=3>${os}</td><td rowspan=3>${framework}</td><td>${fw_version}</td><td rowspan=3>${model}</td><td><a href=\"${JENKINS_URL}/job/${new_build[0]}/${new_build[1]}\">${new_build[0]} #${new_build[1]}</a></td><td><a href=${tuning_log}>${tuning_strategy}</a></td>" >> ${WORKSPACE}/report.html
    echo "<td><a href=${tuning_log}>${tuning_time}</a></td><td><a href=${tuning_log}>${tuning_count}</a></td>" >> ${WORKSPACE}/report.html

    ref_fw_ver=$(grep "^${os};${platform};${framework};.*;${model};" ${tuneLogLast} |awk -F';' '{print $4}')
    tuning_strategy=$(grep "^${os};${platform};${framework};${ref_fw_ver};${model};" ${tuneLogLast} |awk -F';' '{print $6}')
    tuning_time=$(grep "^${os};${platform};${framework};${ref_fw_ver};${model};" ${tuneLogLast} |awk -F';' '{print $7}')
    tuning_count=$(grep "^${os};${platform};${framework};${ref_fw_ver};${model};" ${tuneLogLast} |awk -F';' '{print $8}')
    tuning_log=$(grep "^${os};${platform};${framework};${ref_fw_ver};${model};" ${tuneLogLast} |awk -F';' '{print $9}')

    echo |awk -F ';' -v current_values="${current_values}" -v last_values="${last_values}" \
              -v pb_size="${pb_size}" -v last_pb_size="${last_pb_size}" \
              -v ts="${tuning_strategy}" -v tt="${tuning_time}" -v tc="${tuning_count}" -v tl="${tuning_log}" \
              -v ref_build_name="${ref_build[0]}" -v ref_build_number="${ref_build[1]}" \
              -v new_build_name="${new_build[0]}" -v new_build_number="${new_build[1]}" \
              -v ref_fw_ver="${ref_fw_ver}" \
              -v jenkins_url="${JENKINS_URL}" '

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
                       printf("<td rowspan=3 style=\"background-color:#90EE90\">%.4f</td>", target);
                    }else if(target < -0.05) {
                       printf("<td rowspan=3 style=\"background-color:#FFD2D2\">%.4f</td>", target);
                    }else{
                       printf("<td rowspan=3>%.4f</td>", target);
                    }
                }else if(c == "ms") {
                    target = a / b;
                    if(target >= 1.5) {
                       printf("<td rowspan=3 style=\"background-color:#90EE90\">%.4f</td>", target);
                    }else if(target < 1) {
                       printf("<td  rowspan=3 style=\"background-color:#FFD2D2\">%.4f</td>", target);
                    }else{
                       printf("<td rowspan=3>%.4f</td>", target);
                    }
                }else if(c == "fps") {
                    target = a / b;
                    if(target >= 2) {
                       printf("<td rowspan=3 style=\"background-color:#90EE90\">%.4f</td>", target);
                    }else if(target < 1) {
                       printf("<td rowspan=3 style=\"background-color:#FFD2D2\">%.4f</td>", target);
                    }else{
                       printf("<td rowspan=3>%.4f</td>", target);
                    }
                }else {
                    // Compare PB size
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
                }else {
                    target = a / b;
                    if(target >= 0.95) {
                        status_png = "background-color:#90EE90";
                    }else {
                        status_png = "background-color:#FFD2D2";
                    }
                }
                printf("<td style=\"%s\" colspan=2>%.4f</td>", status_png, target);
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

            // PB size
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
            printf("</tr>\n<tr><td>%8$s</td><td><a href=\"%1$s/job/%2$s/%3$s\">%2$s #%3$s</a></td><td><a href=%7$s>%4$s</a></td><td><a href=%7$s>%5$s</a></td><td><a href=%7$s>%6$s</a></td>", jenkins_url, ref_build_name, ref_build_number, ts, tt, tc, tl, ref_fw_ver);
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
            printf("</tr>\n<tr><td>-</td><td>%s... #%s/%s... #%s</td><td colspan=4>Mem Peak:%s</td>",
                substr(new_build_name,0,5),
                substr(new_build_number,0,5),
                substr(ref_build_name,0,5),
                substr(ref_build_number,0,5),
                pb_size_[3]);

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

function generate_results {
    oses=$(sed '1d' ${summaryLog} |cut -d';' -f1 | awk '!a[$0]++')
    
    for os in ${oses[@]}
    do
        platforms=$(sed '1d' ${summaryLog} |grep "^${os}" |cut -d';' -f2 | awk '!a[$0]++')
        for platform in ${platforms[@]}
        do
            frameworks=$(sed '1d' ${summaryLog} |grep "^${os};${platform}" |cut -d';' -f3 | awk '!a[$0]++')
            for framework in ${frameworks[@]}
            do
                models=$(sed '1d' ${summaryLog} |grep "^${os};${platform};${framework}" |cut -d';' -f6 | awk '!a[$0]++')
                for model in ${models[@]}
                do
                    current_values=$(generate_inference ${summaryLog})
                    last_values=$(generate_inference ${summaryLogLast})

                    # PB Size
                    pb_size=$(grep "^${os};${platform};${framework};*;${model};" ${tuneLog} |awk -F ';' '{printf("%s;%s;%s", $9,$10,$11)}')
                    last_pb_size=$(grep "^${os};${platform};${framework};*;${model};" ${tuneLogLast} |awk -F ';' '{printf("%s;%s;%s", $9,$10,$11)}')

                    generate_html_core
                done
            done
        done
    done
}

function generate_html_body {

cat >> ${WORKSPACE}/report.html << eof

<body>
    <div id="main">
        <h1 align="center">LPOT Results Comparison
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
                      <th colspan="6">INT8</th>
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
}

function generate_html_footer {

    cat >> ${WORKSPACE}/report.html << eof
            <tr>
                <td colspan="22"><font color="#d6776f">Note: </font>All data tested on TensorFlow Dedicated Server.</td>
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
