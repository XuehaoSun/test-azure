
// Groovy

credential = '5da0b320-00b8-4312-b653-36d4cf980fcb'

// Parameters Pre-defined
node_label = "iLit"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "---- Running on node ${node_label} ----"

pythons = "3.6"
if ('pythons' in params && params.pythons != '') {
    pythons = params.pythons
}
echo "---- pythons: ${pythons} ----"

strategies = "ILIT"
if ('strategies' in params && params.strategies != '') {
    strategies = params.strategies
}
echo "strategies: ${strategies}"

frameworks = "tensorflow"
if ('frameworks' in params && params.frameworks != '') {
    frameworks = params.frameworks
}

tensorflow_versions = "ILIT"
if ('tensorflow_versions' in params && params.tensorflow_versions != '') {
    tensorflow_versions = params.tensorflow_versions
}

pytorch_versions = "ILIT"
if ('pytorch_versions' in params && params.pytorch_versions != '') {
    pytorch_versions = params.pytorch_versions
}

mxnet_versions = "ILIT"
if ('mxnet_versions' in params && params.mxnet_versions != '') {
    mxnet_versions = params.mxnet_versions
}
echo "---- frameworks: ${frameworks} ----"
echo "---- tensorflow_versions: ${tensorflow_versions} ----"
echo "---- pytorch_versions: ${pytorch_versions} ----"
echo "---- mxnet_versions: ${mxnet_versions} ----"

ilit_url = "https://gitlab.devtools.intel.com/intelai/LowPrecisionInferenceTool.git"
if ('ilit_url' in params && params.ilit_url != '') {
    ilit_url = params.ilit_url
}

nigthly_test_branch = "developer"
if ('nigthly_test_branch' in params && params.nigthly_test_branch != '') {
    nigthly_test_branch = params.nigthly_test_branch
}

// setting refer_build
refer_build = "x0"
if ('refer_build' in params && params.refer_build != '') {
    refer_build = params.refer_build
}
echo "Running ${refer_build}"

py_list = pythons.split(",")
st_list = strategies.split(",")
fw_list = frameworks.split(",")
tf_list = tensorflow_versions.split(",")
pt_list = pytorch_versions.split(",")
mx_list = mxnet_versions.split(",")


// def all_tensorflow_models = ''
// def all_pytorch_models = ''
// def all_mxnet_models = ''
// def tensorflow_models = ''
// def pytorch_models = ''
// def mxnet_models = ''


