// Groovy

credential = 'lab_tfbot'

// Parameters Pre-defined
node_label = "lpot"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "---- Running on node ${node_label} ----"

pythons = "3.6"
if ('pythons' in params && params.pythons != '') {
    pythons = params.pythons
}
echo "---- pythons: ${pythons} ----"

strategies = "lpot"
if ('strategies' in params && params.strategies != '') {
    strategies = params.strategies
}
echo "strategies: ${strategies}"

frameworks = "tensorflow"
if ('frameworks' in params && params.frameworks != '') {
    frameworks = params.frameworks
}

tensorflow_versions = "2.4.0"
if ('tensorflow_versions' in params && params.tensorflow_versions != '') {
    tensorflow_versions = params.tensorflow_versions
}

pytorch_versions = "1.5.0+cpu"
if ('pytorch_versions' in params && params.pytorch_versions != '') {
    pytorch_versions = params.pytorch_versions
}

mxnet_versions = "1.7.0"
if ('mxnet_versions' in params && params.mxnet_versions != '') {
    mxnet_versions = params.mxnet_versions
}

onnxruntime_versions = "1.6.0"
if ('onnxruntime_versions' in params && params.onnxruntime_versions != '') {
    onnxruntime_versions = params.onnxruntime_versions
}

// setting onnx_version
onnx_version = '1.7.0'
if ('onnx_version' in params && params.onnx_version != '') {
    onnx_version = params.onnx_version
}
echo "onnx_version: ${onnx_version}"

tune_only=false
if (params.tune_only != null){
    tune_only=params.tune_only
}
echo "tune_only = ${tune_only}"

PARALLEL=true
if (params.PARALLEL != null){
    PARALLEL=params.PARALLEL
}
echo "PARALLEL = ${PARALLEL}"

RUN_UT=true
if (params.RUN_UT != null){
    RUN_UT=params.RUN_UT
}
echo "RUN_UT = ${RUN_UT}"

echo "---- frameworks: ${frameworks} ----"
echo "---- tensorflow_versions: ${tensorflow_versions} ----"
echo "---- pytorch_versions: ${pytorch_versions} ----"
echo "---- mxnet_versions: ${mxnet_versions} ----"
echo "---- onnxruntime_versions: ${onnxruntime_versions} ----"

lpot_url = "https://gitlab.devtools.intel.com/intelai/LowPrecisionInferenceTool.git"
if ('lpot_url' in params && params.lpot_url != '') {
    lpot_url = params.lpot_url
}

lpot_branch = "developer"
if ('lpot_branch' in params && params.lpot_branch != '') {
    lpot_branch = params.lpot_branch
}

// setting refer_build
refer_build = "x0"
if ('refer_build' in params && params.refer_build != '') {
    refer_build = params.refer_build
}
echo "Running ${refer_build}"

tuning_timeout="10800"
if ('tuning_timeout' in params && params.tuning_timeout != ''){
    tuning_timeout=params.tuning_timeout
}
echo "tuning_timeout: ${tuning_timeout}"


val_branch="master"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"


py_list = pythons.split(",")
st_list = strategies.split(",")
fw_list = frameworks.split(",")
tf_list = tensorflow_versions.split(",")
pt_list = pytorch_versions.split(",")
mx_list = mxnet_versions.split(",")
onnxrt_list = onnxruntime_versions.split(",")

def tensorflow_models_pass = ""
def tensorflow_oob_models_pass = ""
def pytorch_models_pass = ""
def mxnet_models_pass = ""
def onnxrt_models_pass = ""
def weekly_description = ""

EXCEL_REPORT=false
if ('EXCEL_REPORT' in params && params.EXCEL_REPORT){
    echo "EXCEL_REPORT is true"
    EXCEL_REPORT=params.EXCEL_REPORT
}

