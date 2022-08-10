from codecs import utf_16_be_decode
import re
import os
import datetime
import json
import requests
import sys
import argparse


def main(project: str, version: str):
    now = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    project = "INC" if not project else project
    jira_project = "ILITV" if project == "INC" else "NLPTOOLKIU"
    version = "1.12.1" if not version else version
    filename = "log"
    issue_list = []
    issue_dict = {}
    jira_api_url = "https://jira.devtools.intel.com/rest/api/2/search"
    jql="project%20%3D%20{2}%20AND%20issuetype%20%3DBug%20AND%20Sprint%20%3D%22{0}%20v{1}%22".format(project, version, jira_project)
    cmd = "curl -o out.json -k -vv -H \"Authorization: Bearer NDYwOTMxMzA3Nzk2OoFBUadiAkGCyKaiq3QBVq28F+Iw\" -H \"content-type: application/json\" -X GET {0}?jql={1}".format(jira_api_url, jql)
    os.system(cmd)
    with open("./out.json", "r") as f:
        jira_data = json.loads(f.read())
    for issue in jira_data["issues"]:
        issue_dict = {}
        issue_dict['Jira ID'] = issue['key']
        issue_dict['Jira Link'] = "https://jira.devtools.intel.com/browse/{}".format(issue['key'])
        issue_dict['Issue Type'] = issue['fields']['issuetype']['name']
        issue_dict['Task'] = issue['fields']['summary'].replace(",", " ")
        issue_dict['Owner'] = issue['fields']['assignee']['displayName']. replace(",", " ") if issue['fields']['assignee'] is not None else "N/A"
        issue_dict['Priority'] = issue['fields']['priority']['name']
        issue_dict['Labels'] = ";".join(issue['fields']['labels'])
        issue_dict['ETA'] = issue['fields']['duedate'] if issue['fields']['duedate'] is not None else "N/A"
        issue_dict['Left Days'] = "{:.2f}".format(hourstimediff("{} 0:0:0".format(issue_dict['ETA']), now)) if issue_dict['ETA'] != "N/A" else "N/A"
        issue_dict['Jira Status'] = issue['fields']['status']['name']
        issue_dict['Affected Version'] = issue['fields']['versions'][0]['name'] if issue['fields']['versions'] else "N/A"
        issue_list.append(issue_dict)

    # coding=UTF-8
    today = datetime.datetime.now().strftime('%Y-%m-%d')
    filename = f"LPOT_jira_status_of_{today}.csv"
    header = ["Jira ID", "Jira Link", "Issue Type", "Task", "Owner", "Priority", "Labels", "ETA", "Left Days", "Jira Status", "Affected Version"]
    header_line = ",".join(header)
    csv_content = [header_line]
    for item in issue_list:
        line = ",".join([item['Jira ID'], item['Jira Link'], item['Issue Type'], item['Task'], item['Owner'], item['Priority'], item['Labels'], item['ETA'], item['Left Days'], item['Jira Status'], item['Affected Version']])
        csv_content.append(line)

    with open(filename, 'w') as file:
        for line in csv_content:
            file.write(line + "\n")

    print(f"Report successfully saved to {filename}")


def hourstimediff(new, old):    
    newdate = datetime.datetime.strptime(new, "%Y-%m-%d %H:%M:%S")
    olddate = datetime.datetime.strptime(old, "%Y-%m-%d %H:%M:%S")
    seconds = (newdate - olddate).seconds
    total_s = (newdate - olddate).total_seconds()
    days = float(total_s / 3600 /24)
    return days

def parse_arguments():
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", type=str, default="INC")
    parser.add_argument("--version", type=str, default="ALL")
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_arguments()
    main(
        project=args.project,
        version=args.version,
    )
