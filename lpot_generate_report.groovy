credential = 'lab_tfbot'
try{ echo "BRANCH=${BRANCH}"; } catch (Exception e) { BRANCH="master" ; echo "BRANCH=${BRANCH}" }
try{ echo "python_version=${python_version}"; } catch (Exception e) { python_version="3.6" ; echo "python_version=${python_version}" }
try{ echo "tensorflow_version=${tensorflow_version}"; } catch (Exception e) { tensorflow_version="" ; echo "Could not found TensorFlow version." }
try{ echo "mxnet_version=${mxnet_version}"; } catch (Exception e) { mxnet_version="" ; echo "Could not found MXNet TensorFlow version." }
try{ echo "pytorch_version=${pytorch_version}"; } catch (Exception e) { pytorch_version="" ; echo "Could not found PyTorch version." }

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
        try {
            fw_versions = jsonParse(readFile("$WORKSPACE/logs/fw_versions.json"))
            tensorflow_version = fw_versions."tensorflow"
            mxnet_version = fw_versions."mxnet"
            pytorch_version = fw_versions."pytorch"
            onnxruntime_version = fw_versions."onnxruntime"
        } catch (Exception e) {
            print("Could not load framework versions.")
        }
    }

    stage("Generate excel report") {
        withEnv([
            "tensorflow_version=${tensorflow_version}",
            "mxnet_version=${mxnet_version}",
            "pytorch_version=${pytorch_version}",
            "onnxruntime_version=${onnxruntime_version}"
        ]) {
            sh '''#!/bin/bash
                set -xe
    
                export PATH=${HOME}/miniconda3/bin/:$PATH
                conda_env_name="report_generator"
                if [ $(conda info -e | grep ${conda_env_name} | wc -l) != 0 ]; then
                    conda remove --name ${conda_env_name} --all -y
    
                    conda_dir=$(dirname $(dirname $(which conda)))
                    if [ -d ${conda_dir}/envs/${conda_env_name} ]; then
                        rm -rf ${conda_dir}/envs/${conda_env_name}
                    fi
                fi
    
                conda create python=${python_version} -y -n ${conda_env_name}
    
                source activate ${conda_env_name}
    
                pip install -U pip
    
                pip install -r ./lpot-validation/scripts/report_generator/requirements.txt
                python ./lpot-validation/scripts/report_generator/generate_excel_report.py \
                    --tuning-log=${WORKSPACE}/logs/tuning_info.log \
                    --summary-log=${WORKSPACE}/logs/summary.log \
                    --tensorflow-version=${tensorflow_version} \
                    --mxnet-version=${mxnet_version} \
                    --pytorch-version=${pytorch_version} \
                    --onnxruntime-version=${onnxruntime_version}
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
