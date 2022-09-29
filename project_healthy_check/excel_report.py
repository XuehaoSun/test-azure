from datetime import datetime
import math
import xlsxwriter
import os


def drawing(workbook, worksheet, chart_type, chart_sheet, multiList, chart_title, insert_sheet, chart_x_title, chart_y_title):
    # auto graph - multiLine
    chart = workbook.add_chart({'type': chart_type})

    for item in multiList:
        chart.add_series({
            'name': '={}!${}${}'.format(chart_sheet, item['chart_name_col'], item['chart_name_row']),
            'categories': '={}!${}${}:${}${}'.format(chart_sheet, item['chart_categories_from_col'], item['chart_categories_from_row'], item['chart_categories_to_col'], (item['chart_categories_to_row'] + 1)),
            'values': '={}!${}${}:${}${}'.format(chart_sheet, item['chart_values_from_col'], item['chart_values_from_row'], item['chart_values_to_col'], (item['chart_values_to_row'] + 1)),
            # 'line': {
            #     'color': item['chartColor']
            # },
        })

    chart.set_title({'name': chart_title})
    chart.set_style(10)
    chart.set_x_axis({'name': chart_x_title})
    chart.set_y_axis({'name':  chart_y_title})
    worksheet.insert_chart(insert_sheet, chart)


def create(log_file, file_name='status_report.xlsx'):
    file_path = os.path.join(log_file, file_name)
    workbook = xlsxwriter.Workbook(file_path)
    return workbook


def jira_status_trend(workbook, worksheet, status_data, days):
    # Add an Excel date format.
    date_format = workbook.add_format({'num_format': 'mmm d'})

    # Adjust the column width.
    worksheet.set_column(0, 0, 20)

    # Write some data headers.
    worksheet.write('A1', 'Date')
    worksheet.write('B1', 'Open')
    worksheet.write('C1', 'Closed')
    worksheet.write('D1', 'WIP')

    # Start from the first cell below the headers.
    row = 1
    col = 0

    for key, value in status_data.items():
        # Convert the date string into a datetime object.
        date = datetime.strptime(key, "%Y-%m-%d")
        worksheet.write_datetime(row, col, date, date_format)
        worksheet.write_number(row, col + 1, value['open'])
        worksheet.write_number(row, col + 2, value['closed'])
        worksheet.write_number(row, col + 3, value['in_progress'])
        row += 1

    chart_type = 'line'
    chart_sheet = 'jira_bug_trend'

    OC_chart_title = 'jira bug OPEN/CLOSE'
    OC_x_title = 'Date'
    OC_y_title = 'count'
    OC_insert_sheet = 'G3'
    first_col = 'B'
    OC_multiList = [
        {
            "chart_name_col"           : chr(ord(first_col) + index),
            "chart_name_row"           : 1,
            "chart_categories_from_col": 'A',
            "chart_categories_from_row": 2,
            "chart_categories_to_col"  : 'A',
            "chart_categories_to_row"  : days,
            "chart_values_from_col"    : chr(ord(first_col) + index),
            "chart_values_from_row"    : 2,
            "chart_values_to_col"      : chr(ord(first_col) + index),
            "chart_values_to_row"      : days,
        } for index in range(2)
    ]    

    WIP_chart_title = 'jira bug WIP'
    WIP_x_title = 'Date'
    WIP_y_title = 'count'
    WIP_insert_sheet = 'G21'
    WIP_multiList = [
        {
            "chart_name_col"           : 'D',
            "chart_name_row"           : 1,
            "chart_categories_from_col": 'A',
            "chart_categories_from_row": 2,
            "chart_categories_to_col"  : 'A',
            "chart_categories_to_row"  : days,
            "chart_values_from_col"    : 'D',
            "chart_values_from_row"    : 2,
            "chart_values_to_col"      : 'D',
            "chart_values_to_row"      : days,
        }
    ]

    drawing(workbook, worksheet, chart_type, chart_sheet, OC_multiList, OC_chart_title, OC_insert_sheet, OC_x_title, OC_y_title)
    drawing(workbook, worksheet, chart_type, chart_sheet, WIP_multiList, WIP_chart_title, WIP_insert_sheet, WIP_x_title, WIP_y_title)


def write_jira_live_days_full_data(workbook, worksheet, issue_data):
    # Add an Excel date format.
    date_format = workbook.add_format({'num_format': 'mmm d'})

    # Adjust the column width.
    worksheet.set_column(3, 4, 20)

    # Write some data headers.
    worksheet.write('A1', 'Jira ID')
    worksheet.write('B1', 'Live days')
    worksheet.write('C1', 'Issue Type')
    worksheet.write('D1', 'Created date')
    worksheet.write('E1', 'Closed date')

    # Start from the first cell below the headers.
    row = 1
    col = 0

    for issue in issue_data:
        # Convert the date string into a datetime object.
        created_date = datetime.strptime(issue['created day'], "%Y-%m-%d")
        live_days = math.ceil(float(issue['live time']))  # Bug live time (rounded up)
        Jira_ID = issue['Jira ID']
        Issue_Type = issue['Issue Type']

        worksheet.write_string(row, col, Jira_ID)
        worksheet.write_number(row, col + 1, live_days)
        worksheet.write_string(row, col + 2, Issue_Type)
        worksheet.write_datetime(row, col + 3, created_date, date_format)
        if (issue['closed day'] == 'N/A'):
            worksheet.write_string(row, col + 4, issue['closed day'])
        else:
            closed_date = datetime.strptime(issue['closed day'], "%Y-%m-%d")
            worksheet.write_datetime(row, col + 4, closed_date, date_format)
        row += 1


