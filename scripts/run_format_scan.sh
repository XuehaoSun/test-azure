#!/bin/bash
# Script assumes that repository is currently on branch with new changes (source branch).

set -x
set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='
if [ $# != "2" ] ; then 
    echo 'ERROR:'
    echo "Expected 2 parameters got $#"
    printf 'Please use following parameters:
    --repo_dir=<path to repository>
    --target_branch=<MR target branch>
    '
    exit 1
fi

for i in "$@"
do
    case $i in
        --repo_dir=*)
            REPO_DIR=`echo $i | sed "s/${PATTERN}//"`;;
        --target_branch=*)
            TARGET_BRANCH=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

export PATH=${HOME}/miniconda3/bin/:$PATH
source activate ${HOSTNAME}
python -V

pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple
pip install pylint

cd ${REPO_DIR}

if [[ ${TARGET_BRANCH} == '' ]]; then
    echo "TARGET BRANCH not provided. Executing pylint on current branch: $(git name-rev --name-only HEAD)."
else
    mkdir diff_dir
    cp -pv --parents $(git --no-pager diff --name-only $(git show-ref -s remotes/origin/${TARGET_BRANCH})) ./diff_dir
    cd diff_dir
    if [ ! -d "ilit" ]; then
        echo "No changes in ilit module."
        exit 0
    fi
fi

python -m pylint -f json --disable=R,C,W,I,E0401,E0611 --enable=line-too-long --max-line-length=99 ilit > ${WORKSPACE}/ilit-pylint.json
pylint_code=$?
if [ ${pylint_code} -ne 0 ]; then
    exit 1
fi
exit 0
