
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


:install_inc
echo "Installing Neural Compressor..."
CALL python -V

FOR /F %%i IN ('pip list ^| find /c "neural-compressor"') do SET c_inc=%%i

if %c_inc% NEQ 0 (
    CALL pip uninstall neural-compressor-full -y --user
    IF %ERRORLEVEL% NEQ 0 (
        echo "Could not remove Neural Compressor package."
        exit 1
    )
    CALL pip list
)

cd %WORKSPACE%
FOR %%i in (neural_compressor*.whl) DO CALL pip install %%i
IF %ERRORLEVEL% NEQ 0 (
    echo "Could not install inc package."
    exit 1
)
echo "Checking Neural Compressor..."
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

SET TF_ENABLE_ONEDNN_OPTS=1
SET TF_ENABLE_MKL_NATIVE_FORMAT=1

GOTO:install_inc


:set_MXNet_env
echo "Setting env variables for MXNet..."
SET KMP_BLOCKTIME=1
SET KMP_AFFINITY=granularity=fine,verbose,compact,1,0
SET OMP_NUM_THREADS=28

GOTO:install_inc


:set_PT_env
echo "Setting env variables for PyTorch..."
SET OMP_NUM_THREADS=28

GOTO:install_inc


:set_ONNXRT_env
echo "Setting env variables for ONNX..."

GOTO:install_inc