def write_jira_live_days(workbook, worksheet, issue_data):
    # days_list = [index for index in range(0, days+1)]
    days_list = ['0', '1', '2', '3', '4', '5', '6-10', '11-15', '>15']
    day_dict = {}
    for i in days_list:
        day_dict[i] = {"Bug-P1": 0, "Task-P1": 0, "Feature-P1": 0, "Bug-P2 or lower": 0, "Task-P2 or lower": 0, "Feature-P2 or lower": 0}

    for issue in issue_data:
        live_days = math.ceil(float(issue['live time']))
        if (live_days <= 5):
            day_dict[str(live_days)][issue['Issue Type detailed']] += 1
        elif (live_days <= 10):
            day_dict['6-10'][issue['Issue Type detailed']] += 1
        elif (live_days <= 15):
            day_dict['11-15'][issue['Issue Type detailed']] += 1
        else:
            day_dict['>15'][issue['Issue Type detailed']] += 1

    # Adjust the column width.
    worksheet.set_column(3, 6, 15)

    # Write data headers.
    worksheet.write('A1', 'Live days')
    worksheet.write('B1', 'Bug-P1')
    worksheet.write('C1', 'Task-P1')
    worksheet.write('D1', 'Feature-P1')
    worksheet.write('E1', 'Bug-P2 or lower')
    worksheet.write('F1', 'Task-P2 or lower')
    worksheet.write('G1', 'Feature-P2 or lower')

    # Start from the first cell below the headers.
    row = 1
    col = 0

    for item, value in day_dict.items():
        worksheet.write_string(row, col, item)
        worksheet.write_number(row, col + 1, value['Bug-P1'])
        worksheet.write_number(row, col + 2, value['Task-P1'])
        worksheet.write_number(row, col + 3, value['Feature-P1'])
        worksheet.write_number(row, col + 4, value['Bug-P2 or lower'])
        worksheet.write_number(row, col + 5, value['Task-P2 or lower'])
        worksheet.write_number(row, col + 6, value['Feature-P2 or lower'])
        row += 1

    chart_type = 'column'
    chart_sheet = 'jira_issue_live_days'

    P1_LI_chart_title = 'P1 issue live days'
    P1_LI_x_title = 'Live days'
    P1_LI_y_title = 'count'    
    P1_LI_insert_sheet = 'J3'
    first_col = 'B'
    P1_LI_multiList = [
        {
            "chart_name_col"           : chr(ord(first_col) + index),
            "chart_name_row"           : 1,
            "chart_categories_from_col": 'A',
            "chart_categories_from_row": 2,
            "chart_categories_to_col"  : 'A',
            "chart_categories_to_row"  : len(days_list),
            "chart_values_from_col"    : chr(ord(first_col) + index),
            "chart_values_from_row"    : 2,
            "chart_values_to_col"      : chr(ord(first_col) + index),
            "chart_values_to_row"      : len(days_list),
        } for index in range(3)
    ]    

    P2_LI_chart_title = 'P2 or lower issue live days'
    P2_LI_x_title = 'Live days'
    P2_LI_y_title = 'count'      
    P2_LI_insert_sheet = 'J21'
    P2_first_col = 'E'
    P2_LI_multiList = [
        {
            "chart_name_col"           : chr(ord(P2_first_col) + index),
            "chart_name_row"           : 1,
            "chart_categories_from_col": 'A',
            "chart_categories_from_row": 2,
            "chart_categories_to_col"  : 'A',
            "chart_categories_to_row"  : len(days_list),
            "chart_values_from_col"    : chr(ord(P2_first_col) + index),
            "chart_values_from_row"    : 2,
            "chart_values_to_col"      : chr(ord(P2_first_col) + index),
            "chart_values_to_row"      : len(days_list),
        } for index in range(3)
    ]    
   
    drawing(workbook, worksheet, chart_type, chart_sheet, P1_LI_multiList, P1_LI_chart_title, P1_LI_insert_sheet, P1_LI_x_title, P1_LI_y_title)
    drawing(workbook, worksheet, chart_type, chart_sheet, P2_LI_multiList, P2_LI_chart_title, P2_LI_insert_sheet, P2_LI_x_title, P2_LI_y_title)


