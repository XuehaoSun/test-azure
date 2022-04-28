output_file=$1
coverage_pr_log=$2
coverage_base_log=$3
module_name=$4
[[ ! -f $coverage_pr_log ]] && exit 1
[[ ! -f $coverage_base_log ]] && exit 1
file_name="./coverage_compare"
coverage_pr="./coverage_pr"
coverage_base="./coverage_base"
sed -n '/^Name  /','/^TOTAL/'p $coverage_pr_log > $coverage_pr
sed -n '/^Name  /','/^TOTAL/'p $coverage_base_log > $coverage_base
[[ ! -s $coverage_pr ]] && exit 0
[[ ! -s $coverage_base ]] && exit 0
sed -i "s|\/home.*${module_name}\/||g" $coverage_pr
sed -i "s|\/home.*${module_name}\/||g" $coverage_base
diff $coverage_pr $coverage_base > diff_file
[[ $? == 0 ]] && exit 0
grep -Po "[<,>,\d].*" diff_file | awk '{print $1 "\t" $2 "\t" $3 "\t"  $4 "\t"  $5 "\t" $6 "\t" $7}' | sed "/Name/d" | sed "/TOTAL/d" |sed "/---/d" > $file_name
[[ ! -s $file_name ]] && exit 0
[[ -f $output_file ]] && rm -f $output_file
touch $output_file

function generate_html_head {

cat > ${output_file} << eof

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

function main {
    generate_html_head
    # generate table head
    echo """
    <body>
    <div id="main">
      <h1 align="center">Coverage Detail</h1>
      <table class=\"features-table\" style=\"width: 60%;margin: 0 auto 0 0;empty-cells: hide\">
        <tr>
            <th>Commit</th>
            <th>FileName</th>
            <th>Miss</th>
            <th>Branch</th>
            <th>Cover</th>
        </tr>
    """ >> ${output_file}
    # generate compare detail
    cat $file_name | while read line
    do
        echo "read line: $line"
        if [[ $(echo $line | grep "[0-9]a[0-9]") ]] && [[ $(cat $file_name | grep -A 1 "$line" | grep ">") ]]; then
            file=$(cat $file_name | grep -A 1 "$line" | grep -Po ">.*" | sed 's/>[ \t]*//g' | awk '{print $1}')
            miss=$(cat $file_name | grep -A 1 "$line" | grep -Po ">.*" | sed 's/>[ \t]*//g' | awk '{print $3}')
            cover=$(cat $file_name | grep -A 1 "$line" | grep -Po ">.*" | sed 's/>[ \t]*//g' | awk '{print $6}')
            branch=$(cat $file_name | grep -A 1 "$line" | grep -Po ">.*" | sed 's/>[ \t]*//g' | awk '{print $4}')
            echo """
            <tr><td>PR/BASE</td><td style=\"text-align:left\">${file}</td>
                <td style=\"text-align:left\">NA/${miss}</td>
                <td style=\"text-align:left\">NA/${branch}</td>
                <td style=\"text-align:left\">NA/${cover}</td>
            </tr>""" >> ${output_file}
        elif [[ $(echo $line | grep "[0-9]c[0-9]") ]] && [[ $(cat $file_name | grep -A 1 "$line" | grep "<") ]] && [[ $(cat $file_name | grep -A 2 "$line" | grep ">") ]]; then
            file1=$(cat $file_name | grep -A 1 "$line" | grep -Po "<.*" | sed 's/<[ \t]*//g' | awk '{print $1}')
            miss1=$(cat $file_name | grep -A 1 "$line" | grep -Po "<.*" | sed 's/<[ \t]*//g' | awk '{print $3}')
            cover1=$(cat $file_name | grep -A 1 "$line" | grep -Po "<.*" | sed 's/<[ \t]*//g' | awk '{print $6}')
            branch1=$(cat $file_name | grep -A 1 "$line" | grep -Po "<.*" | sed 's/<[ \t]*//g' | awk '{print $4}')
            file2=$(cat $file_name | grep -A 2 "$line" | grep -Po ">.*" | sed 's/>[ \t]*//g' | awk '{print $1}')
            miss2=$(cat $file_name | grep -A 2 "$line" | grep -Po ">.*" | sed 's/>[ \t]*//g' | awk '{print $3}')
            cover2=$(cat $file_name | grep -A 2 "$line" | grep -Po ">.*" | sed 's/>[ \t]*//g' | awk '{print $6}')
            branch2=$(cat $file_name | grep -A 2 "$line" | grep -Po ">.*" | sed 's/>[ \t]*//g' | awk '{print $4}')
            echo """
            <tr><td>PR/BASE</td><td style=\"text-align:left\">${file1}</td>
                <td style=\"text-align:left\">${miss1}/${miss2}</td>
                <td style=\"text-align:left\">${branch1}/${branch2}</td>
                <td style=\"text-align:left\">${cover1}/${cover2}</td>
            </tr>""" >> ${output_file}
        elif [[ $(echo $line | grep "[0-9]d[0-9]") ]] && [[ $(cat $file_name | grep -A 1 "$line" | grep "<") ]]; then
            cat $file_name | grep -A 1 "$line" | grep -Po "<.*" | sed 's/<[ \t]*//g' >> $output_file
            file=$(cat $file_name | grep -A 1 "$line" | grep -Po "<.*" | sed 's/>[ \t]*//g' | awk '{print $1}')
            miss=$(cat $file_name | grep -A 1 "$line" | grep -Po "<.*" | sed 's/>[ \t]*//g' | awk '{print $3}')
            cover=$(cat $file_name | grep -A 1 "$line" | grep -Po "<.*" | sed 's/>[ \t]*//g' | awk '{print $6}')
            branch=$(cat $file_name | grep -A 1 "$line" | grep -Po "<.*" | sed 's/>[ \t]*//g' | awk '{print $4}')
            echo """
            <tr><td>PR/BASE</td><td style=\"text-align:left\">${file}/NA</td>
                <td style=\"text-align:left\">${miss}/NA</td>
                <td style=\"text-align:left\">${branch}/NA</td>
                <td style=\"text-align:left\">${cover}/NA</td>
            </tr>""" >> ${output_file}
        fi
    done
    # generage table end
    echo """</table></div></body></html>""" >> ${output_file}

}

main



