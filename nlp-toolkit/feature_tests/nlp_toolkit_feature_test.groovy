@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

credential = 'c09d6555-5e41-4b99-bf90-50f518319b49'

// parameters
// setting node_label
sub_node_label = "lpot"
if ('sub_node_label' in params && params.sub_node_label != '') {
    sub_node_label = params.sub_node_label
}
echo "Running on node ${sub_node_label}"

nlp_url="https://github.com/intel-innersource/frameworks.ai.nlp-toolkit.intel-nlp-toolkit.git"
if ('nlp_url' in params && params.nlp_url != ''){
    nlp_url = params.nlp_url
}
echo "nlp_url is ${nlp_url}"

lpot_url = "https://github.com/intel/neural-compressor.git"
lpot_branch = "master"

requirement_list="ruamel.yaml==0.17.4"
if ('requirement_list' in params && params.requirement_list != ''){
    requirement_list = params.requirement_list
}
echo "requirement_list is ${requirement_list}"

python_version="3.8"
if ('python_version' in params && params.python_version != ''){
    python_version = params.python_version
}
echo "python_version is ${python_version}"

binary_build_job=""
if ('binary_build_job' in params && params.binary_build_job != ''){
    binary_build_job = params.binary_build_job
}
echo "binary_build_job is ${binary_build_job}"

binary_build_job_nlp = ""
if ('binary_build_job_nlp' in params && params.binary_build_job_nlp != ''){
    binary_build_job_nlp = params.binary_build_job_nlp
}
echo "binary_build_job_nlp is ${binary_build_job_nlp}"

nlp_branch = ''
if ('nlp_branch' in params && params.nlp_branch != '') {
    nlp_branch = params.nlp_branch
}
echo "nlp_branch: $nlp_branch"

val_branch="main"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

feature_name = ''
if ('feature_name' in params && params.feature_name != '') {
    feature_name = params.feature_name
}
echo "feature_name: ${feature_name}"

// Platforms specification pattern: "os1:cpu_name1,cpuname_2;os2:cpu_name1,cpuname_3"
PLATFORMS = "linux:*"
if ('PLATFORMS' in params && params.PLATFORMS != ''){
    PLATFORMS = params.PLATFORMS
}
echo "PLATFORMS: ${PLATFORMS}"

cpu="unknown"
if ('cpu' in params && params.cpu != ''){
    cpu=params.cpu
}
echo "cpu: ${cpu}"

os="unknown"
if ('os' in params && params.os != ''){
    os=params.os
}
echo "os: ${os}"

if (feature_name == 'graph_optimization'){
    sub_node_label='ILIT && spr'
}

def cleanup() {
    try {
        dir(WORKSPACE) {
            deleteDir()
            sh '''#!/bin/bash -x
                rm -rf *
                rm -rf .git
                sudo rm -rf *
                sudo rm -rf .git
                git config --global user.email "sys_lpot_val@intel.com"
                git config --global user.name "sys-lpot-val"
            '''
        }
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
            branches                         : [[name: "${nlp_branch}"]],
            browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
            doGenerateSubmoduleConfigurations: false,
            extensions                       : [
                    [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot-models"],
                    [$class: 'CloneOption', timeout: 5]
            ],
            submoduleCfg                     : [],
            userRemoteConfigs                : [
                    [credentialsId: "${credential}",
                     url          : "${nlp_url}"]
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
                        string(name: "inc_url", value: "${nlp_url}"),
                        string(name: "inc_branch", value: "${nlp_branch}"),
                        string(name: "val_branch", value: "${val_branch}"),
                        string(name: "LINUX_BINARY_CLASSES", value: "wheel"),
                        string(name: "LINUX_PYTHON_VERSIONS", value: "${python_version}"),
                        string(name: "WINDOWS_BINARY_CLASSES", value: ""),
                        string(name: "WINDOWS_PYTHON_VERSIONS", value: ""),
                ]
                downstreamJob = build job: "lpot-release-build", propagate: false, parameters: binaryBuildParams
                
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
        if ("${binary_build_job_nlp}" == "") {
            stage("build Binary NLP"){
                List binaryBuildParamsNLP = [
                        string(name: "python_version", value: "${python_version}"),
                        string(name: "nlp_url", value: "${nlp_url}"),
                        string(name: "nlp_branch", value: "${nlp_branch}"),
                        string(name: "val_branch", value: "${val_branch}")
                ]
                downstreamJob = build job: "nlp-toolkit-release-wheel-build", propagate: false, parameters: binaryBuildParamsNLP
                binary_build_job_nlp = downstreamJob.getNumber()
                echo "binary_build_job_nlp: ${binary_build_job_nlp}"
                echo "downstreamJob.getResult(): ${downstreamJob.getResult()}"
                if (downstreamJob.getResult() != "SUCCESS") {
                    currentBuild.result = "FAILURE"
                    failed_build_url = downstreamJob.absoluteUrl
                    echo "failed_build_url: ${failed_build_url}"
                    error("---- lpot wheel build got failed! ---- Details in ${failed_build_url}consoleText! ---- ")
                }
            }
        }
        stage('Copy binary') {
            catchError {
                copyArtifacts(
                        projectName: 'lpot-release-build',
                        selector: specific("${binary_build_job}"),
                        filter: "linux_binaries/wheel/${python_version}/neural_compressor*.whl, linux_binaries/wheel/${python_version}/neural_compressor*.tar.gz, linux_binaries/wheel/${python_version}/neural-compressor*.tar.bz2",
                        fingerprintArtifacts: true,
                        flatten: true,
                        target: "${WORKSPACE}")
            }
            catchError {
                copyArtifacts(
                        projectName: 'nlp-toolkit-release-wheel-build',
                        selector: specific("${binary_build_job_nlp}"),
                        filter: 'intel_extension_for_transformers*.whl, nlp-toolkit-*.tar.bz2, intel_extension_for_transformers-*.tar.gz',
                        fingerprintArtifacts: true,
                        target: "${WORKSPACE}")
            }
        }
        
        stage("feature test"){
            dir(WORKSPACE){
                def featuresConfig =  jsonParse(readFile("$WORKSPACE/lpot-validation/config/features.json"))
                def featureConf = [:]
                try {
                    featureConf = featuresConfig."${feature_name}"
                } catch (e) {
                    println("Not found additional parameters for \"${feature_name}\" feature.")
                }
                def args = ""
                for (param in featureConf) {
                    args += "--${param.key}=${param.value}"
                }
                withEnv([
                        "feature_name=${feature_name}",
                        "python_version=${python_version}",
                        "args=${args}"
                ]) {
                    sh '''
                        #!/bin/bash
                        set -xe
                        chmod 775 ./lpot-validation/nlp-toolkit/feature_tests/scripts/test_${feature_name}.sh
                        ./lpot-validation/nlp-toolkit/feature_tests/scripts/test_${feature_name}.sh --python_version=${python_version} ${args}
                    '''
                }
                sh """#!/bin/bash
                    echo ${CPU_NAME} > ${WORKSPACE}/cpu_name.log
                """
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

