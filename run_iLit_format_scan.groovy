updateGitlabCommitStatus state: 'pending'
gitLabConnection('gitlab.devtools.intel.com')

credential = "lab_tfbot"

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

nigthly_test_branch = ''
MR_source_branch = ''
MR_target_branch = ''
if ('nigthly_test_branch' in params && params.nigthly_test_branch != '') {
    nigthly_test_branch = params.nigthly_test_branch

}else{
    if ("${gitlabSourceBranch}" != '') {
        MR_source_branch = "${gitlabSourceBranch}"
        MR_target_branch = "${gitlabTargetBranch}"
    }
}
echo "nigthly_test_branch: $nigthly_test_branch"
echo "MR_source_branch: $MR_source_branch"
echo "MR_target_branch: $MR_target_branch"


def cleanup() {

    try {
        sh '''#!/bin/bash -x
        cd $WORKSPACE
        sudo rm -rf *           
        '''
    } catch(e) {
        echo "==============================================="
        echo "ERROR: Exception caught in cleanup()           "
        echo "ERROR: ${e}"
        echo "==============================================="

        echo ' '
        echo "Error while doing cleanup"
    }  // catch

}

def download() {
    dir(WORKSPACE) {

        checkout scm

        if(MR_source_branch != ''){
            checkout changelog: true, poll: true, scm: [
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${MR_source_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "iLit"],
                            [$class: 'CloneOption', timeout: 60],
                            [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'DEFAULT', mergeTarget: "${MR_target_branch}"]]
                    ],
                    submoduleCfg                     : [],
                    userRemoteConfigs                : [
                            [credentialsId: "${credential}",
                             url          : "${ilit_url}"]
                    ]
            ]
        }
        else {
            checkout changelog: true, poll: true, scm: [
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${nigthly_test_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "iLit"],
                            [$class: 'CloneOption', timeout: 60]
                    ],
                    submoduleCfg                     : [],
                    userRemoteConfigs                : [
                            [credentialsId: "${credential}",
                             url          : "${ilit_url}"]
                    ]
            ]
        }
    }
}

node(node_label){
    try{
        cleanup()
        download()
        stage('Flake8') {
            echo "+---------------- Flake8 ----------------+"

            sh '''#!/bin/bash
                export PATH=${HOME}/miniconda3/bin/:$PATH
                conda remove --all -y -n ${HOSTNAME}
                conda create python=3.6.9 -y -n ${HOSTNAME}
                source activate ${HOSTNAME}
                python -V
                
                set -x
                pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple
                pip install flake8
                flake8 --max-line-length 120 ${WORKSPACE}/iLit > ${WORKSPACE}/flake8-iLit-$(date +%s).log 2>&1 || true
            '''
        }

        stage('pylint'){
            echo "+---------------- Pylint ----------------+"

            sh '''#!/bin/bash
                export PATH=${HOME}/miniconda3/bin/:$PATH
                source activate ${HOSTNAME}
                python -V
                
                set -x
                pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple
                pip install pylint
                python -m pylint --persistent=n --generate-rcfile >pylint.conf
                python -m pylint --rcfile=pylint.conf ${WORKSPACE}/iLit > ${WORKSPACE}/pylint-iLit-$(date +%s).log 2>&1 || true
            '''

            updateGitlabCommitStatus state:'success'
        }

    }catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILED"
        throw e
    }finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.log,*.html,*/*.log', excludes: null
            fingerprint: true
        }
    }
}