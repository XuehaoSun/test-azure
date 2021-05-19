import re
import datetime
import json
import requests
import sys
import argparse


def main(version: str, priority: str):
    now = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    filename = "log"
    issue_list = []
    issue_dict = {}

    jira_api_url = "https://jira.devtools.intel.com/rest/api/2/search"
    jql = f"project = ILITV AND issuetype in (Feature, Bug, Sub-Feature) AND status in (New, \"In Progress\", Open, Assigned, Deferred, Implemented) AND priority in ({parse_priority(priority)})"
    if version != "ALL":
        jql += f" AND affectedVersion = {version}"
    jira_request_data = {
        "jql": jql,
        "maxResults": 500,
        "fields": [
            "summary",
            "status",
            "assignee",
            "priority",
            "labels",
            "duedate",
            "versions",
            "issuetype"
        ]
    }
    jira_request_headers = {
        "Authorization": "Basic d2xpYW8yOldpbmNlbnQ4MyQ=",
        "Accept": "application/json",
        "Content-Type": "application/json",
    }
    req = requests.post(url=jira_api_url, json=jira_request_data, headers=jira_request_headers, verify=False)
    jira_data = req.json()

    for issue in jira_data["issues"]:
        issue_dict = {}
        issue_dict['Jira ID'] = issue['key']
        issue_dict['Jira Link'] = "https://jira.devtools.intel.com/browse/{}".format(issue['key'])
        issue_dict['Issue Type'] = issue['fields']['issuetype']['name']
        issue_dict['Task'] = issue['fields']['summary'].replace(",", " ")
        issue_dict['Owner'] = issue['fields']['assignee']['displayName']. replace(",", " ") if issue['fields']['assignee'] is not None else "N/A"
        issue_dict['Priority'] = issue['fields']['priority']['name']
        issue_dict['ETA'] = issue['fields']['duedate'] if issue['fields']['duedate'] is not None else "N/A"
        issue_dict['Left Days'] = "{:.2f}".format(hourstimediff("{} 0:0:0".format(issue_dict['ETA']), now)) if issue_dict['ETA'] != "N/A" else "N/A"
        issue_dict['Jira Status'] = issue['fields']['status']['name']
        issue_dict['Affected Version'] = issue['fields']['versions'][0]['name'] if issue['fields']['versions'] else "N/A"
        match = 0
        for label in issue['fields']['labels']:
            if "PR-" in label:
                match = 1
                issue_dict['PR'] = label
                issue_dict['PR Link'] = label.replace("PR-", "https://github.com/intel-innersource/frameworks.ai.lpot.intel-lpot/pull/")
                # begin to get github pr info
                url = label.replace("PR-", 'https://api.github.com/repos/intel-innersource/frameworks.ai.lpot.intel-lpot/pulls/')
                headers = {'Authorization': 'token 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',
                        'Accept': 'application/vnd.github.v3+json'}
                r = requests.get(url, headers=headers)
                pr = r.json()
                if pr["state"] == "open":
                    issue_dict['Pending Days'] = "{:.2f}".format(
                        hourstimediff(now, pr["created_at"].replace("T", " ").replace("Z", "")))
                    issue_dict['Pre-ci'] = "N/A"
                else:
                    issue_dict['Pending Days'] = "N/A"
                    issue_dict['Pre-ci'] = "N/A"
                break
        if match == 0:
            issue_dict['PR'] = "N/A"
            issue_dict['PR Link'] = "N/A"
            issue_dict['Pending Days'] = "N/A"
            issue_dict['Pre-ci'] = "N/A"
        issue_list.append(issue_dict)

    # coding=UTF-8
    today = datetime.datetime.now().strftime('%Y-%m-%d')
    filename = f"LPOT_jira_status_of_{today}.csv"
    header = ["Jira ID", "Jira Link", "Issue Type", "Task", "Owner", "Priority", "ETA", "Left Days", "Jira Status", "Affected Version", "PR", "PR Link", "Pending Days", "Pre-ci"]
    header_line = ",".join(header)
    csv_content = [header_line]
    for item in issue_list:
        line = ",".join([item['Jira ID'], item['Jira Link'], item['Issue Type'], item['Task'], item['Owner'], item['Priority'], item['ETA'], item['Left Days'], item['Jira Status'], item['Affected Version'], item['PR'], item['PR Link'], item['Pending Days'], item['Pre-ci']])
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
    parser.add_argument("--priority", type=str, default="P1")
    parser.add_argument("--affected_version", type=str, default="ALL")
    return parser.parse_args()


def parse_priority(priority: str):
    mapped_priorities = []
    priority_map = {
        "P1": "P1-Stopper",
        "P2": "P2-High",
        "P3": "P3-Medium",
        "P4": "P4-Low"
    }
    priorities = priority.split(",")
    for prior in priorities:
        mapped_priority = priority_map.get(prior, None)
        if mapped_priority is None:
            raise Exception(f"Priority {prior} not recognized. Use one of following: {priority_map.keys()}")
        mapped_priorities.append(mapped_priority)
    return ",".join(mapped_priorities)


if __name__ == "__main__":
    args = parse_arguments()
    main(
        version=args.affected_version,
        priority=args.priority,
    )
