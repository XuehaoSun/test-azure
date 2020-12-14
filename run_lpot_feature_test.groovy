credential = 'lab_tfbot'

// parameters
// setting node_label
sub_node_label = "lpot"
if ('sub_node_label' in params && params.sub_node_label != '') {
    sub_node_label = params.sub_node_label
}
echo "Running on node ${sub_node_label}"

lpot_url="https://gitlab.devtools.intel.com/intelai/LowPrecisionInferenceTool"
if ('lpot_url' in params && params.lpot_url != ''){
    lpot_url = params.lpot_url
}
echo "lpot_url is ${lpot_url}"

requirement_list="ruamel.yaml"
if ('requirement_list' in params && params.requirement_list != ''){
    requirement_list = params.requirement_list
}
echo "requirement_list is ${requirement_list}"

python_version="3.6"
if ('python_version' in params && params.python_version != ''){
    python_version = params.python_version
}
echo "python_version is ${python_version}"

binary_build_job=""
if ('binary_build_job' in params && params.binary_build_job != ''){
    binary_build_job = params.binary_build_job
}
echo "binary_build_job is ${binary_build_job}"

lpot_branch = ''
if ('lpot_branch' in params && params.lpot_branch != '') {
    lpot_branch = params.lpot_branch
}
echo "lpot_branch: $lpot_branch"

feature_name = ''
if ('feature_name' in params && params.feature_name != '') {
    feature_name = params.feature_name
}
echo "feature_name: ${feature_name}"

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
    checkout changelog: true, poll: true, scm: [
            $class                           : 'GitSCM',
            branches                         : [[name: "${lpot_branch}"]],
            browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
            doGenerateSubmoduleConfigurations: false,
            extensions                       : [
                    [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot-models"],
                    [$class: 'CloneOption', timeout: 60]
            ],
            submoduleCfg                     : [],
            userRemoteConfigs                : [
                    [credentialsId: "${credential}",
                     url          : "${lpot_url}"]
            ]
    ]
}

node( sub_node_label ){
    try {
        cleanup()
        dir('lpot-validation') {
            checkout scm
        }
        stage("download"){
            download()
        }

        
        if ("${binary_build_job}" == "") {
            stage('Build binary') {
                List binaryBuildParams = [
                        string(name: "lpot_url", value: "${lpot_url}"),
                        string(name: "lpot_branch", value: "${lpot_branch}"),
                        string(name: "MR_source_branch", value: "${MR_source_branch}"),
                        string(name: "MR_target_branch", value: "${MR_target_branch}"),
                ]
                downstreamJob = build job: "lpot-release-wheel-build", propagate: false, parameters: binaryBuildParams
                
                binary_build_job = downstreamJob.getNumber()
                echo "binary_build_job: ${binary_build_job}"
                echo "downstreamJob.getResult(): ${downstreamJob.getResult()}"
                if (downstreamJob.getResult() != "SUCCESS") {
                    currentBuild.result = "FAILURE"
                    failed_build_url = downstreamJob.absoluteUrl
                    echo "failed_build_url: ${failed_build_url}"
                    error("---- lpot wheel build got failed! ---- Details in ${failed_build_url}consoleText! ---- ")
                }
            }
        }
        
        stage("feature test"){
            dir(WORKSPACE){
                withEnv([
                        "feature_name=${feature_name}",
                        "python_version=${python_version}"
                ]) {
                    sh '''
                        #!/bin/bash
                        set -xe
                        chmod 775 ./lpot-validation/scripts/feature_test/test_${feature_name}.sh
                        ./lpot-validation/scripts/feature_test/test_${feature_name}.sh
                    '''
                }
            }
        }

    } catch(e) {
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.log', excludes: null
            fingerprint: true
        }
    }
}

