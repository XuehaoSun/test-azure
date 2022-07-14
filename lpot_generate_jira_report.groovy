try{ echo "PROJECT=${PROJECT}"; } catch (Exception e) { PROJECT="P1" ; echo "PROJECT=${PROJECT}" }
try{ echo "VERSION=${VERSION}"; } catch (Exception e) { VERSION="ALL" ; echo "VERSION=${VERSION}" }


node(LABEL) {

    stage("Clone validation repository") {
        dir('lpot-validation') {
            retry(5) {
                checkout scm
            }
        }
    }

    stage("Generate jira report") {
        withEnv([
            "VERSION=${VERSION}",
            "PROJECT=${PROJECT}",
            "CPU_NAME=${CPU_NAME}"
        ]) {
            sh '''#!/bin/bash
                set -xe

                export PATH=${HOME}/miniconda3/bin/:$PATH
                conda_env_name="jira_status_check"
                if [[ -n ${CPU_NAME} ]]; then
                    conda_env_name="${conda_env_name}-${CPU_NAME}"
                fi
                if [ $(conda info -e | grep ${conda_env_name} | wc -l) != 0 ]; then
                    (conda remove --name ${conda_env_name} --all -y) || true

                    conda_dir=$(dirname $(dirname $(which conda)))
                    if [ -d ${conda_dir}/envs/${conda_env_name} ]; then
                        rm -rf ${conda_dir}/envs/${conda_env_name}
                    fi
                fi

                conda create python=3.8 -y -n ${conda_env_name}

                source activate ${conda_env_name}

                pip install -U pip

                pushd ./lpot-validation/scripts/jira_status_check
                pip install -r requirements.txt
                python collect_issues_data.py --project=${PROJECT} --version=${VERSION}
                csv_report=$(ls -t LPOT_jira_status_of_*.csv | head -n 1)
                python generate_report.py --csv=${csv_report} --version=${VERSION} --project=${PROJECT}
                popd
                cp ./lpot-validation/scripts/jira_status_check/jira_status_report.html .
            '''
        }
    }

    stage("Send report") {
            if (REPORT_RECIPIENTS.size() <= 0) {
                print("Report recipients not specified.")
            } else {
                emailext subject: "${PROJECT} BUGs status report",
                to: "${REPORT_RECIPIENTS}",
                body: '''${FILE,path="jira_status_report.html"}''',
                mimeType: 'text/html'
            }
        }

    stage("Archive artifacts") {
        archiveArtifacts artifacts: '*.html', excludes: null, allowEmptyArchive: true
    }
}

