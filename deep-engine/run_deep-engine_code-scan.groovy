credential = "c09d6555-5e41-4b99-bf90-50f518319b49"

node_label = "non-perf"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

TOOL = "TOOL"
if ('TOOL' in params && params.TOOL != '') {
    TOOL = params.TOOL
}
echo "TOOL: ${TOOL}"

deepengine_url="git@github.com:intel-innersource/frameworks.ai.deep-engine.intel-deep-engine.git"
if ('deepengine_url' in params && params.deepengine_url != ''){
    deepengine_url = params.deepengine_url
}
echo "deepengine_url is ${deepengine_url}"

deepengine_branch = ''
PR_source_branch = ''
PR_target_branch = ''
if ('deepengine_branch' in params && params.deepengine_branch != '') {
    deepengine_branch = params.deepengine_branch

}else{
    PR_source_branch = params.PR_source_branch
    PR_target_branch = params.PR_target_branch
}
echo "deepengine_branch: $deepengine_branch"
echo "PR_source_branch: $PR_source_branch"
echo "PR_target_branch: $PR_target_branch"

conda_env = "deep-engine-code-scan"
if ('conda_env' in params && params.conda_env != '') {
    conda_env = params.conda_env
}
echo "Running code scan on ${conda_env}"

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

        echo ' '
        echo "Error while doing cleanup"
    }
}

def download() {
    retry(5) {
        dir('lpot-validation') {
            checkout scm
        }
        def deepengine_branch = deepengine_branch
        if (PR_source_branch != "") {
            deepengine_branch = PR_source_branch
        }
        checkout changelog: true, poll: true, scm: [
                $class                           : 'GitSCM',
                branches                         : [[name: "${deepengine_branch}"]],
                browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                doGenerateSubmoduleConfigurations: false,
                extensions                       : [
                        [$class: 'RelativeTargetDirectory', relativeTargetDir: "deep-engine"],
                        [$class: 'CloneOption', timeout: 60]
                ],
                submoduleCfg                     : [],
                userRemoteConfigs                : [
                        [credentialsId: "${credential}",
                         url          : "${deepengine_url}"]
                ]
        ]
    }
}

node(node_label){
    try{
        cleanup()
        stage('download') {
            download()
        }
        stage("Code Scan") {
            echo "---------------------------------------------------------"
            echo "-----------------  Running Code Scan  -----------------"
            echo "---------------------------------------------------------"
            status = sh(
                    script: "bash ${WORKSPACE}/lpot-validation/deep-engine/scripts/run_engine_format_scan.sh  --tool=${TOOL} --repo_dir=${WORKSPACE}/deep-engine",
                    returnStatus:true)
            if (status != 0) {
                throw new Exception("Found code format scan errors.")
            }
        }

    }catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.log, *.json', excludes: null
            fingerprint: true
        }
    }

}

