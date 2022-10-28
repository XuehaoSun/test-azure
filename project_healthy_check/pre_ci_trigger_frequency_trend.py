# coding=UTF-8
import argparse
import datetime
import json
import math
import os

from excel_report import report
from pr_duration import duration


def parse_arguments():
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", type=str, default="INC")
    parser.add_argument("--version", type=str, default="ALL")
    parser.add_argument("--days", type=int, default=15)
    parser.add_argument("--logfile_path", type=str, default="./")
    parser.add_argument("--jenkins_data_path", type=str, default="./")
    parser.add_argument("--type_list", type=str, default="Bug,Feature,Task")
    parser.add_argument("--jenkins_job_name", type=str, default="")
    parser.add_argument("--pr_repo_name", type=str, default="frameworks.ai.lpot.lpot-validation")
    return parser.parse_args()


def get_jira_data(project, version, type_list = []):
    project = "INC" if not project else project
    jira_project = "ILITV" if project == "INC" else "NLPTOOLKIU"
    version = args.version.replace(' ', '%20')
    issue_list = []
    for type in type_list:
        jira_api_url = "https://jira.devtools.intel.com/rest/api/2/search"
        jql = f"project%20%3D%20{jira_project}%20AND%20issuetype%20%3D{type}%20AND%20Sprint%20%3D%22{project}%20v{version}%22\&maxResults=1000"
        cmd = "curl -o out_{2}.json -x 'http://child-prc.intel.com:913' -k -vv -H \"Authorization: Bearer NDYwOTMxMzA3Nzk2OoFBUadiAkGCyKaiq3QBVq28F+Iw\" -H \"content-type: application/json\" -X GET {0}?jql={1}".format(jira_api_url, jql, type)
        os.system(cmd)
        issue_list.extend(search('out_{}.json'.format(type)))

    result_dict = get_before_days(args.days)
    for item in issue_list:
        print(item['Jira Status'], item['Jira ID'], item['Priority'], item['Jira Link'], item['Issue Type'], item['created day'], item['closed day'], item['live time'])
        if( item['Issue Type']=='Bug'):
            if (result_dict.get(item['created day'])):
                result_dict[item['created day']]['open'] += 1
            if (result_dict.get(item['closed day'])):
                result_dict[item['closed day']]['closed'] += 1

            for day, v in result_dict.items():
                time = datetime.datetime.strptime(day, '%Y-%m-%d')
                if (time < item['closed time'] and time >= item['created time']):
                    result_dict[day]['in_progress'] += 1

    return result_dict, issue_list


def get_jenkins_data(jenkins_data_path):
    with open(jenkins_data_path, "r", encoding='utf-8') as f:
        lines = f.readlines()
    data = [line.split('|')for line in lines]

    data = [{
        "job name": item[0],
        "Number"  : int(item[1]),
        "Trigger" : item[2],
        "Status"  : item[3],
        "Date"    : time_difference_conversion(item[4], {"hours":8, "minutes":0}, "%Y-%m-%dT%H:%M:%SZ", '%Y-%m-%d'),
        "Duration": item[5]
    } for item in data]

    return {"data": data, "date": [get_date(index) for index in range(args.days, 0, -1)]}


def time_difference_conversion(old_time, time_difference={"hours":0, "minutes":0}, old_format='', new_format='', return_str=True):
    old_time = datetime.datetime.strptime(old_time, old_format)
    new_time = old_time + datetime.timedelta(hours=time_difference['hours'], minutes=time_difference['minutes'])
    new_time = new_time.strftime(new_format) if (return_str) else new_time
    return new_time


def hourstimediff(new, old):
    newdate = datetime.datetime.strptime(new, "%Y-%m-%d %H:%M:%S")
    olddate = datetime.datetime.strptime(old, "%Y-%m-%d %H:%M:%S")
    total_s = (newdate - olddate).total_seconds()
    days = float(total_s / 3600 / 24)
    return days


