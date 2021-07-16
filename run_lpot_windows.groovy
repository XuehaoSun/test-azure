@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

credential = 'c09d6555-5e41-4b99-bf90-50f518319b49'

currentBuild.description = framework + '-' + model

// parameters
// setting node_label
sub_node_label = "lpot"
if ('sub_node_label' in params && params.sub_node_label != '') {
    sub_node_label = params.sub_node_label
}
echo "Running on node ${sub_node_label}"

// test framework
framework = "tensorflow"
if ('framework' in params && params.framework != '') {
    framework = params.framework
}
echo "framework: ${framework}"

// setting framework_version
framework_version  = '1.15.2'
if ('framework_version' in params && params.framework_version != '') {
    framework_version = params.framework_version
}
echo "framework_version: ${framework_version}"

// model
model  = 'resnet50'
if ('model' in params && params.model != '') {
    model = params.model
}
echo "Running ${model}"

precision  = 'int8,fp32'
if ('precision' in params && params.precision != '') {
    precision = params.precision
}
def precision_list = parseStrToList(precision)
echo "Running ${precision}"

mode = 'latency'
if ('mode' in params && params.mode != '') {
    mode = params.mode
}
def mode_list = parseStrToList(mode)
echo "Running ${mode}"

lpot_url="https://gitlab.devtools.intel.com/intelai/LowPrecisionInferenceTool"
if ('lpot_url' in params && params.lpot_url != ''){
    lpot_url = params.lpot_url
}
echo "lpot_url is ${lpot_url}"

requirement_list="ruamel.yaml==0.17.4"
if ('requirement_list' in params && params.requirement_list != ''){
    requirement_list = params.requirement_list
}
echo "requirement_list is ${requirement_list}"

python_version="3.6"
if ('python_version' in params && params.python_version != ''){
    python_version = params.python_version
}
echo "python_version is ${python_version}"

strategy="basic"
if ('strategy' in params && params.strategy != ''){
    strategy = params.strategy
}
echo "strategy is ${strategy}"

binary_build_job=""
if ('binary_build_job' in params && params.binary_build_job != ''){
    binary_build_job = params.binary_build_job
}
echo "binary_build_job is ${binary_build_job}"

test_mode="nightly"
if ('test_mode' in params && params.test_mode != ''){
    test_mode = params.test_mode
}
echo "test_mode is ${test_mode}"

lpot_branch = ''
MR_source_branch = ''
MR_target_branch = ''
if ('lpot_branch' in params && params.lpot_branch != '') {
    lpot_branch = params.lpot_branch
}else{
    MR_source_branch = params.MR_source_branch
    MR_target_branch = params.MR_target_branch
}
echo "lpot_branch: $lpot_branch"
echo "MR_source_branch: $MR_source_branch"
echo "MR_target_branch: $MR_target_branch"

tuning_timeout="10800"
if ('tuning_timeout' in params && params.tuning_timeout != ''){
    tuning_timeout=params.tuning_timeout
    tuning_timeout="${tuning_timeout}"
}
echo "tuning_timeout: ${tuning_timeout}"

max_trials=0
if ('max_trials' in params && params.max_trials != ''){
    max_trials=params.max_trials
}
echo "max_trials: ${max_trials}"

tune_only=false
if (params.tune_only != null){
    tune_only=params.tune_only
}
echo "tune_only = ${tune_only}"

val_branch="master"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

def new_env=true
if(framework == 'pytorch'){
    label=model.split('_')
    if(label[0] == 'bert'){
        sub_node_label='py-bert'
        new_env=false
    }
    if(model == 'dlrm'){
        sub_node_label='dlrm'
    }
}

env_type="conda"
if ('env_type' in params && params.env_type != ''){
    env_type=params.env_type
}
echo "env_type: ${env_type}"


onnx_version = '1.7.0'
if ('onnx_version' in params && params.onnx_version != '') {
    onnx_version = params.onnx_version
}
println("onnx_version: " + onnx_version)


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


