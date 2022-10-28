# coding=utf-8
import argparse
import datetime
import math
import os
import re

from dateutil.relativedelta import relativedelta

from excel_report import report_bug_escape_rate
from pre_ci_trigger_frequency_trend import search


def parse_arguments():
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", type=str, default="INC", help='INC, NLPTOOLKIT')
    parser.add_argument("--start_days", type=str, default="2022-01-01")
    parser.add_argument("--logfile_path", type=str, default="./")
    return parser.parse_args()


def get_jira_data(project):
    project = "INC" if not project else project
    jira_project = "ILITV" if project == "INC" else "NLPTOOLKIU"

    jira_api_url = "https://jira.devtools.intel.com/rest/api/2/search"
    jql = f"project%20%3D%20{jira_project}%20AND%20issuetype%20%3D%20Bug%20AND%20created%20\>%3D%20\"{start_days}\""
    issue_list = []
    bug_list = [1]
    while (len(bug_list)):
        cmd = f"curl -o bug_status.json -x 'http://child-prc.intel.com:913' -k -vv -H \"Authorization: Bearer NDYwOTMxMzA3Nzk2OoFBUadiAkGCyKaiq3QBVq28F+Iw\" -H \"content-type: application/json\" -X GET {jira_api_url}?jql={jql}\&startAt={len(issue_list)}\&maxResults=1000"
        os.system(cmd)
        bug_list = search('bug_status.json')
        issue_list.extend(bug_list)
    print(len(issue_list))
    return issue_list


def get_bug_escape_status(issue_list):
    start_quarter = math.ceil(start_days.month / 3)
    month_list = [start_days + relativedelta(months=i) for i in range(0, period.get('monthly'))]
    quarter_list = [{
        'year': start_days.year + (start_quarter + i - 1) // 4,
        'quarter': (start_quarter + i - 1) % 4 + 1
    } for i in range(0, period.get('quarterly'))]
    year_list = [start_days + relativedelta(years=i) for i in range(0, period.get('yearly'))]
    monthly_data_dict, quarterly_data_dict, yearly_data_dict = {}, {}, {}

    # analyse monthly data
    for date in month_list:
        if (not monthly_data_dict.__contains__(f"{date.year}-{date.month}")):
            monthly_data_dict[f"{date.year}-{date.month}"] = {"external": 0, "internal": 0, "total": 0}

    # analyse quarterly data
    for date in quarter_list:
        if (not quarterly_data_dict.__contains__(f"{date.get('year')}-Q{date.get('quarter')}")):
            quarterly_data_dict[f"{date.get('year')}-Q{date.get('quarter')}"] = {"external": 0, "internal": 0, "total": 0}

    # analyse yearly data
    for date in year_list:
        if (not yearly_data_dict.__contains__(f"{date.year}")):
            yearly_data_dict[f"{date.year}"] = {"external": 0, "internal": 0, "total": 0}

    for item in issue_list:
        print(item['Jira ID'], item['Priority'], item['Jira Link'], item['created day'], item['Labels'])
        open_year = item['created time'].year
        open_month = item['created time'].month
        open_quarter = math.ceil(item['created time'].month / 3)

        if (item['Labels'] == 'external' or item['Labels'] == 'External'):
            monthly_data_dict[f"{open_year}-{open_month}"]['external'] += 1
            quarterly_data_dict[f"{open_year}-Q{open_quarter}"]['external'] += 1
            yearly_data_dict[f"{open_year}"]['external'] += 1
        else:
            monthly_data_dict[f"{open_year}-{open_month}"]['internal'] += 1
            quarterly_data_dict[f"{open_year}-Q{open_quarter}"]['internal'] += 1
            yearly_data_dict[f"{open_year}"]['internal'] += 1

        monthly_data_dict[f"{open_year}-{open_month}"]['total'] += 1
        quarterly_data_dict[f"{open_year}-Q{open_quarter}"]['total'] += 1
        yearly_data_dict[f"{open_year}"]['total'] += 1

    print(monthly_data_dict)
    print(quarterly_data_dict)
    print(yearly_data_dict)

    return {'monthly': monthly_data_dict, 'quarterly': quarterly_data_dict, 'yearly': yearly_data_dict}


if __name__ == "__main__":
    args = parse_arguments()
    y, m, d = [int(i) for i in re.findall(r'\d+', args.start_days)]
    start_days = datetime.date(y, m, d)
    end_days = datetime.date.today()
    assert (end_days >= start_days), 'Start days later than today'
    period = {
        'monthly':
        abs((start_days.year - end_days.year) * 12 + (start_days.month - end_days.month)) + 1,
        'quarterly':
        abs((start_days.year - end_days.year) * 4 + (math.ceil(start_days.month / 3) - math.ceil(end_days.month / 3))) + 1,
        'yearly':
        abs(start_days.year - end_days.year) + 1,
    }
    data = get_jira_data(project=args.project)
    data = get_bug_escape_status(data)
    report_bug_escape_rate(data, args.logfile_path, f'from {start_days} to {end_days}', args.project)
