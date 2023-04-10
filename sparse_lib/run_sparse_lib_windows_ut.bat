setlocal enabledelayedexpansion

@REM Add utils (including tee) from git distribution; ref - https://stackoverflow.com/a/4488734
FOR /F "tokens=*" %%o IN ('where git') do (set git_path=%%o)
pushd "%git_path%\..\..\usr\bin"
set git_user_bin=%CD%
popd
set PATH=%PATH%;"%git_user_bin%"

cd %WORKSPACE%\\a\\intel_extension_for_transformers\\backends\\neural_engine
mkdir build && cd build
cmake .. -DNE_WITH_SPARSELIB_ONLY=ON -DNE_WITH_SPARSELIB_BENCHMARK=ON -DNE_WITH_TESTS=ON -DNE_WITH_ONEDNN_GRAPH=OFF -DNE_WITH_SPARSELIB=ON -DNE_DYNAMIC_LINK=ON 2>&1 | tee -a cmake_log
call:check_stage "incomplete" cmake_log 22
if %check_stage_tmp% equ 0 (
cmake --build . -j 2>&1 | tee -a build_log
call:check_stage "error" build_log 22
)
if %check_stage_tmp% equ 0 (
cd bin\\Debug
for /r %%i in (test*.exe) do ( %%i 2>&1 | tee -a run_log)
call:check_stage "FAIL" run_log 20
)
if %check_stage_tmp% equ 0 (
    exit  0
) else (
    exit  1
)

:check_stage
for /f "delims=" %%t in ('find /c %1 %2') do ( set check_stage_tmp=%%t )
set prefix_len=%3
set check_stage_tmp=!check_stage_tmp:~%prefix_len%,-1!
if %check_stage_tmp% gtr 0 (
    type %2 2>&1 | tee -a %WORKSPACE%\\a\\win_error_log
)
goto:eof
