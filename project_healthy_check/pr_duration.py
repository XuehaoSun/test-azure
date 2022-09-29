import requests
import json

def duration(current_page, state_params, pr_repo_name):

    proxies = {
        'http': 'http://child-prc.intel.com:913',
        'https': 'http://child-prc.intel.com:913'
    }

    pr_url = 'https://api.github.com/repos/intel-innersource/{}/pulls'.format(pr_repo_name)
    pr_headers = {
        'Authorization': 'token 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',
        'Accept': 'application/vnd.github.v3+json'
    }
    file_list = []

    for state_param in state_params:
        params = {
        'state'   : state_param, 
        'per_page': 25,
        'page'    : current_page
        }
        
        file_list.append('pr_{}_duration_{}.json'.format(state_param, current_page))

        requests.packages.urllib3.disable_warnings()
        r = requests.get(pr_url, headers=pr_headers, params=params, proxies=proxies, verify = False)
        pr = r.json()
        js = json.dumps(pr, sort_keys=True, indent=4, separators=(',', ':'))

        f = open('pr_{}_duration_{}.json'.format(state_param, current_page), 'w')
        f.write(js)
        f.close()

    # return file_name_list
    return file_list

if __name__ == "__main__":
    duration()