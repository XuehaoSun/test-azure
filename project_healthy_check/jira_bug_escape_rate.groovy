import groovy.json.*
import hudson.model.*
import jenkins.model.*
import hudson.triggers.* 


node_label = "inteltf-clx6248-306.sh.intel.com"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

conda_env = "project_healthy_check"
if ('conda_env' in params && params.conda_env != '') {
    conda_env = params.conda_env
}
echo "Running project_healthy_check on ${conda_env}"

val_branch="master"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

python_version="3.8"
if ('python_version' in params && params.python_version != ''){
    python_version=params.python_version
}
echo "python_version: ${python_version}"

start_days="2022-09-01"
if ('start_days' in params && params.start_days != ''){
    start_days=params.start_days
}
echo "start_days: ${start_days}"

project="INC"
if ('project' in params && params.project != ''){
    project=params.project
}
echo "project: ${project}"

def cleanup() {

    try {
        sh '''#!/bin/bash -x
        cd $WORKSPACE
        rm -rf *
        rm -rf .git
        sudo rm -rf *
        sudo rm -rf .git
        git config --global user.email "sys_lpot_val@intel.com"
        git config --global user.name "sys-lpot-val"
        '''
    } catch(e) {
        echo "==============================================="
        echo "ERROR: Exception caught in cleanup()           "
        echo "ERROR: ${e}"
        echo "==============================================="

        echo ' '
        echo "Error while doing cleanup"
    }

}

def download() {
    dir(WORKSPACE) {
        retry(5) {
            dir('lpot-validation') {
                checkout scm
            }
        }
    }
}

def build_conda_env() {
    if ("${python_version}" != ""){
        conda_env="${conda_env}-${python_version}"
    }
    println("full conda_env_name = " + conda_env)
    withEnv(["conda_env_name=${conda_env}",
             "python_version=${python_version}"]) {
        retry(3) {
            sh'''#!/bin/bash
                set -xe
                echo "Create new conda env for ..."
                if [ $(conda info -e | grep ${conda_env_name} | wc -l) != 0 ]; then
                    (conda remove --name ${conda_env_name} --all -y) || true
                fi
                conda_dir=$(dirname $(dirname $(which conda)))
                if [ -d ${conda_dir}/envs/${conda_env_name} ]; then
                    rm -rf ${conda_dir}/envs/${conda_env_name}
                fi
                conda config --add channels defaults
                conda create python=${python_version} -y -n ${conda_env_name}

                source activate ${conda_env_name}

                # Upgrade pip
                pip install -U pip
                pip install xlsxwriter
                pip install requests
                pip install python-dateutil
            '''
        }
    }
}


node(node_label){
    try {
        cleanup()
        stage('download') {
            download()
        }

        stage('env_build') {
            build_conda_env()
            println "now conda env is " + conda_env
        }

        stage('analyse bug escape rate'){
            run_jira_bug_escape_rate_scripts = "${WORKSPACE}/lpot-validation/project_healthy_check/jira_bug_escape_rate.py"
            println("run healthy check...")
            withEnv(["run_jira_bug_escape_rate_scripts=${run_jira_bug_escape_rate_scripts}", "conda_env=${conda_env}"]){
                    sh'''#!/bin/bash
                    export PATH=${HOME}/miniconda3/bin/:$PATH
                    source activate ${conda_env}

                    mkdir logfile
                    cd ${WORKSPACE}/lpot-validation/project_healthy_check
                    
                    if [ -f "${run_jira_bug_escape_rate_scripts}" ]; then
                        python ${run_jira_bug_escape_rate_scripts} --logfile_path=${WORKSPACE}/logfile --start_days=${start_days} --project=${project}
                    fi
                '''
            }
        }
    } catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: 'logfile/*.xlsx', excludes: null
            fingerprint: true
        }
    }
}