def search(json_name):
    issue_list = []
    now = (datetime.datetime.now() + datetime.timedelta(hours=12, minutes=30)).strftime('%Y-%m-%d %H:%M:%S')
    with open(json_name, "r") as f:
        jira_data = json.loads(f.read())
    if (jira_data.__contains__("issues")):
        for issue in jira_data["issues"]:
            issue_dict = {}
            issue_dict['Jira ID']    = issue['key']
            issue_dict['Jira Link']  = "https://jira.devtools.intel.com/browse/{}".format(issue['key'])
            issue_dict['Issue Type'] = issue['fields']['issuetype']['name']
            issue_dict['Task']       = issue['fields']['summary'].replace(",", " ")
            issue_dict['Owner']      = issue['fields']['assignee']['displayName'].replace(",", " ") if issue['fields']['assignee'] is not None else "N/A"
            issue_dict['Priority']   = issue['fields']['priority']['name']
            issue_dict['Labels']     = ";".join(issue['fields']['labels'])
            issue_dict['ETA']        = issue['fields']['duedate'] if issue['fields']['duedate'] is not None else "N/A"
            issue_dict['Left Days']  = "{:.2f}".format(hourstimediff("{} 0:0:0".format(issue_dict['ETA']), now)) if issue_dict['ETA'] != "N/A" else "N/A"
            issue_dict['Jira Status']         = issue['fields']['status']['name']
            issue_dict['Affected Version']    = issue['fields']['versions'][0]['name'] if issue['fields']['versions'] else "N/A"
            issue_dict['Issue Type detailed'] = issue_dict['Issue Type']+'-'+('P1' if(issue_dict['Priority']=='P1-Stopper') else 'P2 or lower')
            # create_time = time_difference_conversion(issue['fields']['created'].split('.')[0], {"hours":12, "minutes":30}, "%Y-%m-%dT%H:%M:%S", return_str=False)
            
            create_time = datetime.datetime.strptime(issue['fields']['created'].split('.')[0], "%Y-%m-%dT%H:%M:%S") + datetime.timedelta(hours=12, minutes=30)
            issue_dict['created time'] = datetime.datetime.strptime(create_time.strftime('%Y-%m-%d'), '%Y-%m-%d')
            issue_dict['created day']  = create_time.strftime('%Y-%m-%d')
            create_time = create_time.strftime('%Y-%m-%d %H:%M:%S')

            if (issue_dict['Jira Status'] == "Closed" and issue['fields']['customfield_15336'] != None):
                closed_time = datetime.datetime.strptime(issue['fields']['customfield_15336'].split('.')[0], "%Y-%m-%dT%H:%M:%S") + datetime.timedelta(hours=12, minutes=30)
            else:
                closed_time = datetime.datetime.strptime("2099-1-1T12:00:00", "%Y-%m-%dT%H:%M:%S")
            issue_dict['closed time'] = datetime.datetime.strptime(closed_time.strftime('%Y-%m-%d'), '%Y-%m-%d')

            issue_dict['closed day'] = closed_time.strftime('%Y-%m-%d') if (issue_dict['Jira Status'] == "Closed") else "N/A"
            issue_dict['live time'] = "{:.2f}".format(
                -hourstimediff(create_time, now)) if issue_dict['Jira Status'] != "Closed" else "{:.2f}".format(
                    -hourstimediff(create_time, closed_time.strftime('%Y-%m-%d %H:%M:%S')))

            issue_list.append(issue_dict)

    return issue_list


def get_before_days(days=1):
    days_list = [get_date(index) for index in range(days, 0, -1)]
    day_dict = {}
    for i in days_list:
        day_dict[i] = {"open": 0, "closed": 0, "in_progress": 0}
    return day_dict


def get_date(before_of_day):
    today = datetime.datetime.now()
    offset = datetime.timedelta(days=-before_of_day)
    re_date = (today + offset).strftime('%Y-%m-%d')
    return re_date


def process_pr_list(day, repo_name):
    now = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    ddl_day = datetime.datetime.strptime(get_date(day), '%Y-%m-%d')

    current_page_open = 1
    current_page_closed = 1
    current_page = 0
    state_params = []

    days_list = ['0', '1', '2', '3', '4', '5', '6-10', '11-15', '>15']
    pr_list = {}
    for i in days_list:
        pr_list[i] = {"closed": 0, "open": 0}

    # calculate current_page
    while (current_page_open != -1 or current_page_closed != -1):
        # response json file
        current_page += 1
        if (current_page_open != -1 and current_page_closed != -1):
            state_params = ['open', 'closed']
        elif (current_page_closed != -1):
            state_params = ['closed']
        else:
            state_params = ['open']

        file_list = duration(current_page, state_params, repo_name)

        for json_file in file_list:
            with open(json_file, "r") as f:
                pr_data_list = json.loads(f.read())
                # open / close
                for pr_data in pr_data_list:
                    created_at_time_format = time_difference_conversion(pr_data['created_at'], {"hours":8, "minutes":0}, "%Y-%m-%dT%H:%M:%SZ", '%Y-%m-%d %H:%M:%S', False)
                    created_at_time_str = time_difference_conversion(pr_data['created_at'], {"hours":8, "minutes":0}, "%Y-%m-%dT%H:%M:%SZ", '%Y-%m-%d %H:%M:%S')
                    created_at_time_date = created_at_time_format.strftime('%Y-%m-%d')
                    created_at_date = datetime.datetime.strptime(created_at_time_date, '%Y-%m-%d')
                    
                    # closed_at exceed ddl_day
                    if ((created_at_date < ddl_day) and (pr_data['state'] == 'open')):
                        current_page_open = -1
                        break
                    if ((created_at_date < ddl_day) and (pr_data['state'] == 'closed')):
                        current_page_closed = -1
                        break

                    if (pr_data['state'] == 'open'):
                        current_time = now
                        previous_time = created_at_time_str
                    elif (pr_data['state'] == 'closed'):
                        current_time = time_difference_conversion(pr_data['closed_at'], {"hours":8, "minutes":0}, "%Y-%m-%dT%H:%M:%SZ", '%Y-%m-%d %H:%M:%S')
                        previous_time = created_at_time_str

                    days = math.ceil(hourstimediff(current_time, previous_time))

                    if (days <= 5): 
                        pr_list[str(days)][pr_data['state']] += 1
                    elif (days <= 10): 
                        pr_list['6-10'][pr_data['state']] += 1
                    elif (days <= 15): 
                        pr_list['11-15'][pr_data['state']] += 1
                    else: 
                        pr_list['>15'][pr_data['state']] += 1

    return pr_list


if __name__ == "__main__":
    args = parse_arguments()
    pr_list = process_pr_list(args.days, args.pr_repo_name)
    summary_dict, issue_list = get_jira_data(project=args.project, version=args.version, type_list=args.type_list.split(','))
    jenkins_data = get_jenkins_data(args.jenkins_data_path)
    report(summary_dict, issue_list, jenkins_data, args.jenkins_job_name, args.logfile_path, args.days, pr_list)
