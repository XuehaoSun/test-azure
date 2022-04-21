
SET framework=%1
SET model=%2
shift
shift

echo "Framework: %framework%"
echo "Model: %model%"


IF "%framework%" == "tensorflow" (
    GOTO:set_TF_env
) ELSE IF "%framework%" == "mxnet" (
    GOTO:set_MXNet_env
) ELSE IF "%framework%" == "pytorch" (
    GOTO:set_PT_env
) ELSE IF "%framework%" == "onnxrt" (
    GOTO:set_ONNXRT_env
) ELSE (
    echo "Framework %framework% not recognized"
    exit 1
)


:install_lpot
echo "Installing LPOT..."
CALL python -V

FOR /F %%i IN ('pip list ^| find /c "lpot"') do SET c_lpot=%%i

if %c_lpot% NEQ 0 (
    CALL pip uninstall lpot -y --user
    IF %ERRORLEVEL% NEQ 0 (
        echo "Could not remove LPOT package."
        exit 1
    )
    CALL pip list
)

cd %WORKSPACE%
FOR %%i in (lpot*.whl) DO CALL pip install %%i
IF %ERRORLEVEL% NEQ 0 (
    echo "Could not install lpot package."
    exit 1
)
echo "Checking LPOT..."
CALL pip list

cd %WORKSPACE%\\lpot-models

SET LOGLEVEL=DEBUG
GOTO:install_internal_requirements


:install_internal_requirements
echo "Installing requirements for validation scripts..."
CALL pip install -r %WORKSPACE%\lpot-validation\scripts\requirements.txt

GOTO:eof


:set_TF_env
echo "Setting env variables for TensorFlow..."
SET KMP_BLOCKTIME=1
SET KMP_AFFINITY=granularity=fine,verbose,compact,1,0
SET TF_MKL_OPTIMIZE_PRIMITIVE_MEMUSE=false

GOTO:install_lpot


:set_MXNet_env
echo "Setting env variables for MXNet..."
SET KMP_BLOCKTIME=1
SET KMP_AFFINITY=granularity=fine,verbose,compact,1,0
SET OMP_NUM_THREADS=28

GOTO:install_lpot


:set_PT_env
echo "Setting env variables for PyTorch..."
SET OMP_NUM_THREADS=28

GOTO:install_lpot


:set_ONNXRT_env
echo "Setting env variables for ONNX..."

GOTO:install_lpot
