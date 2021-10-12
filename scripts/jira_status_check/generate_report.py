import argparse
from argparse import Namespace
import csv
from pyhtml import *
import datetime
from typing import Dict, List, Optional
from operator import itemgetter as ig
from functools import cmp_to_key

from typing import List
from utils import parse_priority

UNRESOLVED_STATUSES = ["New", "In Progress", "Open", "Assigned", "Deferred"]
RESOLVED_STATUSES = ["Closed", "Done", "Resolved", "Implemented"]
class JiraIssue:
    """Jira Issue interface."""

    def __init__(self, data: dict) -> None:
        """Initialize JIRA issue object."""
        self.jira_id = data.get("jira id")
        self.jira_link = data.get("jira link")
        self.jira_status = data.get("jira status")
        self.issue_type = data.get("issue type")
        self.task = data.get("task")
        self.owner = data.get("owner")
        self.priority = data.get("priority")
        self.labels = []
        if data.get("labels"):
            self.labels = data.get("labels").split(";")
        self.eta = data.get("eta")
        self.left_days = data.get("left days")
        self.affected_version = data.get("affected version")
        self.pr = data.get("pr")
        self.pr_link = data.get("pr link")
        self.pre_ci = data.get("pre-ci")
        self.pending_days = data.get("pending days")

    def __getitem__(self,key):
        return getattr(self, key)

    @property
    def remaining_time(self) -> bool:
        """Check remaining time in days based on ETA."""
        if self.eta and self.eta != "N/A":
            eta_date = datetime.datetime.strptime(self.eta, '%Y-%m-%d')
            current_date = datetime.datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
            diff = eta_date - current_date
            return diff.days
        return None

class Issues:
    """Jira issues aggregator."""

    def __init__(self, issues: List[JiraIssue] = []):
        self._issues: List[JiraIssue] = issues

    @property
    def issues(self) -> List[JiraIssue]:
        """Get jira issues."""
        return self._issues

    def sort_issues(self, sort_keys: List[str]) -> None:
        """Sort issues by specified keys."""
        comparers = [
            ((ig(col[1:].strip()), -1) if col.startswith('-') else (ig(col.strip()), 1))
            for col in sort_keys
        ]
        def comparer(left, right):
            comparer_iter = (
                cmp(fn(left), fn(right)) * mult
                for fn, mult in comparers
            )
            return next((result for result in comparer_iter if result), 0)
        self._issues = sorted(self.issues, key=cmp_to_key(comparer))

    def add_issue(self, issue: JiraIssue) -> None:
        if isinstance(issue, JiraIssue) and self.get_issue_by_id(issue.jira_id) is None:
            self._issues.append(issue)
        else:
            print("Found duplicated issue id!")

    def get_issue_by_id(self, jira_id: str) -> Optional[JiraIssue]:
        """Search through aggregated issues for issue with specified id. If not found return None."""
        for issue in self.issues:
            if issue.jira_id == jira_id:
                return issue
        return None

    def get_issue_by_type(self, issue_types: List[str]) -> List[JiraIssue]:
        """Search through aggregated issues for issues with specified type."""
        issues: List[JiraIssue] = []
        for issue in self.issues:
            if issue.issue_type in issue_types:
                issues.append(issue)
        return issues

    def get_issue_by_priority(self, issue_priorities: List[str]) -> List[JiraIssue]:
        """Search through aggregated issues for issues with specified priority."""
        issues: List[JiraIssue] = []
        parsed_priorities = []
        for priority in issue_priorities:
            parsed_priorities.append(parse_priority(priority))
        for issue in self.issues:
            if issue.priority in parsed_priorities:
                issues.append(issue)
        return issues

    def get_issue_by_labels(self, issue_labels: Dict[str, List[str]]) -> List[JiraIssue]:
        """Search through aggregated issues for issues with specified labels."""
        issues: List[JiraIssue] = []
        include_labels = issue_labels.get("include", [])
        exclude_labels = issue_labels.get("exclude", [])
        for issue in self.issues:
            if (
                (set(include_labels).issubset(set(issue.labels))) and
                not (exclude_labels and set(exclude_labels).issubset(set(issue.labels)))
            ):
                issues.append(issue)
        return issues

    def get_issue_by_status(self, issue_statuses: List[str]) -> List[JiraIssue]:
        """Search through aggregated issues for issues with specified statuses."""
        issues: List[JiraIssue] = []
        for issue in self.issues:
            if issue.jira_status in issue_statuses:
                issues.append(issue)
        return issues