def main() {
    // clean up
    dir( WORKSPACE ) {
        sh """
            rm -rf ./*
            rm -rf .git
        """
        dir('lpot-validation'){
            retry(5) {
                checkout scm
            }
        }
    }

    // copy reference
    if(refer_build != 'x0') {
        copyArtifacts(
            projectName: currentBuild.projectName,
            selector: specific("${refer_build}"),
            filter: 'summary.log',
            fingerprintArtifacts: true,
            target: "reference",
            optional: true
        )
    }

    // download lpot
    retry(5) {
        checkout changelog: true, poll: true, scm: [
                $class                           : 'GitSCM',
                branches                         : [[name: "${lpot_branch}"]],
                browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                doGenerateSubmoduleConfigurations: false,
                extensions                       : [
                        [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot-lpot"],
                        [$class: 'CloneOption', timeout: 60]
                ],
                submoduleCfg                     : [],
                userRemoteConfigs                : [
                        [credentialsId: "${credential}",
                        url          : "${lpot_url}"]
                ]
        ]
    }

    def lpot_commit = sh (
            script: """
                cd lpot-lpot  &&  git rev-parse HEAD
            """,
            returnStdout: true
    ).trim()

    summary_log_init = "${WORKSPACE}/summary_init.log"
    summary_log = "${WORKSPACE}/summary.log"
    writeFile file: summary_log_init, text:""
    writeFile file: summary_log, text: "Python; FWK; FWK version; Strategy; Status; Job URL; Job Number \n"

    try {

        def build_jobs = [:]
        def refer_number = 'x0'

        py_list.each { py ->
            //only for loop st for py3.6
            if (py != '3.6'){
                st_list=['basic']
            }
            fw_list.each { fw ->

                // set framework version
                if( fw == "tensorflow" ) {
                    fw_ver_list = tf_list
                    if (py == '3.8'){
                        fw_ver_list = ['2.2.0', '2.3.0']
                    }
                }else if( fw == "pytorch" ) {
                    fw_ver_list = pt_list
                }else if( fw == "mxnet" ) {
                    fw_ver_list = mx_list
                }else if( fw == "onnxrt" ) {
                    fw_ver_list = onnxrt_list
                }else {
                    error("${fw}: No such framework!")
                }

                fw_ver_list.each { fw_ver ->

                    st_list.each { st ->
                        if (PARALLEL) {
                            build_jobs["${py}-${fw}-${fw_ver}-${st}"] = {
                                getJob(py, fw, fw_ver, st, lpot_commit)
                            } // jobs
                        } else {
                            getJob(py, fw, fw_ver, st, lpot_commit)
                        }
                    } // framework_version
                } // framework
            } // strategy
        } // python

        // Execute test suites
        if (PARALLEL) {
            parallel build_jobs
        }

        // generate report
        withEnv([
                "summary_log=${summary_log}",
                "summary_log_init=${summary_log_init}"
        ]) {
            sh '''#!/bin/bash -x
            sort ${summary_log_init} >> ${summary_log} 
            bash ${WORKSPACE}/lpot-validation/scripts/generate_lpot_report_weekly.sh 
            '''
        }

        if (EXCEL_REPORT) {
            stage("Generate excel report") {
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    retry(5) {
                        generateExcelReport(lpot_commit)
                    }
                }
            }
        }

    } catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        throw e

    } finally {
        // save logs
        dir( WORKSPACE ) {
            archiveArtifacts artifacts: "summary.log, report.html, logs/**/*"
        }

        // send report
        emailext subject: "LPOT Weekly",
            to: "${recipient_list}",
            replyTo: "${recipient_list}",
            body: '''${FILE,path="report.html"}''',
            attachmentsPattern: "",
            mimeType: 'text/html'

    }
} // node

