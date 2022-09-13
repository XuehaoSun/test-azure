#!/bin/bash

function main {
    script_dir=$(dirname "${BASH_SOURCE[0]}")
    echo "summary_dir: ${summary_dir}"
    echo "summary_dir_last: ${summary_dir_last}"
    echo "overview_log: ${overview_log}"
    echo "script_dir: ${script_dir}"

    conda_env_name="sparse_lib"
    export PATH=${HOME}/miniconda3/bin/:$PATH
    if [[ ! $(conda info -e | grep ${conda_env_name}) ]]; then
        conda create -n ${conda_env_name} python=3.8 -y
    fi
    conda activate ${conda_env_name} || source activate ${conda_env_name}
    pip install --upgrade pip
    pip install pandas==1.4.4 Jinja2==3.1.2 openpyxl==3.0.10 matplotlib==3.5.3 --proxy=http://child-prc.intel.com:913

    generate_html_head
    generate_html_overview
    for caselog in $(find $summary_dir/*_summary.log); do
        local name=$(basename $caselog | sed 's/_summary.log//')
        echo "<h2>$name <a href=\"${name}_summary.xlsx\" style=\"font-size: initial\">${name}_summary.xlsx</a> </h2>" >>${WORKSPACE}/report.html
        python "$script_dir/generate_sparse_lib.py" $caselog ${summary_dir_last}/$(basename $caselog) \
            >>${WORKSPACE}/report.html \
            2>>${WORKSPACE}/perf_regression.log
    done

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
    PR_TITLE=''
    Test_Info_Title=''
    Test_Info=''

    commit_id=$(echo ${ghprbActualCommit} | awk '{print substr($1,1,7)}')

    PR_TITLE="[ <a href='${ghprbPullLink}'>PR-${ghprbPullId}</a> ]"
    Test_Info_Title="<th colspan="2">Source Branch</th> <th colspan="4">Target Branch</th> <th colspan="4">Commit</th> "
    Test_Info="<td colspan="2">${MR_source_branch}</td> <td colspan="4">${MR_target_branch}</td> <td colspan="4">${commit_id}"

    cat >>${WORKSPACE}/report.html <<eof

<body>
    <div id="main">
        <h1 align="center">Sparse Lib Tests ${PR_TITLE}
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
}

function generate_html_head {
    cat >${WORKSPACE}/report.html <<eof

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

    cat >>${WORKSPACE}/report.html <<eof
    </div>
</body>
</html>
eof
}

main
