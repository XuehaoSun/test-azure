credential = "lab_tfbot"

node_label = "non-perf"
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

val_branch="developer"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

echo "MR_source_branch: $MR_source_branch"
echo "MR_target_branch: $MR_target_branch"

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
                checkout changelog: true, poll: true, scm: [
                        $class                           : 'GitSCM',
                        branches                         : [[name: "${MR_source_branch}"]],
                        browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [
                                [$class: 'RelativeTargetDirectory', relativeTargetDir: "LPOT"],
                                [$class: 'CloneOption', timeout: 60],
                                [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'DEFAULT', mergeTarget: "${MR_target_branch}"]]
                        ],
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [
                                [credentialsId: "${credential}",
                                 url          : "${lpot_url}"]
                        ]
                ]

            }
        }
    }
}

node(node_label) {
    try {
        cleanup()
        download()
        stage("Code Scan") {
            echo "---------------------------------------------------------"
            echo "-----------------  Running License Check  -----------------"
            echo "---------------------------------------------------------"
            dir("$WORKSPACE/LPOT") {
                withEnv(["MR_target_branch=${MR_target_branch}"]) {
                    sh '''#!/bin/bash
                    set -xe
                    git --no-pager diff --name-only $(git show-ref -s remotes/origin/${MR_target_branch}) ./lpot > ${WORKSPACE}/diff.log
                    files=$(cat $WORKSPACE/diff.log | awk '!a[$0]++')
                    for file in ${files}
                    do
                        if [ $(grep -c "Copyright (c) 2021 Intel Corporation" ${file}) = 0 ]; then
                            echo ${file} >> ${WORKSPACE}/copyright_issue_summary.log
                        fi
                    done 
                '''
                }
            }
        }
        stage("Status Check") {
            out = sh(script:"ls ${WORKSPACE}/copyright_issue_summary.log",returnStatus:true)
            if ( out == 0 ) {
                currentBuild.result = 'FAILURE'
                error("------------------Check <copyright_issue_summary.log> for wrong file list !!!!!!!!!!!!!!!!!!!!!!!")
            }
        }

    } catch(e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.log', excludes: null, allowEmptyArchive: true
            fingerprint: true
        }
    }
}
