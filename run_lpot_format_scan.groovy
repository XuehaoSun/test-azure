credential = "c09d6555-5e41-4b99-bf90-50f518319b49"

node_label = "clx8280"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

lpot_url="https://gitlab.devtools.intel.com/chuanqiw/auto-tuning.git"
if ('lpot_url' in params && params.lpot_url != ''){
    lpot_url = params.lpot_url
}

echo "lpot_url is ${lpot_url}"

python_version="3.6"
if ('python_version' in params && params.python_version != ''){
    python_version = params.python_version
}
echo "python_version is ${python_version}"

val_branch="master"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

echo "lpot_branch: $lpot_branch"
echo "MR_source_branch: $MR_source_branch"
echo "MR_target_branch: $MR_target_branch"

echo "TOOL is ${TOOL}"

def cleanup() {
    stage("Cleanup") {
        try {
        sh '''#!/bin/bash -x
            cd $WORKSPACE
            sudo rm -rf *
            sudo rm -rf .git
            git config --global user.email "sys_lpot_val@intel.com"
            git config --global user.name "sys-lpot-val"
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
                lpot_branch = lpot_branch
                if (MR_source_branch != "") {
                    lpot_branch = MR_source_branch
                }
                checkout changelog: true, poll: true, scm: [
                    $class: 'GitSCM',
                    branches: [[name: "${lpot_branch}"]],
                    browser: [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions : [
                        [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot"],
                        [$class: 'CloneOption', timeout: 10]
                    ],
                    submoduleCfg: [],
                    userRemoteConfigs: [
                        [credentialsId: "${credential}",
                        url: "${lpot_url}"]
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
                    conda_env_name=lpot-format_scan-${python_version}
                    if [ $(conda info -e | grep ${conda_env_name} | wc -l) != 0 ]; then
                        echo "${conda_env_name} exist!"
                    else
                        conda create python=${python_version} -y -n ${conda_env_name}
                    fi

                    source activate ${conda_env_name}

                    wait

                    # Upgrade pip
                    pip install -U pip

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
            script: "${WORKSPACE}/scripts/run_format_scan.sh --python_version=${python_version} --tool=${TOOL} --repo_dir=${WORKSPACE}/lpot",  // There is no source branch as script assumes that it is currently on MR branch; look at download function.
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
            archiveArtifacts artifacts: '*.json,*.log,*.csv', excludes: null, allowEmptyArchive: true
            fingerprint: true
        }
    }
}