def parse_arguments() -> Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", type=str, required=True, help="Path to csv with issues summary.")
    parser.add_argument("--affected_version", type=str, default="ALL")
    return parser.parse_args()

def main(args: Namespace) -> None:
    """Generate report."""
    jira_issues: Issues = Issues()
    with open(args.csv, newline="") as summary_file:
        header = [item.strip() for item in summary_file.readline().lower().split(",")]
        reader = csv.DictReader(summary_file, fieldnames=header, delimiter=",")
        for row in reader:
            jira_issues.add_issue(JiraIssue(row))

    jira_issues.sort_issues(["owner", "priority"])

    customer_issues = jira_issues.get_issue_by_labels({"include": ["customer"]})
    customer_issues_unresolved = Issues(customer_issues).get_issue_by_status(UNRESOLVED_STATUSES)
    customer_issues_resolved = Issues(customer_issues).get_issue_by_status(RESOLVED_STATUSES)
    customer_issues_table = create_table(customer_issues_unresolved)

    non_customer_issues = jira_issues.get_issue_by_labels({"exclude": ["customer"]})

    feature_issues = Issues(non_customer_issues).get_issue_by_type(["Feature", "Sub-Feature"])

    p1_feature_issues = Issues(feature_issues).get_issue_by_priority(["P1"])
    p1_feature_issues_unresolved = Issues(p1_feature_issues).get_issue_by_status(UNRESOLVED_STATUSES)
    p1_feature_issues_resolved = Issues(p1_feature_issues).get_issue_by_status(RESOLVED_STATUSES)
    p1_feature_issues_table = create_table(p1_feature_issues_unresolved)

    other_feature_issues = Issues(feature_issues).get_issue_by_priority(["P2", "P3", "P4"])
    other_feature_issues_unresolved = Issues(other_feature_issues).get_issue_by_status(UNRESOLVED_STATUSES)
    other_feature_issues_resolved = Issues(other_feature_issues).get_issue_by_status(RESOLVED_STATUSES)
    other_feature_issues_table = create_table(other_feature_issues_unresolved)

    bugs_issues = Issues(non_customer_issues).get_issue_by_type(["Bug"])
    bugs_issues_unresolved = Issues(bugs_issues).get_issue_by_status(UNRESOLVED_STATUSES)
    bugs_issues_resolved = Issues(bugs_issues).get_issue_by_status(RESOLVED_STATUSES)
    bugs_table = create_table(bugs_issues_unresolved)

    html_title = "INC JIRA status summary"
    if args.affected_version != "" and args.affected_version != "ALL":
        html_title = f"INC v{args.affected_version} JIRA status summary"


    # Initialize sections
    p1_features_section = (
        h3("Features P1:"),
        h5("None")
    )
    other_features_section = (
        h3("Other features:"),
        h5("None")
    )
    bugs_section = (
        h3("Bugs:"),
        h5("None")
    )
    customer_section = (
        h3("Customer:"),
        h5("None")
    )

    if len(p1_feature_issues) > 0:
        p1_features_section = (
            h3("Features P1:"),
            h5(f"Progress: {round(100*len(p1_feature_issues_resolved)/len(p1_feature_issues))}% [ Done tasks {len(p1_feature_issues_resolved)}/{len(p1_feature_issues)} ]"),
            p1_feature_issues_table,
            br()
        )

    if len(other_feature_issues) > 0:
        other_features_section = (
            h3("Other features:"),
            h5(f"Progress: {round(100*len(other_feature_issues_resolved)/len(other_feature_issues))}% [ Done tasks {len(other_feature_issues_resolved)}/{len(other_feature_issues)} ]"),
            other_feature_issues_table,
            br()
        )

    
    if len(bugs_issues) > 0:
        bugs_section = (
            h3("Bugs:"),
            h5(f"Progress: {round(100*len(bugs_issues_resolved)/len(bugs_issues))}% [ Done tasks {len(bugs_issues_resolved)}/{len(bugs_issues)} ]"),
            bugs_table,
            br(),
        )
    if len(customer_issues) > 0:
        customer_section = (
            h3("Customer:"),
            h5(f"Progress: {round(100*len(customer_issues_resolved)/len(customer_issues))}% [ Done tasks {len(customer_issues_resolved)}/{len(customer_issues)} ]"),
            customer_issues_table,
        )

    
    report = html(
        head(
            title(html_title),
            style("""
        table {
            border-collapse: collapse;
        }
        th {
            text-align: center;
            background-color: #003C71;
            color: white;
            padding: 3px;
            border: solid 2px white;
        }
        td {
            background-color: #f7f8f9;
            font-size: 0.7em;
            padding: 10px;
            border: solid 2px white;
        }
        tr:hover td {
            background-color: #edeff0 !important;
        }
        """)
        ),
        body(
            h1(align="center")(html_title),
            h2(align="center")(datetime.datetime.now().strftime('%Y-%m-%d')),
            br(),
            p1_features_section,
            other_features_section,
            bugs_section,
            customer_section,
        )
    )

    with open("jira_status_report.html", "w") as f:
        f.write(report.render())

