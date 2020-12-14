#!/bin/bash
# BUILD_URL=.
# WORKSPACE=.
# RUN_DISPLAY_URL=.
# BUILD_NUMBER=.
# lpot_branch=.
# lpot_commit=.
# summaryLog=summary.log

function generate_results {
    echo "summaryLog: ${summaryLog}"
    echo "lpot_branch: ${lpot_branch}"
    echo "lpot_commit: ${lpot_commit}"
    features=$(sed '1d' ${summaryLog} |cut -d';' -f1 | awk '!a[$0]++')
    generate_html_head
    for feature in ${features[@]}
    do
      generate_html_core ${feature}
    done
    generate_html_footer
}

function generate_html_core {

  feature_name=$1
  png_path="https://inteltf-jenk.sh.intel.com/static/93433cd1/images/24x24"
  result_list=($(grep ${feature_name} ${summaryLog} |sed 's/;/ /g'))
  feature_url=${result_list[2]}

  if [[ "${result_list[1]}" == "fail" ]];then
      feature_status="<img src=${png_path}/red.png></img>"
  elif [[ "${result_list[1]}" == "pass" ]];then
      feature_status="<img src=${png_path}/blue.png></img>"
  else
      feature_status="<img src=${png_path}/yellow.png></img>"
  fi

  cat >> ${WORKSPACE}/report.html <<  eof
        $(
             if [ "${result_list[1]}" != "" ];then
                echo "<tr><td style=\"text-align:left\"><a href=\"${feature_url}\">${feature_name}</a></td>"
                echo "<td>${feature_status}</td></tr>"
             fi
        )
eof
}

function generate_html_footer {

    cat >> ${WORKSPACE}/report.html << eof
        </table>
    </div>
  </body>
  </html>
eof
}

function generate_html_head {

  Test_Info_Title="<th colspan="4">Test Branch</th> <th colspan="4">Commit ID</th> "
  Test_Info="<th colspan="4">${lpot_branch}</th> <th colspan="4">${lpot_commit}</th> "

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

<body>
    <div id="main">
    <h1 align="center">LPOT Feature Tests
            [ <a href="${RUN_DISPLAY_URL}">Job-${BUILD_NUMBER}</a> ]</h1>

    <h2>Summary</h2>
	    <table class="features-table">
	        <tr>
              <th>Platform</th>
              <th>Repo</th>
              ${Test_Info_Title}
		      </tr>
		      <tr>
			        <td>CLX8280</td>
			        <td><a href="https://gitlab.devtools.intel.com/intelai/LowPrecisionInferenceTool">LPOT</a></td>
              ${Test_Info}
			    </tr>
	    </table>

    <h2>Feature Test</h2>
        <table class="features-table" style="width: 60%;margin: 0 auto 0 0;">
        <tr>
            <th>Task</th>
            <th>Status</th>
        </tr>
eof

}

generate_results