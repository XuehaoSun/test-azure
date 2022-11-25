credential = 'c09d6555-5e41-4b99-bf90-50f518319b49'
try{ echo "BRANCH=${BRANCH}"; } catch (Exception e) { BRANCH="master" ; echo "BRANCH=${BRANCH}" }
try{ echo "python_version=${python_version}"; } catch (Exception e) { python_version="3.6" ; echo "python_version=${python_version}" }

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

node(NODE_NAME) {

    stage("Clone validation repository") {
        dir('lpot-validation') {
            retry(5) {
                checkout scm
            }
        }
    }

    stage("Get builds summary") {
        copyArtifacts(
            projectName: JOB_NAME,
            selector: specific("${BUILD_NUMBER}"),
            filter: 'summary.log, tuning_info.log, fw_versions.json',
            fingerprintArtifacts: true,
            target: "${WORKSPACE}/logs")
    }

    stage("Generate excel report") {
        withEnv(["CPU_NAME=${CPU_NAME}"]){
            sh '''#!/bin/bash
            set -xe

            export PATH=${HOME}/miniconda3/bin/:$PATH
            conda_env_name="report_generator"
            if [[ -n ${CPU_NAME} ]]; then
                conda_env_name="${conda_env_name}-${CPU_NAME}"
            fi
            if [ $(conda info -e | grep ${conda_env_name} | wc -l) == 0 ]; then
                conda create python=${python_version} -y -n ${conda_env_name}
            fi

            source activate ${conda_env_name}

            pip install -U pip

            pip install -r ./lpot-validation/scripts/report_generator/requirements.txt
            python ./lpot-validation/scripts/report_generator/generate_excel_report.py \
                --tuning-log=${WORKSPACE}/logs/tuning_info.log \
                --summary-log=${WORKSPACE}/logs/summary.log
            '''
        }
    }

    stage("Send report") {
            if (REPORT_RECIPIENTS.size() <= 0) {
                print("Report recipients not specified.")
            } else {
                emailext subject: "lpot excel report",
                to: "${REPORT_RECIPIENTS}",
                body: "Excel report for <a href='${JENKINS_URL}/job/${JOB_NAME}/${BUILD_NUMBER}'>${JOB_NAME} #${BUILD_NUMBER}</a> has been attached.",
                attachmentsPattern: "lpot_report.xlsx",
                mimeType: 'text/html'
            }
        }

    stage("Archive artifacts") {
        archiveArtifacts artifacts: 'logs/*.log, **/*.xlsx', excludes: null, allowEmptyArchive: true
    }
}
