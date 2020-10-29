credential = "5da0b320-00b8-4312-b653-36d4cf980fcb"

node_label = "clx8280"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

ilit_url="https://gitlab.devtools.intel.com/chuanqiw/auto-tuning.git"
if ('ilit_url' in params && params.ilit_url != ''){
    ilit_url = params.ilit_url
}

echo "ilit_url is ${ilit_url}"

python_version="3.6"
if ('python_version' in params && params.python_version != ''){
    python_version = params.python_version
}
echo "python_version is ${python_version}"

echo "nigthly_test_branch: $nigthly_test_branch"
echo "MR_source_branch: $MR_source_branch"
echo "MR_target_branch: $MR_target_branch"

echo "TOOL is ${TOOL}"

def cleanup() {
    stage("Cleanup") {
        try {
        sh '''#!/bin/bash -x
            cd $WORKSPACE
            sudo rm -rf *
            git config --global user.email "lab_tfbot@intel.com"
            git config --global user.name "lab_tfbot"
        '''
        } catch(e) {
            echo "==============================================="
            echo "ERROR: Exception caught in cleanup()"
            echo "ERROR: ${e}"
            echo "==============================================="

            echo ' '
            echo "Error while doing cleanup"
        }
    }
}

def download() {
    stage("Download") {
        dir(WORKSPACE) {
            retry(5) {
                checkout scm
                ilit_branch = nigthly_test_branch
                if (MR_source_branch != "") {
                    ilit_branch = MR_source_branch
                }
                checkout changelog: true, poll: true, scm: [
                    $class: 'GitSCM',
                    branches: [[name: "${ilit_branch}"]],
                    browser: [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions : [
                        [$class: 'RelativeTargetDirectory', relativeTargetDir: "iLit"],
                        [$class: 'CloneOption', timeout: 60]
                    ],
                    submoduleCfg: [],
                    userRemoteConfigs: [
                        [credentialsId: "${credential}",
                        url: "${ilit_url}"]
                    ]
                ]
            }
        }
    }
}

def create_conda_env() {
    stage("Create conda env") {
        withEnv(["python_version=${python_version}"]) {
            retry(5) {
                sh '''#!/bin/bash
                    set -xe
                    export PATH=${HOME}/miniconda3/bin/:$PATH
                    conda_env_name=ilit-format_scan-${python_version}
                    if [ $(conda info -e | grep ${conda_env_name} | wc -l) == 0 ]; then
                        echo "${conda_env} exist!"
                    fi

                    conda create python=${python_version} -y -n ${conda_env_name}

                    source activate ${conda_env_name}

                    wait

                    echo "pip list all the components------------->"
                    pip list
                    sleep 2
                    echo "------------------------------------------"
                '''
            }
        }
    }
}

node(node_label) {
    try {
        cleanup()
        download()
        create_conda_env()
        stage("Code Scan") {
            echo "---------------------------------------------------------"
            echo "-----------------  Running Code Scan  -----------------"
            echo "---------------------------------------------------------"
            status = sh(
            script: "${WORKSPACE}/scripts/run_format_scan.sh --python_version=${python_version} --tool=${TOOL} --repo_dir=${WORKSPACE}/iLit",  // There is no source branch as script assumes that it is currently on MR branch; look at download funtion.
            returnStatus:true)
            if (status != 0) {
                throw new Exception("Found code format scan errors.")
            }
        }

    } catch(e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.json,*.log', excludes: null, allowEmptyArchive: true
            fingerprint: true
        }
    }
}