// start
node( 'master' ) {
    
    // clean up
    dir( WORKSPACE ) {
        deleteDir()
        sh " rm -rf ./* "
        checkout scm
    }

    // copy reference
    if(refer_build != 'x0') {
        copyArtifacts(
            projectName: currentBuild.projectName,
            selector: specific("${refer_build}"),
            filter: 'summary.log',
            fingerprintArtifacts: true,
            target: "reference"
        )
    }

    // download iLit
    checkout changelog: true, poll: true, scm: [
            $class                           : 'GitSCM',
            branches                         : [[name: "${nigthly_test_branch}"]],
            browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
            doGenerateSubmoduleConfigurations: false,
            extensions                       : [
                    [$class: 'RelativeTargetDirectory', relativeTargetDir: "ilit-ilit"],
                    [$class: 'CloneOption', timeout: 60]
            ],
            submoduleCfg                     : [],
            userRemoteConfigs                : [
                    [credentialsId: "${credential}",
                     url          : "${ilit_url}"]
            ]
    ]
    def ilit_commit = sh (
            script: """
                cd ilit-ilit  &&  git rev-parse HEAD
            """,
            returnStdout: true
    ).trim()

    summary_log_init = "${WORKSPACE}/summary_init.log"
    summary_log = "${WORKSPACE}/summary.log"
    writeFile file: summary_log, text: "Python; FWK; FWK version; Strategy; Status; Job URL; Job Number \n"

    try {

        def build_jobs = [:]
        def refer_number = 'x0'

        py_list.each { py ->
            fw_list.each { fw ->

                // set framework version
                if( fw == "tensorflow" ) {
                    fw_ver_list = tf_list
                }else if( fw == "pytorch" ) {
                    fw_ver_list = pt_list
                }else if( fw == "mxnet" ) {
                    fw_ver_list = mx_list
                }else {
                    error("${fw}: No such framework!")
                }

                fw_ver_list.each { fw_ver ->

                    // build env
                    // build_env["env-${py}-${fw}-${fw_ver}"] = {
                    // 
                    //     stage("env-${py}-${fw}-${fw_ver}") {
                    //         def downstreamEnv = build job: "create_conda_env", propagate: true, parameters: [
                    //             string(name: 'node_label', value:"${node_label}"),
                    //             string(name: 'framework', value:"${fw}"),
                    //             string(name: 'framework_version', value:"${fw_ver}"),
                    //             string(name: 'python_version', value:"${py}")
                    //         ]
                    //     }
                    // }

                    st_list.each { st ->

                        echo "---- tensorflow_models: ${tensorflow_models}"
                        echo "---- pytorch_models: ${pytorch_models}"
                        echo "---- mxnet_models: ${mxnet_models}"

                        build_jobs["${py}-${st}-${fw}-${fw_ver}"] = {
                            stage("${py}-${st}-${fw}-${fw_ver}") {
                            
                                echo "---- Runing ${fw}-${fw_ver} with py-${py} and strategy-${st} ----"

                                def downstreamJob
                                catchError {

                                    // get the reference build number
                                    if(refer_build != 'x0') {
                                        refer_number = sh (
                                                script: """
                                                    cat ${WORKSPACE}/reference/summary.log |grep "${py};${fw};${fw_ver};${st}" |cut -d ';' -f7 |sed 's/[^0-9]//g'
                                                """,
                                                returnStdout: true
                                        ).trim()
                                    }

                                    // set models
                                    if(py == '3.6' && st == 'basic') {
                                        if (fw == 'tensorflow') {
                                            tensorflow_models = all_tensorflow_models
                                        }
                                        if (fw == 'pytorch' && fw_ver == '1.6.0') {
                                            pytorch_models = all_pytorch_models
                                        }
                                        if (fw == 'mxnet' && fw_ver == '1.6.0') {
                                            mxnet_models = all_mxnet_models
                                        }
                                    }
                                
                                    downstreamJob = build job: "intel-iLit-validation-top-weekly", propagate: false, parameters: [
                                        string(name: 'Frameworks', value:"${fw}"),
                                        string(name: "${fw}_version", value:"${fw_ver}"),
                                        string(name: 'strategy', value:"${st}"),
                                        string(name: 'python_version', value:"${py}"),
                                        string(name: 'sub_node_label', value:"${node_label}"),
                                        string(name: 'refer_build', value:"${refer_number}"),
                                        string(name: 'tensorflow_models', value:"${tensorflow_models}"),
                                        string(name: 'pytorch_models', value:"${pytorch_models}"),
                                        string(name: 'mxnet_models', value:"${mxnet_models}"),
                                        string(name: 'ilit_url', value:"${ilit_url}"),
                                        string(name: 'nigthly_test_branch', value:"${ilit_commit}"),
                                        string(name: 'test_mode', value: "weekly")
                                    ]

                                } // catchError

                                build_number = downstreamJob.number
                                build_result = downstreamJob.result
                                build_url = downstreamJob.absoluteUrl
                    
                                context_text = readFile file: summary_log_init
                                writeFile file: summary_log_init, text: context_text + "${py};${fw};${fw_ver};${st};${build_result};${build_url}artifact/report.html;${build_number}" + "\n"
                            } // stage
                        } // jobs
                    } // framework_version
                } // framework
            } // strategy
        } // python

        // parallel build_env
        parallel build_jobs

        // generate report
        withEnv([
                "summary_log=${summary_log}",
                "summary_log_init=${summary_log_init}"
        ]) {
            sh '''#!/bin/bash -x
            sort ${summary_log_init} >> ${summary_log} 
            bash ${WORKSPACE}/scripts/generate_ilit_report_weekly.sh 
            '''
        }
    
    } catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        throw e

    } finally {
        // save logs
        dir( WORKSPACE ) {
            archiveArtifacts artifacts: "summary.log, report.html"
        }

        // send report
        emailext subject: "iLiT Weekly",
            to: "${recipient_list}",
            replyTo: "${recipient_list}",
            body: '''${FILE,path="report.html"}''',
            attachmentsPattern: "",
            mimeType: 'text/html'

    }
} // node
