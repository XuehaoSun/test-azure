@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

node_label = "clx8280"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

conda_env = "HOSTNAME"
if ('conda_env' in params && params.conda_env != '') {
    conda_env = params.conda_env
}
echo "Running ut on ${conda_env}"

python_version = "3.7"
if ('python_version' in params && params.python_version != '') {
    python_version = params.python_version
}
echo "Python version: ${python_version}"

// Define the SRC of components
NPM = ""
if ('NPM' in params && params.NPM != '') {
    NPM = params.NPM
}
echo "NPM: ${NPM}"
npm_list = parseStrToList(NPM)

PIP = ""
if ('PIP' in params && params.PIP != '') {
    PIP = params.PIP
}
echo "PIP: ${PIP}"
pip_list = parseStrToList(PIP)

GIT = ""
if ('GIT' in params && params.GIT != '') {
    GIT = params.GIT
}
echo "GIT: ${GIT}"
git_list = parseStrToList(GIT)

def parseStrToList(srtingElements, delimiter=',') {
    if (srtingElements == ''){
        return []
    }
    return srtingElements[0..srtingElements.length()-1].tokenize(delimiter)
}

def cleanup() {

    try {
        sh '''#!/bin/bash -x
        cd $WORKSPACE
        rm -rf *
        rm -rf .git
        sudo rm -rf *
        sudo rm -rf .git
        '''
    } catch(e) {
        echo "==============================================="
        echo "ERROR: Exception caught in cleanup()           "
        echo "ERROR: ${e}"
        echo "==============================================="
    }

}

def conda_env_create() {
    println("full conda_env_name = " + conda_env)
    withEnv(["python_version=${python_version}", "conda_env_name=${conda_env}"]){
        sh '''#!/bin/bash
        export PATH=${HOME}/miniconda3/bin/:$PATH
        if [ $(conda info -e | grep ${conda_env_name} | wc -l) != 0 ]; then
        conda remove --name ${conda_env_name} --all -y
        fi
    
        conda_dir=$(dirname $(dirname $(which conda)))
        if [ -d ${conda_dir}/envs/${conda_env_name} ]; then
        rm -rf ${conda_dir}/envs/${conda_env_name}
        fi
    
        conda create -n ${conda_env_name} python=${python_version} -y 
        '''
    }
}

node(node_label){
    try{
        stage('download'){
            cleanup()
            dir('lpot-validation') {
                checkout scm
            }
        }
        stage('snyk scan') {
            if ("${CPU_NAME}" != ""){
                conda_env="${conda_env}-${CPU_NAME}"
            }
            if (NPM != '') {
                npm_list.each { npm_dep ->
                    echo "snyk scan on ${npm_dep} --->"
                    withEnv(["npm_dep=${npm_dep}"]) {
                    sh '''#!/bin/bash -x
                        bash ${WORKSPACE}/lpot-validation/scripts/run_snyk.sh \
                        --dep_name=${npm_dep} \
                        --src=npm
                        
                    '''
                    }
                }
            }
            if (PIP != ''){
                pip_list.each { pip_dep ->
                    echo "snyk scan on ${pip_dep} ---> "
                    conda_env_create()
                    withEnv(["pip_dep=${pip_dep}", "conda_name=${conda_env}"]) {
                    sh '''#!/bin/bash -x
                        bash ${WORKSPACE}/lpot-validation/scripts/run_snyk.sh \
                        --dep_name=${pip_dep} \
                        --src=pip \
                        --conda_name=${conda_name}
                        
                    '''
                    }
                }
            }

            if (GIT != ''){
                git_list.each { git_dep ->
                    echo "snyk scan on ${git_dep} ---> "
                    def modelConf =  jsonParse(readFile("$WORKSPACE/lpot-validation/config/snyk_scan_package_list.json"))
                    repo_url = modelConf."${git_dep}"."src"
                    repo_branch = modelConf."${git_dep}"."branch"
                    conda_env_create()
                    withEnv(["git_dep=${git_dep}", "conda_name=${conda_env}", "repo_url=${repo_url}", "repo_branch=${repo_branch}"]) {
                        sh '''#!/bin/bash -x
                        bash ${WORKSPACE}/lpot-validation/scripts/run_snyk.sh \
                        --dep_name=${git_dep} \
                        --src=git \
                        --repo_url=${repo_url} \
                        --repo_branch=${repo_branch} \
                        --conda_name=${conda_name}
                        
                    '''
                    }
                }
            }
        }
    }catch(e){
        currentBuild.result = "FAILURE"
        throw e
    }finally {
        // save log files
        stage("Archive Artifacts") {
            archiveArtifacts artifacts: "json_report/*json, html_report/*html, *.log", excludes: null
            fingerprint: true
        }
    }
}