def cleanup() {

    try {
        bat '''
            RMDIR /s /q C:/Jenkins/workspace/.lpot
            ( dir /b /a "." | findstr . ) > nul && (
                FORFILES /P "." /M * /C "cmd /c if @isdir==FALSE (del @file) else (rmdir /s /q @path)"
            )
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

def parseStrToList(srtingElements, delimiter=',') {
    if (srtingElements == ''){
        return []
    }
    return srtingElements[0..srtingElements.length()-1].tokenize(delimiter)
}

def create_virtual_env() {
    withEnv(["framework=${framework}","framework_version=${framework_version}","python_version=${python_version}"]) {
        retry(5) {
            bat '''

                CALL pip config set global.index-url http://pypi.douban.com/simple/
                CALL pip config set global.trusted-host "pypi.douban.com test.pypi.org pypi.org pypi.python.org"

                SET env_name=%framework%-%framework_version%-%python_version%

                FOR /F %%i IN ('dir "."  ^| find /c "%env_name%"') do SET VENV_COUNT=%%i

                 if %VENV_COUNT% NEQ 0 (
                    RMDIR /S /Q "%env_name:"=%"
                )

                CALL python -m venv %env_name%
                IF %ERRORLEVEL% NEQ 0 (
                    echo "Could not create new virtual environment."
                    exit 1
                )

                CALL %env_name%\\Scripts\\activate
                IF %ERRORLEVEL% NEQ 0 (
                    echo "Could not activate venv."
                    exit 1
                )

                pip -V

                where pip

                CALL pip config list
                CALL pip install -U pip
                CALL pip install ruamel.yaml==0.17.4 wheel

                IF "%framework%" == "tensorflow" (
                    IF "%framework_version%" == "1.15UP1" (
                        IF "%python_version%" == "3.6" (
                                CALL pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp36-cp36m-manylinux2010_x86_64.whl
                        ) ELSE IF "%python_version%" == "3.7" (
                                CALL pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp37-cp37m-manylinux2010_x86_64.whl
                        ) ELSE IF "%python_version%" == "3.5" (
                                CALL pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp35-cp35m-manylinux2010_x86_64.whl
                        ) ELSE echo "!!! TF 1.15UP1 do not support %python_version:"=%"
                    ) ELSE (
                        CALL pip install intel-%framework:"=%==%framework_version:"=%
                    )
                ) ELSE IF "%framework%" == "pytorch" (
                        IF "%model%" == "resnest50" (
                            SET framework_version="1.6.0+cpu"
                        )
                        CALL pip install torch==%framework_version% -f https://download.pytorch.org/whl/torch_stable.html
                ) ELSE IF "%framework%" == "mxnet" (
                    IF "%framework_version%" == "1.6.0" (
                        CALL pip install %framework:"=%-mkl==%framework_version%
                    ) ELSE IF "%framework_version%" == "1.7.0" (
                        CALL pip install %framework:"=%==%framework_version:"=%.post1
                    ) ELSE pip install %framework:"=%==%framework_version%
                ) ELSE IF "%framework%" == "onnx" (
                    CALL pip install onnx==%onnx_version%
                    IF "%framework_version%" == "nightly" (
                        CALL pip uninstall -y onnxruntime
                        CALL pip install -i https://test.pypi.org/simple ort-nightly
                    ) ELSE (
                        CALL pip install onnxruntime==%framework_version%
                    )
                    IF %model% == "bert_base_MRPC" (
                        CALL pip install torch
                        CALL pip install torchvision
                    )
                )

                IF %ERRORLEVEL% NEQ 0 (
                    echo "Could not install requirements."
                    exit 1
                )

                echo "pip list all the components------------->"
                CALL pip list
                echo "------------------------------------------"
            '''
        }
    }
}

def create_conda_env() {
    withEnv(["framework=${framework}","framework_version=${framework_version}","python_version=${python_version}"]) {
        retry(5){

            bat '''
                CALL pip config set global.index-url http://pypi.douban.com/simple/
                CALL pip config set global.trusted-host "pypi.douban.com test.pypi.org pypi.org pypi.python.org"

                SET conda_env_name=%framework%-%framework_version%-%python_version%

                FOR /F %%i IN ('conda info -e ^| find /c "%conda_env_name%"') do SET CONDA_COUNT=%%i

                if %CONDA_COUNT% NEQ 0 (
                    CALL conda remove --name "%conda_env_name:"=%" --all -y
                    IF %ERRORLEVEL% NEQ 0 (
                        echo "Could not remove conda environment."
                        exit 1
                    )
                )

                FOR /F %%i IN ('where conda') do SET CONDA_PATH="%%i"
                FOR %%F in ("%CONDA_PATH:"=%") do SET CONDA_DIRNAME=%%~dpF

                SET CONDA_DIRNAME=%CONDA_DIRNAME:~0,-1%

                FOR %%F in ("%CONDA_DIRNAME:"=%") do SET CONDA_DIRNAME=%%~dpF

                echo "Conda dir: %CONDA_DIRNAME%"
                FOR /F %%i IN ('dir "%CONDA_DIRNAME:"=%envs"  ^| find /c "%conda_env_name%"') do SET CONDA_ENV_DIR_COUNT=%%i
                echo "CONDA_ENV_DIR_COUNT = %CONDA_ENV_DIR_COUNT%"

                IF %CONDA_ENV_DIR_COUNT% NEQ 0 (
                    RMDIR /S /Q "%CONDA_DIRNAME:"=%envs\\%conda_env_name:"=%"
                    IF %ERRORLEVEL% NEQ 0 (
                        echo "Could not remove conda environment dir."
                        exit 1
                    )
                )

                CALL conda create python=%python_version% -y -n %conda_env_name%
                IF %ERRORLEVEL% NEQ 0 (
                    echo "Could not create new conda environment."
                    exit 1
                )

                CALL conda activate %conda_env_name%
                
                CALL pip config list

                CALL pip install -U pip
                CALL pip install ruamel.yaml==0.17.4 wheel

                IF "%framework%" == "tensorflow" (
                    IF "%framework_version%" == "1.15UP1" (
                        IF "%python_version%" == "3.6" (
                                CALL pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp36-cp36m-manylinux2010_x86_64.whl
                        ) ELSE IF "%python_version%" == "3.7" (
                                CALL pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp37-cp37m-manylinux2010_x86_64.whl
                        ) ELSE IF "%python_version%" == "3.5" (
                                CALL pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp35-cp35m-manylinux2010_x86_64.whl
                        ) ELSE echo "!!! TF 1.15UP1 do not support %python_version:"=%"
                    ) ELSE (
                        CALL pip install intel-%framework:"=%==%framework_version:"=%
                    )
                ) ELSE IF "%framework%" == "pytorch" (
                        IF "%model%" == "resnest50" (
                            SET framework_version="1.6.0+cpu"
                        )
                        CALL pip install torch==%framework_version% -f https://download.pytorch.org/whl/torch_stable.html
                ) ELSE IF "%framework%" == "mxnet" (
                    IF "%framework_version%" == "1.6.0" (
                        CALL pip install %framework:"=%-mkl==%framework_version%
                    ) ELSE IF "%framework_version%" == "1.7.0" (
                        CALL pip install %framework:"=%==%framework_version:"=%.post1
                    ) ELSE pip install %framework:"=%==%framework_version%
                ) ELSE IF "%framework%" == "onnxrt" (
                   CALL pip install onnx==%onnx_version%
                    IF "%framework_version%" == "nightly" (
                        CALL pip uninstall -y onnxruntime
                        CALL pip install -i https://test.pypi.org/simple ort-nightly
                    ) ELSE (
                        CALL pip install onnxruntime==%framework_version%
                    )
                    IF "%model%" == "bert_base_MRPC" (
                        CALL pip install torch
                        CALL pip install torchvision
                    )
                )

                CALL pip install opencv-python

                IF %ERRORLEVEL% NEQ 0 (
                    echo "Could not install requirements."
                    exit 1
                )

                echo "pip list all the components------------->"
                CALL pip list
                echo "------------------------------------------"
            '''
        }
    }
}

def normalizePath(path) {
    return path.replaceAll("\\\\", "/")
}

node( sub_node_label ) {
    // Get CPU name from env variable if not defined
    if (['unknown','any', '*'].contains(cpu)) {
        cpu = env.CPU_NAME
        echo "Detected cpu: ${cpu}"
    }

    stage("Cleanup") {
        cleanup()
    }

    stage("Clone validation repository") {
        dir('lpot-validation') {
            retry(5) {
                checkout scm
            }
        }
    }

    try {

        stage("Prepare environment"){
            if (new_env){
                if (env_type == "conda") {
                    create_conda_env()
                } else {
                    create_virtual_env()
                }
            }else{
                println("Test need a special local conda env, DO NOT create again!!!")
            }

        }

        stage("Clone LPOT repository") {
            retry(5) {
                if(MR_source_branch != ''){
                    checkout changelog: true, poll: true, scm: [
                            $class                           : 'GitSCM',
                            branches                         : [[name: "${MR_source_branch}"]],
                            browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                            doGenerateSubmoduleConfigurations: false,
                            extensions                       : [
                                    [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot-models"],
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
                else {
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
            }
        }

        if ("${binary_build_job}" == "") {
            stage('Build wheel') {
                withEnv([
                    "framework=${framework}",
                    "framework_version=${framework_version}",
                    "python_version=${python_version}"]) {
                    retry(5) {
                        bat """
                            SET env_name=%framework%-%framework_version%-%python_version%

                            IF "${env_type}" == "conda" (
                                CALL conda activate %env_name%
                            ) else (
                                CALL ${WORKSPACE}\\%env_name%\\Scripts\\activate
                            )
                            IF %ERRORLEVEL% NEQ 0 (
                                echo "Could not activate environment."
                                exit 1
                            )
                            

                            echo "Build wheel..."
                            cd lpot-models
                            python setup.py sdist bdist_wheel
                            copy dist\\lpot*.whl ${WORKSPACE}\\
                        """
                    }
                }
                archiveArtifacts artifacts: "lpot*.whl"
            }
        } else {
            stage("Copy wheel")
            copyArtifacts(
                projectName: 'lpot-release-wheel-build',
                selector: specific("${binary_build_job}"),
                filter: 'lpot*.whl',
                fingerprintArtifacts: true,
                target: "${WORKSPACE}")

            archiveArtifacts artifacts: "lpot*.whl"
        }

        stage("Read model config") {
            // get params FOR tuning and benchmark
            def configPath = "$WORKSPACE/lpot-validation/config/model_params_${framework}_win.json"
            println("Reading config from " + configPath)
            def modelConf =  jsonParse(readFile(configPath))
            println(modelConf."${framework}"."${model}")
            
            model_src_dir = modelConf."${framework}"."${model}"."model_src_dir"
            println("model_src_dir = " + model_src_dir)
            
            dataset_location = modelConf."${framework}"."${model}"."dataset_location"
            println("dataset_location = " + dataset_location)

            input_model = modelConf."${framework}"."${model}"."input_model"
            println("input_model = " + input_model)

            yaml = modelConf."${framework}"."${model}"."yaml"
            println("yaml = " + yaml)

            //mr test will cover different strategies, the other test mode will use the passed strategy
            if ( MR_source_branch != '' ){
                if (framework == "tensorflow"){
                    strategy = "basic"
                    if (model_src_dir == "image_recognition"){
                        dataset_location = "C:/Users/Public/Downloads/dataset/TF_mini_imagenet"
                        println("MR test tensorflow model_src_dir is image_recognition.")
                        println("So set dataset_location to C:/Users/Public/Downloads/dataset/TF_mini_imagenet")
                    }
                    if (model_src_dir == "object_detection" && model == "ssd_resnet50_v1"){
                        // Set mini-coco FOR obj mr test, set absolute baseline replace relative one to reach the acc goal
                        dataset_location = " C:/Users/Public/Downloads/dataset/tensorflow/mini-coco-500.record"
                        withEnv(["model_src_dir=${model_src_dir}"]) {
                            bat (
                                    script:"""
                                        SET yaml_config="%WORKSPACE:"=%\\lpot-models\\examples\\%framework:"=%\\%model_src_dir:"=%\\ssd_resnet50_v1.yaml"
                                        powershell -command "Get-content %yaml_config% | Foreach-Object {\$_ -replace 'relative:\\s*.*\$', 'absolute: 0.01'} | Set-Content %yaml_config%
                                    """,
                                    returnStdout: true
                            ).trim()
                        }
                    }
                }else if(framework == "pytorch" && model == "resnet18"){
                    strategy = "bayesian"
                }else if(framework == "mxnet" && model == "resnet50v1"){
                    strategy = "mse"
                }
            }
        }

        stage("Tuning") {
            if ( MR_source_branch != '' ){
                tuninig_timeout="5400"
            }
            timeout(time: tuning_timeout, unit: "SECONDS") {
                model_src_dir = normalizePath("${WORKSPACE}\\lpot-models\\examples\\${framework}\\${model_src_dir}")
                dataset_location = normalizePath("${DATASET_DIR}\\${dataset_location}")
                input_model = normalizePath("${DATASET_DIR}\\${input_model}")
                withCredentials([string(credentialsId: 'sigopt_api_token_suyue', variable: 'SIGOPT_TOKEN')]) {
                    withEnv(["framework=${framework}","framework_version=${framework_version}","python_version=${python_version}"]) {
                        bat """
                            echo "Running ---- ${framework}, ${model}, ${strategy} ----Tuning"
                            CALL quser
                            echo "-------quser-------"
                            
                            SET env_name=%framework%-%framework_version%-%python_version%
                            IF "%framework%" == "pytorch" (
                                IF NOT %model:bert=% == %model% (
                                    SET env_name=pytorch-bert-1.6
                                )
                            )

                            IF "${env_type}" == "conda" (
                                CALL conda activate %env_name%
                            ) else (
                                CALL ${WORKSPACE}\\%env_name%\\Scripts\\activate
                            )
                            IF %ERRORLEVEL% NEQ 0 (
                                echo "Could not activate environment."
                                exit 1
                            )

                            CALL ${WORKSPACE}%\\lpot-validation\\scripts\\env_setup.bat ${framework} ${model}

                            CALL python ${WORKSPACE}%\\lpot-validation\\scripts\\run_tuning_trigger.py ^
                            --framework=${framework} ^
                            --model=${model} ^
                            --model_src_dir=${model_src_dir} ^
                            --dataset_location=${dataset_location} ^
                            --input_model=${input_model} ^
                            --yaml=${yaml} ^
                            --strategy=${strategy} ^
                            --strategy_token=${SIGOPT_TOKEN} ^
                            --max_trials=${max_trials} ^
                            --cpu=${cpu}
                        """
                    }
                }
            }
        }

        stage("Check tuning status"){
            dir("${WORKSPACE}"){

                bat """
                    SET control_phrase="Found a quantized model which meet accuracy goal."
                    IF %model% == "helloworld_keras" (
                        SET control_phrase="Inference is done."
                    )

                    FOR /F %%i IN ('type "${framework}-${model}-${os}-${cpu}-tune.log" ^| find /c %control_phrase%') do SET status_count=%%i 
                    if %status_count% EQU 0 (
                        exit 1
                    )

                """
            }
        }

        stage("Performance") {
            // Set Latency mode for MR tests
            if ((lpot_branch == ''&& MR_source_branch != '') || model_src_dir == 'oob_models' || model == 'style_transfer') {
                mode_list = ["latency"]
                echo "Mode list: ${mode_list}"
            }

            def configPath = "$WORKSPACE/lpot-validation/config/model_params_${framework}_win.json"
            println("Reading config from " + configPath)
            def modelConf =  jsonParse(readFile(configPath))
            println(modelConf."${framework}"."${model}")

            if (!tune_only && model != "helloworld_keras") {
                println("========== Benchmark ========")
                batch_size = modelConf."${framework}"."${model}"."batch_size"
                timeout(360) {
                    withEnv(["framework=${framework}","framework_version=${framework_version}","python_version=${python_version}"]) {
                        precision_list.each { precision ->
                        echo "Precision: ${precision}"
                            mode_list.each { mode ->
                                echo "Mode: ${mode}"
                                bat """
                                    echo "Running ---- ${framework}, ${model}, ${precision}, ${mode} ---- Benchmarking"
                                    
                                    echo "-------quser-------"
                                    CALL quser
                                    echo "-------quser-------"
                                    
                                    SET env_name=%framework%-%framework_version%-%python_version%
                                    IF "%framework%" == "pytorch" (
                                        IF NOT %model:bert=% == %model% (
                                            SET env_name=pytorch-bert-1.6
                                        )
                                    )

                                    IF "${env_type}" == "conda" (
                                        CALL conda activate %env_name%
                                    ) else (
                                        CALL ${WORKSPACE}\\%env_name%\\Scripts\\activate
                                    )
                                    IF %ERRORLEVEL% NEQ 0 (
                                        echo "Could not activate environment."
                                        exit 1
                                    )

                                    CALL python ${WORKSPACE}%\\lpot-validation\\scripts\\run_benchmark_trigger.py ^
                                        --framework=${framework} ^
                                        --model=${model} ^
                                        --model_src_dir=${model_src_dir} ^
                                        --dataset_location=${dataset_location} ^
                                        --input_model=${input_model} ^
                                        --precision=${precision} ^
                                        --mode=${mode} ^
                                        --batch_size=${batch_size} ^
                                        --yaml=${yaml} ^
                                        --cpu=${cpu}

                                    IF %ERRORLEVEL% NEQ 0 (
                                        echo "Error while executing benchmark."
                                        exit 1
                                    )
                                """
                            }
                        }
                    }
                }
            }
        }


    } catch(e) {
        currentBuild.result = "FAILURE"
        throw e
    } finally {

        // save log files
        stage("Archive Artifacts") {
            archiveArtifacts artifacts: "${framework}*.log, tuning_config.yaml", excludes: null
            fingerprint: true
        }
    }
    
}
