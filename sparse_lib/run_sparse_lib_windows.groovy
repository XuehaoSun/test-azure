credential = "c09d6555-5e41-4b99-bf90-50f518319b49"

node_label = "icx-windows"
// if ('node_label' in params && params.node_label != '') {
//     node_label = params.node_label
// }
echo "Running on node ${node_label}"

nlp_url = "https://github.com/intel-innersource/frameworks.ai.nlp-toolkit.intel-nlp-toolkit.git" 
if ('nlp_url' in params && params.nlp_url != ''){
    nlp_url = params.nlp_url
}
echo "nlp_url is ${nlp_url}"

nlp_branch = ''
PR_source_branch = ''
PR_target_branch = ''
if ('nlp_branch' in params && params.nlp_branch != '') {
    nlp_branch = params.nlp_branch
}else{
    PR_source_branch = params.PR_source_branch
    PR_target_branch = params.PR_target_branch
}
echo "nlp_branch: $nlp_branch"
echo "PR_source_branch: $PR_source_branch"
echo "PR_target_branch: $PR_target_branch"

val_branch = ''
if ('val_branch' in params && params.val_branch != ''){
    val_branch = params.val_branch
}
echo "val_branch is ${val_branch}"
lpot_url = ''
if ('lpot_url' in params && params.lpot_url != ''){
    lpot_url=params.lpot_url
}
echo "lpot_url is ${lpot_url}"

def cleanup() {
    try {
        bat '''
        echo bat_clean
        cd C:\\Users\\sdp\\Jenkins\\workspace\\sparse-lib-windows
        echo Y|rmdir /s a\
        echo Y|rmdir /s lpot-validation\
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
    retry(5) {
        if(PR_source_branch != ''){
            checkout changelog: true, poll: true, scm: [
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${PR_source_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "a"],
                            [$class: 'CloneOption', timeout: 5],
                            [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'DEFAULT', mergeTarget: "${PR_target_branch}"]]
                    ],
                    submoduleCfg                     : [],
                    userRemoteConfigs                : [
                            [credentialsId: "${credential}",
                             url          : "${nlp_url}"]
                    ]
            ]

        }
        else {
            checkout changelog: true, poll: true, scm: [
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${nlp_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "a"],
                            [$class: 'CloneOption', timeout: 5]
                    ],
                    submoduleCfg                     : [],
                    userRemoteConfigs                : [
                            [credentialsId: "${credential}",
                             url          : "${nlp_url}"]
                    ]
            ]
        }
    }
    retry(5){
        bat '''
            if not exist C:\\Users\\sdp\\Jenkins\\workspace\\sparse-lib-windows\\a (
                echo windows_nlp_dir not found. Exiting..."
                exit 1
            ) else (
            cd C:\\Users\\sdp\\Jenkins\\workspace\\sparse-lib-windows\\a
            git submodule update --init --recursive
            )
        '''
    }

}


node(node_label){
    try{
        echo "begin clean"
        cleanup()
        dir('lpot-validation') {
            retry(5) {
                checkout scm
            }
        }
        echo "clean done, begin download"
        stage('download') {
            download()
        }
        echo "download done, begin ut"
        stage('unit test'){
            retry(5){
                 ut_status = bat(returnStatus: true, script:"""
                CALL C:\\Users\\sdp\\Jenkins\\workspace\\sparse-lib-windows\\lpot-validation\\sparse_lib\\run_sparse_lib_windows_ut.bat 
                if exist C:\\Users\\sdp\\Jenkins\\workspace\\sparse-lib-windows\\a\\win_error_log (
                exit 1
                ) else (
                exit 0
                )
                """)
            }
            if(ut_status != 0 ){
                currentBuild.result = 'FAILURE'
                error("sparse lib test failed!")
            }
        }
    }
    catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: 'a/intel_extension_for_transformers/backends/neural_engine/build/*log,a/intel_extension_for_transformers/backends/neural_engine/build/bin/Debug/*log,a/*log', excludes: null
            fingerprint: true
        }
    }
}