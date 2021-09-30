credential = "c09d6555-5e41-4b99-bf90-50f518319b49"

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

supported_extensions = "py,sh,yaml"
if ('supported_extensions' in params && params.supported_extensions != '') {
    supported_extensions = params.supported_extensions
}
echo "supported_extensions: ${supported_extensions}"

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

            def extensions = supported_extensions.join(" ")

            dir("$WORKSPACE/LPOT") {
                withEnv(["MR_target_branch=${MR_target_branch}", "extensions=${supported_extensions}"]) {
                    sh '''#!/bin/bash
                    set -xe
                    supported_extensions=($(echo "${extensions}" | tr "," " "))

                    git --no-pager diff --name-only $(git show-ref -s remotes/origin/${MR_target_branch}) ./neural_compressor > ${WORKSPACE}/diff.log
                    files=$(cat $WORKSPACE/diff.log | awk '!a[$0]++')
                    for file in ${files}
                    do
                        if [[ " ${supported_extensions[@]} " =~ " ${file##*.} " ]]; then
                            echo "Checking license in ${file}"
                            if [ $(grep -c "Copyright (c) 2021 Intel Corporation" ${file}) = 0 ]; then
                                echo ${file} >> ${WORKSPACE}/copyright_issue_summary.log
                            fi
                        else
                            echo "Skipping ${file}"
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
