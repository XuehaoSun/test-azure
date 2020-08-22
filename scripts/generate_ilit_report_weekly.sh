#!/bin/bash

# WORKSPACE=.

function main {

    generate_html_head
    generate_html_body
    generate_html_footer

}

function generate_html_body {

    cat >> ${WORKSPACE}/report.html << eof
<body>
    <div id="main">
	    <h1 align="center">iLiT Weekly Report</h1>

        <h2>Summary</h2>
	    <table class="features-table">
            <tr>
                <th>Python</th>
                <th>Framework</th>
                <th>Version</th>
                <th>Strategy</th>
                <th>Status</th>
                <th>Link</th>
            </tr>
            $(
                sed '1d' summary.log |awk -F ';' '{
                    printf("<tr><td>%s</td>", $1);
                    printf("<td>%s</td>", $2);
                    printf("<td>%s</td>", $3);
                    printf("<td>%s</td>", $4);
                    printf("<td>%s</td>", $5);
                    printf("<td><a href=\"%s\">%s</a></td></tr>", $6,$7);
                }'                
            )
	    </table>
eof
}

function generate_html_footer {

    cat >> ${WORKSPACE}/report.html << eof

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