def create_table(issues: Dict[str, JiraIssue]):
    table_header = ["Jira ID", "Task", "Owner", "Priority ", "ETA", "Jira status", "PR", "Pending Days"]
    table_header_row = [th(item) for item in table_header]
    issue_entries = [issue_to_table_row(issue) for issue in issues]
    return table(
                thead(
                    tr(
                        table_header_row
                    )
                ),
                tbody(
                    issue_entries
                )
            )

def issue_to_table_row(issue: JiraIssue):
    """Create pyhtml entry for issue."""
    eta_style = get_eta_style(issue)
    pending_days_style = get_pending_days_style(issue)
    pr_cell = issue.pr_link
    if issue.pr_link not in ["", "N/A"]:
        pr_cell = a(href=issue.pr_link, style="color: inherit;background-color: inherit;")(issue.pr)
    return tr(
        td(a(href=issue.jira_link, style="color: inherit;background-color: inherit;")(issue.jira_id)),
        td(a(href=issue.jira_link, style="color: inherit;background-color: inherit;")(issue.task)),
        td(issue.owner),
        td(issue.priority),
        td(style=eta_style)(issue.eta),
        td(issue.jira_status),
        td(pr_cell),
        td(style=pending_days_style)(issue.pending_days)
    )

def get_eta_style(issue: JiraIssue) -> str:
    """Get style for ETA cell."""
    if issue.remaining_time is None:
        return ""
    if 0 < issue.remaining_time <= 3:
        return "background-color: #fff2d0; font-weight: bold"
    if issue.remaining_time < 0:
        return "color: #931c1a; background-color: #ffe1dd; font-weight: bold"
    return ""


def get_pending_days_style(issue: JiraIssue) -> str:
    """Get style for pending days cell."""
    if issue.pending_days is None or issue.pending_days == "N/A":
        return ""
    if float(issue.pending_days) >= 3:
        return "background-color: #fff2d0; font-weight: bold"
    return ""


def cmp(x, y):
    return (x > y) - (x < y)


if __name__ == "__main__":
    args = parse_arguments()
    main(args)
