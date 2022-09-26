# SparseLib CI

Trigger with PR comment `test pr-sparse`.

## Optional PR comment parameters
Usage:
- `test pr-sparse`
- `test pr-sparse: [options]`

Options:
| Option | Description |
|--------|-------------|
| `--ut`                            | Run UT (sparselib and engine)
| `--ut=sparse_ut_only`             | Run sparselib UT only
| `--benchmark`                     | Run sparselib benchmark
| `--cpplint`                       | Run cpplint check
| `--bandit`                        | Run bendit check
| `--spell`                         | Run speel check
| `--copyright`                     | Run copyright check
| `--models`                        | Run all neural engine models containing sparselib kernels 
| `--models=<models>`               | Run a specified list (comma separated) of models with neural engine
| `--inferencer_config=<configs>`   | Specify a list (comma separated) of model running configs in the format of `<ncores_per_instance>:<bs>` 
| `--refer_build=<jobID>`           | The job id of the build as a refered result (shown as "last" in the test report)

If no parameters are presented, the project parameters set in Jenkins will be used.
