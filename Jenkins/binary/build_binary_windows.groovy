credential = "c09d6555-5e41-4b99-bf90-50f518319b49"

node_label = "clx8280"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

conda_env = "HOSTNAME"
if ('conda_env' in params && params.conda_env != '') {
    conda_env = params.conda_env
}
echo "Running ut on ${conda_env}"

lpot_url=""
if ('lpot_url' in params && params.lpot_url != ''){
    lpot_url = params.lpot_url
}
echo "lpot_url is ${lpot_url}"

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

binary_class = "wheel"
if ('binary_class' in params && params.binary_class != ''){
    binary_class = params.binary_class
}
echo "binary_class is ${binary_class}"

pypi_version = "default"
if ('pypi_version' in params && params.pypi_version != ''){
    pypi_version = params.pypi_version
}
echo "pypi_version is ${pypi_version}"

val_branch="master"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

python_version="3.6"
if ('python_version' in params && params.python_version != ''){
    python_version = params.python_version
}
echo "python_version is ${python_version}"

def cleanup() {

    try {
        stage("Cleanup") {
            try{
                bat '''
                    RMDIR /s /q C:\\Jenkins\\workspace\\.lpot
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
            }
        }
    } catch(e) {
        currentBuild.result = "FAILURE"
        throw e
    }
}

def cloneINCRepository() {
    try {
        stage("Clone INC repository") {
            dir(WORKSPACE) {
                retry(5) {
                    dir('lpot-validation') {
                        checkout scm
                    }

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
        }
    } catch(e) {
        currentBuild.result = "FAILURE"
        throw e
    }
}

def buildBinary() {
    try {
        stage("Binary build") {
            echo "+---------------- ${binary_class} build ----------------+"
            retry(3){
                timeout(120) {
                    do_binary_build()
                }
            }
        }
    } catch(e) {
        currentBuild.result = "FAILURE"
        throw e
    }
}

def do_binary_build() {
    if (binary_class == 'wheel') {
        create_conda_env()
        withEnv(["conda_env=${conda_env}"]) {
            retry(5) {
                bat """
                    CALL conda activate %conda_env%

                    IF %ERRORLEVEL% NEQ 0 (
                        echo "Could not activate conda environment."
                        exit 1
                    )
                    

                    echo "Build wheel..."
                    cd lpot-models
                    python setup.py sdist bdist_wheel
                    copy dist\\neural_compressor*.whl %WORKSPACE%\\
                    copy dist\\neural_compressor*.tar.gz %WORKSPACE%\\
                """
            }
        }
    } else if (binary_class == 'conda') {
        create_conda_env()
        retry(5) {
            bat """
                CALL conda activate %conda_env%

                IF %ERRORLEVEL% NEQ 0 (
                    echo "Could not activate conda environment."
                    exit 1
                )

                echo "Build wheel..."
                cd lpot-models
                python setup.py sdist bdist_wheel
                copy dist\\neural_compressor*.whl %WORKSPACE%\\
                
                echo "Build Conda binary..."
                CALL conda clean -a -y

                for /F %%i IN ('dir /b %WORKSPACE%\\neural_compressor*.whl') DO SET "NC_WHL_FILENAME=%%i"
                SET NC_WHL=%WORKSPACE%\\%NC_WHL_FILENAME%


                pip install pyyaml six 
                CALL conda config --add channels conda-forge
                CALL conda config --add channels fastai
                CALL conda config --add channels esri
                CALL conda install conda-build conda-verify -y

                CALL conda build meta.yaml --no-test

                FOR /F %%i IN ('where conda') do SET CONDA_PATH="%%i"
                FOR %%F in ("%CONDA_PATH:"=%") do SET CONDA_DIRNAME=%%~dpF

                SET CONDA_DIRNAME=%CONDA_DIRNAME:~0,-1%

                FOR %%F in ("%CONDA_DIRNAME:"=%") do SET CONDA_DIRNAME=%%~dpF

                echo "Conda dir: %CONDA_DIRNAME%"

                dir %CONDA_DIRNAME%\\envs\\%conda_env%\\conda-bld\\
                dir %CONDA_DIRNAME%\\envs\\%conda_env%\\conda-bld\\win-64
                for /F %%i IN ('dir /b %CONDA_DIRNAME%\\envs\\%conda_env%\\conda-bld\\win-64\\neural*.tar.bz2') DO SET "NC_PACKAGE_FILENAME=%%i"
                copy %CONDA_DIRNAME%\\envs\\%conda_env%\\conda-bld\\win-64\\%NC_PACKAGE_FILENAME% %WORKSPACE%\\
            """
        }
    } else {
        echo "DO NOT support ${binary_class} build!!!"
    }
}

def create_conda_env() {
    withEnv([
        "conda_env=${conda_env}",
        "python_version=${python_version}"]) {
        retry(5){

            bat '''
                CALL pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple
                CALL pip config set global.trusted-host "mirrors.aliyun.com test.pypi.org pypi.org pypi.python.org"
                CALL pip config set global.timeout 900

                FOR /F %%i IN ('conda info -e ^| find /c "%conda_env%"') do SET CONDA_COUNT=%%i

                if %CONDA_COUNT% NEQ 0 (
                    CALL conda remove --name "%conda_env:"=%" --all -y
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
                FOR /F %%i IN ('dir "%CONDA_DIRNAME:"=%envs"  ^| find /c "%conda_env%"') do SET CONDA_ENV_DIR_COUNT=%%i
                echo "CONDA_ENV_DIR_COUNT = %CONDA_ENV_DIR_COUNT%"

                IF %CONDA_ENV_DIR_COUNT% NEQ 0 (
                    RMDIR /S /Q "%CONDA_DIRNAME:"=%envs\\%conda_env:"=%"
                    IF %ERRORLEVEL% NEQ 0 (
                        echo "Could not remove conda environment dir."
                        exit 1
                    )
                )

                CALL conda create python=%python_version% -y -n %conda_env%
                IF %ERRORLEVEL% NEQ 0 (
                    echo "Could not create new conda environment."
                    exit 1
                )

                CALL conda activate %conda_env%
                
                CALL pip config list

                CALL pip install -U pip
                CALL pip install wheel

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

node(node_label){
    try{
        cleanup()
        cloneINCRepository()
        buildBinary()
    }catch(e){
        currentBuild.result = "FAILURE"
        throw e
    }finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: 'neural*.whl, neural*.tar.bz2, neural*.tar.gz', excludes: null
            fingerprint: true
        }
    }
}
