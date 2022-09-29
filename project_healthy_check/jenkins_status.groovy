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

issue_type="Bug,Feature,Task"
if ('python_version' in params && params.issue_type != ''){
    issue_type=params.issue_type
}
echo "issue_type: ${issue_type}"

inc_version="1.14.1"
if ('inc_version' in params && params.inc_version != ''){
    inc_version=params.inc_version
}
echo "inc_version: ${inc_version}"

days="365"
if ('days' in params && params.days != ''){
    days=params.days
}
echo "days: ${days}"

jenkins_job_name='project_healthy_check'
if ('jenkins_job_name' in params && params.jenkins_job_name != ''){
    jenkins_job_name=params.jenkins_job_name
}
echo "jenkins_job_name: ${jenkins_job_name}"

project="INC"
if ('project' in params && params.project != ''){
    project=params.project
}
echo "project: ${project}"

pr_repo_name="frameworks.ai.lpot.lpot-validation"
if ('pr_repo_name' in params && params.pr_repo_name != ''){
    pr_repo_name=params.pr_repo_name
}
echo "pr_repo_name: ${pr_repo_name}"


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
            '''
        }
    }
}

// @NonCPS
def checkJenkinsStatus(){
    def jobNames = "${jenkins_job_name}".split(',')
    def daysBack = "${days}".toLong()  // adjust to how many days back to report on
    def timeToDays = 24*60*60*1000  // converts msec to days
    def filePath = "${WORKSPACE}/logfile/trigger_data.txt"
    def triggerMsg = ""
    
    println "Job Name: ( # builds: last ${daysBack} days / overall )  Last Status\n   Number | Trigger | Status | Date | Duration\n" 

    for (jobName in jobNames){
        def job = Jenkins.instance.getItemByFullName(jobName)
        def builds = job.getBuilds().byTimestamp(System.currentTimeMillis() - daysBack*timeToDays, System.currentTimeMillis())

        println job.fullName + ' ( ' + builds.size() + ' / ' + job.builds.size() + ' )  ' + job.getLastBuild()?.result

        for (build in builds){
            println jobName + '   ' + build.number + ' | ' + build.getCauses()[0].getShortDescription() + ' | ' + build.result + ' | ' + build.getTimestampString2() + ' | ' + build.getDurationString()
            triggerMsg = triggerMsg + "${jobName}|${build.number.toString()}|${build.getCauses()[0].getShortDescription().toString()}|${build.result.toString()}|${build.getTimestampString2().toString()}|${build.getDurationString().toString()}\n"
        }        
    }

    writeFile file: filePath, text: triggerMsg

    return 0
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

        stage('get jenkins job'){
            try{
                checkJenkinsStatus()
            }catch(e){
                println e
            }
        }

        stage('healthy check'){
            run_healthy_check_scripts = "${WORKSPACE}/lpot-validation/project_healthy_check/pre-ci_trigger_frequency_trend.py"
            println("run healthy check...")
            withEnv(["run_healthy_check_scripts=${run_healthy_check_scripts}", "conda_env=${conda_env}"]){
                    sh'''#!/bin/bash
                    export PATH=${HOME}/miniconda3/bin/:$PATH
                    source activate ${conda_env}

                    mkdir logfile
                    cd ${WORKSPACE}/lpot-validation/project_healthy_check
                    
                    if [ -f "${run_healthy_check_scripts}" ]; then
                        python ${run_healthy_check_scripts} --logfile_path ${WORKSPACE}/logfile --type_list ${issue_type} --version ${inc_version} --jenkins_data_path ${WORKSPACE}/logfile/trigger_data.txt --days ${days} --project ${project} --jenkins_job_name ${jenkins_job_name} --pr_repo_name ${pr_repo_name}
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