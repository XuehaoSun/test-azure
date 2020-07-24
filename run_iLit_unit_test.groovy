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
    MR_source_branch = params.MR_source_branch
    MR_target_branch = params.MR_target_branch
    updateGitlabCommitStatus state: 'pending'
    gitLabConnection('gitlab.devtools.intel.com')
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
    }

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
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "ilit-models"],
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
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "ilit-models"],
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
        stage('unit test') {

            echo "+---------------- unit test ----------------+"
            sh '''#!/bin/bash -x
                export PATH=${HOME}/miniconda3/bin/:$PATH
                source activate tensorflow-1.15.2
                cd ${WORKSPACE}/ilit-models/test
                export PYTHONPATH=${PYTHONPATH}:${WORKSPACE}/ilit-models/
                find . -name "test*.py" | sed 's/.\\//python /g' > run.sh
                ut_log_name=${WORKSPACE}/unit_test.log
                bash run.sh 2>&1 | tee ${ut_log_name}
                if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] && [ $(grep -c "OK" unit_test.log) == 0 ];then
                    exit 1
                fi                 
            '''

        }

    }catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILED"
        throw e
    }finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.log,*/*.log', excludes: null
            fingerprint: true
        }
        if ("${MR_source_branch}" != '') {
            if (currentBuild.result == 'FAILURE') {
                echo "unit test failed"
                updateGitlabCommitStatus state: 'failed'
            } else {
                echo "unit test success"
                updateGitlabCommitStatus state: 'success'
            }
        }
    }
}