def write_jenkins_trigger_status(workbook, worksheet, jenkins_data, jenkins_job_name, days):

    result_dict = {}
    jenkins_job_name = jenkins_job_name.split(',')
    for name in jenkins_job_name:
        result_dict[name] = {}
    for name in result_dict:
        for date in jenkins_data['date']:
            result_dict[name][date] = 0

    for item in jenkins_data['data']:
        result_dict[item["job name"]][item["Date"]] = result_dict[item["job name"]][item["Date"]] + 1  if (result_dict[item["job name"]].get(item["Date"], "")) else 1

    date_format = workbook.add_format({'num_format': 'mmm d'})

    # Adjust the column width.
    worksheet.set_column(0, 0, 20)

    # Write some data headers.
    worksheet.write('A1', 'Date')
    job_names = [job_name for job_name in result_dict]
    col = 'A'
    for job_name in job_names:
        col = chr(ord(col) + 1)
        worksheet.write(col + '1', job_name)

    # write date
    row, col = 1, 0
    for date in result_dict[job_names[0]]:
        date = datetime.strptime(date, "%Y-%m-%d")
        worksheet.write_datetime(row, col, date, date_format)
        row += 1

    # write count by jobnames
    col = 1
    for job_name, value in result_dict.items():
        row = 1
        for date, count in value.items():
            worksheet.write_number(row, col, count)
            row += 1
        col += 1

    chart_type = 'column'
    chart_sheet = 'trigger_count'

    TR_chart_title = 'Trigger count'
    TR_x_title = 'Date'
    TR_y_title = 'count'     
    first_col = 'B'
    TR_multiList = [
        {
            "chart_name_col"           : chr(ord(first_col) + index),
            "chart_name_row"           : 1,
            "chart_categories_from_col": 'A',
            "chart_categories_from_row": 2,
            "chart_categories_to_col"  : 'A',
            "chart_categories_to_row"  : days + 2,
            "chart_values_from_col"    : chr(ord(first_col) + index),
            "chart_values_from_row"    : 2,
            "chart_values_to_col"      : chr(ord(first_col) + index),
            "chart_values_to_row"      : days + 2,
            "chartColor"               : 'white'
        } for index in range(len(job_names))
    ]
    TR_insert_sheet = '{}3'.format(chr(ord('D') + len(job_names)))

    drawing(workbook, worksheet, chart_type, chart_sheet, TR_multiList, TR_chart_title, TR_insert_sheet, TR_x_title, TR_y_title)


def write_jenkins_trigger_status_full_data(workbook, worksheet, jenkins_data):
    date_format = workbook.add_format({'num_format': 'mmm d'})

    # Adjust the column width.
    worksheet.set_column(2, 2, 20)

    # Write some data headers.
    worksheet.write('A1', 'Number')
    worksheet.write('B1', 'Status')
    worksheet.write('C1', 'Date')

    # Start from the first cell below the headers.
    row = 1
    col = 0

    for item in jenkins_data:
        # Convert the date string into a datetime object.
        date = datetime.strptime(item["Date"], "%Y-%m-%d")
        worksheet.write_number(row, col, item['Number'])
        worksheet.write_string(row, col + 1, item['Status'])
        worksheet.write_datetime(row, col + 2, date, date_format)
        row += 1


def pr_duration(workbook, worksheet, pr_list, days=10):
    # Write some data headers.
    worksheet.write('A1', 'Pr duration')
    worksheet.write('B1', 'open')
    worksheet.write('C1', 'closed')

    # Start from the first cell below the headers.
    row = 1
    col = 0

    for item, value in pr_list.items():
        worksheet.write_string(row, col, item)
        worksheet.write_number(row, col + 1, value['open'])
        worksheet.write_number(row, col + 2, value['closed'])
        row += 1

    chart_type = 'line'
    chart_sheet = 'pr_duration'

    PR_chart_title = 'Pr duration'
    PR_x_title = 'Pr duration'
    PR_y_title = 'count'      
    PR_insert_sheet = 'G3'
    first_col = 'B'
    PR_multiList = [
        {
            "chart_name_col"           : chr(ord(first_col) + index),
            "chart_name_row"           : 1,
            "chart_categories_from_col": 'A',
            "chart_categories_from_row": 2,
            "chart_categories_to_col"  : 'A',
            "chart_categories_to_row"  : len(pr_list),
            "chart_values_from_col"    : chr(ord(first_col) + index),
            "chart_values_from_row"    : 2,
            "chart_values_to_col"      : chr(ord(first_col) + index),
            "chart_values_to_row"      : len(pr_list),
        } for index in range(2)
    ]

    drawing(workbook, worksheet, chart_type, chart_sheet, PR_multiList, PR_chart_title, PR_insert_sheet, PR_x_title, PR_y_title)


def report(summary_dict, issue_list, jenkins_data, jenkins_job_name, log_file, days, pr_list):
    workbook = create(log_file)
    jira_status_trend(workbook, workbook.add_worksheet('jira_bug_trend'), summary_dict, days)
    write_jira_live_days(workbook, workbook.add_worksheet('jira_issue_live_days'), issue_list)
    write_jenkins_trigger_status(workbook, workbook.add_worksheet('trigger_count'), jenkins_data, jenkins_job_name, days)
    pr_duration(workbook, workbook.add_worksheet('pr_duration'), pr_list)
    workbook.close()


if __name__ == "__main__":
    report()