def getJob(python_version, framework, framework_version, strategy, lpot_commit) {
    stage("${python_version}-${framework}-${framework_version}-${strategy}") {
        echo "---- Runing ${framework}-${framework_version} with python-${python_version} and strategy-${strategy} ----"

        def downstreamJob
        catchError {

            // get the reference build number
            if(refer_build != 'x0') {
                refer_number = sh (
                        script: """
                            cat ${WORKSPACE}/reference/summary.log |grep "${python_version};${framework};${framework_version};${strategy}" |cut -d ';' -f7 |sed 's/[^0-9]//g'
                        """,
                        returnStdout: true
                ).trim()
            }

            // set models
            tensorflow_models_pass = tensorflow_models
            tensorflow_oob_models_pass = tensorflow_oob_models
            pytorch_models_pass = pytorch_models
            mxnet_models_pass = mxnet_models
            onnxrt_models_pass = onnxrt_models

            fw_version_param_name = "${framework}_version"
            
            weekly_description = "${python_version}-${framework}-${framework_version}-${strategy}"
            if(python_version == '3.6' && strategy == 'basic') {
                if (framework == 'tensorflow' && framework_version == '2.2.0') {
                    tensorflow_models_pass = all_tensorflow_models
                    tensorflow_oob_models_pass = all_tensorflow_oob_models
                    weekly_description = "${python_version}-${framework}-${framework_version}-${strategy}-all_models"
                }
                if (framework == 'pytorch' && framework_version == '1.5.0+cpu') {
                    pytorch_models_pass = all_pytorch_models
                    weekly_description = "${python_version}-${framework}-${framework_version}-${strategy}-all_models"
                }
                if (framework == 'mxnet' && framework_version == '1.7.0') {
                    mxnet_models_pass = all_mxnet_models
                    weekly_description = "${python_version}-${framework}-${framework_version}-${strategy}-all_models"
                }
                if (framework == 'onnxrt' && framework_version == '1.6.0') {
                    onnxrt_models_pass = all_onnxrt_models
                    weekly_description = "${python_version}-${framework}-${framework_version}-${strategy}-all_models"
                    fw_version_param_name = "onnxruntime_version"
                }
            }

            echo "---- tensorflow_models_pass: ${tensorflow_models_pass}"
            echo "---- pytorch_models_pass: ${pytorch_models_pass}"
            echo "---- mxnet_models_pass: ${mxnet_models_pass}"
            echo "---- onnxrt_models_pass: ${onnxrt_models_pass}"

            downstreamJob = build job: "intel-lpot-validation-top-weekly", propagate: false, parameters: [
                string(name: 'Frameworks', value:"${framework}"),
                string(name: "${fw_version_param_name}", value:"${framework_version}"),
                string(name: "onnx_version", value: "${onnx_version}"),
                string(name: 'strategy', value:"${strategy}"),
                string(name: 'python_version', value:"${python_version}"),
                string(name: 'sub_node_label', value:"${node_label}"),
                string(name: 'refer_build', value:"${refer_number}"),
                string(name: 'tensorflow_models', value:"${tensorflow_models_pass}"),
                string(name: 'tensorflow_oob_models', value:"${tensorflow_oob_models_pass}"),
                string(name: 'pytorch_models', value:"${pytorch_models_pass}"),
                string(name: 'mxnet_models', value:"${mxnet_models_pass}"),
                string(name: 'onnxrt_models', value:"${onnxrt_models_pass}"),
                string(name: 'lpot_url', value:"${lpot_url}"),
                string(name: 'lpot_branch', value:"${lpot_commit}"),
                string(name: 'val_branch', value:"${val_branch}"),
                string(name: 'test_mode', value: "weekly"),
                string(name: 'weekly_description', value:"${weekly_description}"),
                booleanParam(name: "tune_only", value: tune_only),
                booleanParam(name: "RUN_UT", value: RUN_UT),
                string(name: 'tuning_timeout', value: "${tuning_timeout}")
            ]

        } // catchError

        build_number = downstreamJob.number
        build_result = downstreamJob.result
        build_url = downstreamJob.absoluteUrl

        context_text = readFile file: summary_log_init
        writeFile file: summary_log_init, text: context_text + "${python_version};${framework};${framework_version};${strategy};${build_result};${build_url}artifact/report.html;${build_number}" + "\n"

        catchError {
            copyArtifacts(
                    projectName: "intel-lpot-validation-top-weekly",
                    selector: specific("${build_number}"),
                    filter: 'tuning_info.log',
                    fingerprintArtifacts: true,
                    target: "logs/${build_number}",
                    optional: true)
        }
    } // stage
}


def generateExcelReport(lpot_commit) {
    withEnv([
        "summaryLog=${summary_log}",
        "lpot_commit=${lpot_commit}",
        "tensorflow_versions=${tensorflow_versions}",
        "mxnet-versions=${mxnet_versions}",
        "pytorch-versions=${pytorch_versions}",
        "onnxruntime-versions=${onnxruntime_versions}"
    ]) {
        sh '''#!/bin/bash
            set -x

            if [ ! -d "${WORKSPACE}/.lpot-report-generator" ]; then
                python3 -m venv ${WORKSPACE}/.lpot-report-generator
            fi

            source ${WORKSPACE}/.lpot-report-generator/bin/activate

            set +e
            python -m pip install --index-url http://mirrors.aliyun.com/pypi/simple -r ./lpot-validation/scripts/report_generator/requirements.txt
            exit_code=$?
            set -e

            if [ $exit_code -ne 0 ]; then
                for requirement in `cat ./lpot-validation/scripts/report_generator/requirements.txt`
                do
                    requirement_whl=$(find ${HOME}/whls  -iname "${requirement}*.whl")
                    if [ ! -z ${requirement_whl} ]; then
                        python -m pip install ${requirement_whl}
                    else
                        echo "Could not found whl file for ${requirement}"
                        exit 1
                    fi
                done
            fi

            workweek=$(($(date +%V)+1))  # TODO: Make WW correct for each year. Current solution will work only for 2021

            python ./lpot-validation/scripts/report_generator/generate_weekly_report.py \
                --logs-dir="${WORKSPACE}/logs" \
                --commit="${lpot_commit}" \
                --tensorflow-version="${tensorflow_versions}" \
                --mxnet-version="${mxnet_versions}" \
                --pytorch-version="${pytorch_versions}" \
                --onnxruntime-version="${onnxruntime_versions}" \
                --WW="${workweek}"

        '''
    }
}


node("master") {
    main()
}
