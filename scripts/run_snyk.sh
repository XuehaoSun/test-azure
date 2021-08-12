#!/bin/bash -x

for var in "$@"
do
    case $var in
        --dep_name=*)
            dep_name=$(echo $var | cut -f2 -d=);;
        --src=*)
            src=$(echo $var | cut -f2 -d=);;
        --conda_name=*)
            conda_name=$(echo $var | cut -f2 -d=);;
        --repo_url=*)
            repo_url=$(echo $var | cut -f2 -d=);;
        --repo_branch=*)
            repo_branch=$(echo $var | cut -f2 -d=);;
    esac
done

# set snyk ENV
export PATH=$PATH:/usr/local/lib/nodejs/node-v14.15.0-linux-x64/bin
export SNYK_API=https://snyk.devtools.intel.com/api
snyk config set api=199cca84-4704-4ac5-9ade-3945b3822668

# scan components
if [ "${src}" == "npm" ]; then
    npm_short_name=`echo ${dep_name} | cut -d'/' -f2`
    log_name=report_${npm_short_name}
    snyk test ${dep_name} --json > ${WORKSPACE}/${log_name}.json
fi

if [ "${src}" == "pip" ]; then
    export PATH=${HOME}/miniconda3/bin/:$PATH
    source activate ${conda_name}
    pip_short_name=`echo $dep_name | cut -d'=' -f1`
    log_name=report_${pip_short_name}
    mkdir $WORKSPACE/$pip_short_name
    cd "$WORKSPACE/$pip_short_name" || true
		echo $dep_name > $WORKSPACE/$pip_short_name/requirements.txt
		pip install -r requirements.txt
		pip list
		snyk test --json > ${WORKSPACE}/${log_name}.json
fi

if [ "${src}" == "git" ]; then
    export PATH=${HOME}/miniconda3/bin/:$PATH
    source activate ${conda_name}
    log_name=report_${dep_name}
    git clone $repo_url $dep_name && cd $dep_name && git checkout $repo_branch

    if [ -f "requirements.txt" ]; then
        echo "snyk scan on requirements.txt..."
        pip install -r requirements.txt
        pip list
        snyk test --json > ${WORKSPACE}/${log_name}.json
    elif [ -f "setup.py" ]; then
        echo "snyk scan on setup.py..."
        python setup.py install
        pip list
        pip freeze  | grep -v "pkg-resources" > requirements.txt
        snyk test --json > ${WORKSPACE}/${log_name}.json
    else
        echo "No file to scan..."
        echo "{summary: nothing to scan!}" > ${WORKSPACE}/${log_name}.json
        exit
    fi

fi

cd "$WORKSPACE"
snyk-to-html -i ${log_name}.json -o ${log_name}.html

# grep summary
RUNLOG=$WORKSPACE/snyk_summary.log
echo "DONE snyk on $dep_name. " >> $RUNLOG
if [ $(grep 'summary' ${log_name}.json | wc -l) == 0 ]; then
    echo "No summary check detailed log for $dep_name. " >> $RUNLOG
else
    grep "summary" ${log_name}.json >> $RUNLOG
fi
echo "-----"

if [ -f "${log_name}.json" ]; then
    mkdir -p json_report
    mv ${log_name}.json json_report
    mkdir -p html_report
    mv ${log_name}.html html_report
fi