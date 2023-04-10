import numpy as np
import torch
from datasets import load_metric
from pathlib import Path
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score
from sentence_transformers import SentenceTransformer, InputExample, losses, models, datasets, evaluation, util
from time import perf_counter

from datasets import load_dataset
agnews = load_dataset("ag_news")
test_dataset = agnews["test"]
topics = agnews["test"].features["label"]

accuracy_score = load_metric("accuracy")

class PerformanceBenchmark:
    def __init__(self, model, dataset, optim_type):
        self.model = model
        self.dataset = dataset
        self.optim_type = optim_type

    def compute_accuracy(self):
        preds = self.model.predict(self.dataset["text"])
        labels = self.dataset["label"]
        accuracy = accuracy_score.compute(predictions=preds, references=labels)
        print(f"Accuracy on test set - {accuracy['accuracy']:.4f}")
        return accuracy

    def compute_size(self):
        state_dict = self.model.model_body.state_dict()
        tmp_path = Path("model.pt")
        torch.save(state_dict, tmp_path)
        # Calculate size in megabytes
        size_mb = Path(tmp_path).stat().st_size / (1024 * 1024)
        # Delete temporary file
        tmp_path.unlink()
        print(f"Model size (MB) - {size_mb:.2f}")
        return {"size_mb": size_mb}

    def time_model(self, query="Fears for T N pension after talks Unions representing workers at Turner   Newall say they are 'disappointed' after talks with stricken parent firm Federal Mogul."):
        latencies = []
        # Warmup
        for i in range(10):
            _ = self.model([query])
        # Timed run
        for i in range(100):
            start_time = perf_counter()
            _ = self.model([query])
            latency = perf_counter() - start_time
            latencies.append(latency)
        # Compute run statistics
        time_avg_ms = 1000 * np.mean(latencies)
        time_std_ms = 1000 * np.std(latencies)
        print(f"Average latency (ms) - {time_avg_ms:.2f} +\- {time_std_ms:.2f}")
        return {"time_avg_ms": time_avg_ms, "time_std_ms": time_std_ms}

    def run_benchmark(self):
        metrics = {}
        metrics[self.optim_type] = self.compute_size()
        metrics[self.optim_type].update(self.compute_accuracy())
        metrics[self.optim_type].update(self.time_model())
        return metrics

num_classes = 4
train_dataset = agnews["train"].shuffle(seed=0).select(range(8 * num_classes))

from setfit import SetFitModel, SetFitTrainer
from sentence_transformers.losses import CosineSimilarityLoss

# Load a SetFit model from Hub
model_id = "paraphrase-mpnet-base-v2"
model = SetFitModel.from_pretrained(model_id)

# Create trainer
teacher_trainer = SetFitTrainer(
    model=model,
    train_dataset=train_dataset,
    eval_dataset=test_dataset,
    loss_class=CosineSimilarityLoss,
    metric="accuracy",
    batch_size=16,
    num_iterations=20, # The number of text pairs to generate for contrastive learning
    num_epochs=1, # The number of epochs to use for constrastive learning
)

# Train and evaluate
teacher_trainer.train()
metrics = teacher_trainer.evaluate()

print(f"model used: {model_id}")
print(f"train dataset: {len(train_dataset)} samples")
print(f"accuracy: {metrics['accuracy']}")

MPNet_teacher_model = teacher_trainer.model.model_body

optim_type = "MPNet_teacher FP32 Torch"
pb = PerformanceBenchmark(
    model=teacher_trainer.model, dataset=test_dataset, optim_type=optim_type
)
perf_metrics = pb.run_benchmark()

from setfit import SetFitModel, SetFitTrainer
from sentence_transformers.losses import CosineSimilarityLoss

# Load a SetFit model from Hub
model_id = 'paraphrase-MiniLM-L3-v2'
model = SetFitModel.from_pretrained(model_id)

# Create trainer
trainer = SetFitTrainer(
    model=model,
    train_dataset=train_dataset,
    eval_dataset=test_dataset,
    loss_class=CosineSimilarityLoss,
    metric="accuracy",
    batch_size=16,
    num_iterations=20, # The number of text pairs to generate for contrastive learning
    num_epochs=1, # The number of epochs to use for constrastive learning
)

# Train and evaluate
trainer.train()
metrics = trainer.evaluate()

print(f"model used: {model_id}")
print(f"train dataset: {len(train_dataset)} samples")
print(f"accuracy: {metrics['accuracy']}")

MiniLM_L3_model = trainer.model.model_body

optim_type = "MiniLM_L3_student FP32 Torch"
pb = PerformanceBenchmark(
    model=trainer.model, dataset=test_dataset, optim_type=optim_type
)
perf_metrics.update(pb.run_benchmark())

from setfit import SetFitModel, SetFitTrainer, DistillationSetFitTrainer

train_dataset_student = agnews["train"].shuffle(seed=0).select(range(1000))

#Distill
student_model = SetFitModel.from_pretrained("sentence-transformers/paraphrase-MiniLM-L3-v2")
teacher_model = teacher_trainer.model
student_trainer = DistillationSetFitTrainer(teacher_model=teacher_model, train_dataset=train_dataset_student, student_model=student_model, eval_dataset=test_dataset, loss_class=CosineSimilarityLoss, metric="accuracy", batch_size=16, num_iterations=20, num_epochs=1)

# Student Train and evaluate
student_trainer.train()
metrics = student_trainer.evaluate()
print("Student results: ", metrics)
MiniLM_L3_distilled_model = student_trainer.model.model_body
MiniLM_L3_distilled_model.save('MiniLM_L3_distilled')

optim_type = "MiniLM_L3_student_distilled FP32 Torch"
pb = PerformanceBenchmark(
    model=student_trainer.model, dataset=test_dataset, optim_type=optim_type
)
perf_metrics.update(pb.run_benchmark())

from optimum.onnxruntime import ORTModelForFeatureExtraction
from transformers import AutoTokenizer
from pathlib import Path

model_id = "MiniLM_L3_distilled"
onnx_path = Path("onnx")

# load vanilla transformers and convert to onnx
model = ORTModelForFeatureExtraction.from_pretrained(model_id, from_transformers=True)
tokenizer = AutoTokenizer.from_pretrained(model_id)

# save onnx checkpoint and tokenizer
model.save_pretrained(onnx_path)
tokenizer.save_pretrained(onnx_path)

from optimum.onnxruntime import ORTOptimizer
from optimum.onnxruntime.configuration import OptimizationConfig

# create ORTOptimizer and define optimization configuration
optimizer = ORTOptimizer.from_pretrained(model)
optimization_config = OptimizationConfig(optimization_level=99) # enable all optimizations

# apply the optimization configuration to the model
optimizer.optimize(
    save_dir=onnx_path,
    optimization_config=optimization_config,
)

def build_static_quant_yaml():
    yaml = """
model:
  name: bert
  framework: onnxrt_qlinearops

quantization:
  approach: post_training_static_quant
  calibration:
    sampling_size: 40

tuning:
  accuracy_criterion:
    relative: 0.01
  exit_policy:
    timeout: 0
  random_seed: 9527
    """
    with open('MiniLM_L3_ST_distilled_onnx_static.yaml', 'w', encoding="utf-8") as f:
        f.write(yaml)

def build_dynamic_quant_yaml():
    yaml = """
model:
  name: bert
  framework: onnxrt_integerops

quantization:
  approach: post_training_dynamic_quant

tuning:
  accuracy_criterion:
    relative: 0.01
  exit_policy:
    timeout: 0
  random_seed: 9527
    """
    with open('MiniLM_L3_ST_distilled_onnx_dynamic.yaml', 'w', encoding="utf-8") as f:
        f.write(yaml)

build_static_quant_yaml()
build_dynamic_quant_yaml()

import os
import onnx
import onnxruntime
from neural_compressor.experimental import Quantization, common
from torch.utils.data import DataLoader
from tqdm import tqdm
from transformers import (
    AutoModel,
    AutoTokenizer,
    TrainingArguments,
    Trainer,
    default_data_collator,
    set_seed,
)

raw_datasets = load_dataset("ag_news")
model_id = "MiniLM_L3_distilled"

tokenizer = AutoTokenizer.from_pretrained(model_id)
model = AutoModel.from_pretrained(model_id)

class OnnxSetFitModel:
    def __init__(self, model, tokenizer):
        self.model = model
        self.tokenizer = tokenizer
        self.predictor = LogisticRegression(max_iter=200)

    def fit_predictor(self, embeddings, labels):
        self.predictor.fit(embeddings, labels)

    def predict(self, embeddings):
        return self.predictor.predict(embeddings)

    def forward(self, model_input):
        model_input = {k:model_input[k].numpy() for k in model_input}
        embedding = self.model.run([self.model.get_outputs()[0].name], model_input)[0]
        token_embeddings = torch.tensor(embedding)
        attention_mask = torch.tensor(model_input['attention_mask'])
        input_mask_expanded = attention_mask.unsqueeze(-1).expand(token_embeddings.size()).float()
        embedding = torch.sum(token_embeddings * input_mask_expanded, 1) / torch.clamp(input_mask_expanded.sum(1), min=1e-9, max=1e9).numpy()
        return embedding

    def __call__(self, inputs):
        model_input = self.tokenizer(
            inputs, padding=True, truncation=True, return_tensors="pt"
        )
        return self.predict(self.forward(model_input))

def preprocess_function(examples):
    # Tokenize the texts
    args = ((examples["text"],))
    result = tokenizer(*args, padding="max_length", max_length=128, truncation=True)
    result["label"] = examples["label"]
    return result

def compute_metrics(preds, labels):
    return (preds == labels).astype(np.float32).mean().item()

num_classes = 4
raw_datasets["train"] = raw_datasets["train"].shuffle(seed=0).select(range(8 * num_classes))
preprocessed_datasets = raw_datasets.map(preprocess_function, batched=True)
preprocessed_datasets = preprocessed_datasets.remove_columns(['text', ])

train_dataset = preprocessed_datasets["train"]
eval_dataset = preprocessed_datasets["test"]

training_args = TrainingArguments(output_dir='tmp')
trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=eval_dataset,
        compute_metrics=compute_metrics,
        tokenizer=tokenizer,
        data_collator=default_data_collator
)

def eval_func(model):
    model = onnxruntime.InferenceSession(model.SerializeToString(), None)
    setfit_model = OnnxSetFitModel(model, tokenizer)
    def get_data(model, dataloader):
        embeddings = []
        labels = []
        for model_input in tqdm(dataloader):
            labels.append(model_input.pop('labels').numpy())
            embedding = model.forward(model_input)
            embedding = embedding.detach().cpu().numpy()
            embeddings.append(embedding)
        return np.concatenate(embeddings, axis=0), np.concatenate(labels, axis=0)
    # train logistic regressor
    embeddings, labels = get_data(setfit_model, trainer.get_train_dataloader())
    sgd = LogisticRegression(max_iter=200)
    sgd.fit(embeddings, labels)

    # evaluate
    embeddings, labels = get_data(setfit_model, trainer.get_eval_dataloader())
    y_pred_test_sgd = sgd.predict(embeddings)

    return compute_metrics(y_pred_test_sgd, labels)

class CalibrationDataset():
    def __init__(self, dataset):
        self.dataset = dataset

    def __getitem__(self, idx):
        data = self.dataset[idx]
        label = data.pop('label')
        return {k:data[k] for k in data}, label

    def __len__(self):
        return len(self.dataset)


model_input = "onnx/model_optimized.onnx"
onnx_int8_model_path = "onnx/MiniLM_L3_int8_static.onnx"
quantizer = Quantization("MiniLM_L3_ST_distilled_onnx_static.yaml")
quantizer.model = common.Model(model_input)
quantizer.eval_func = eval_func
quantizer.calib_dataloader = common.DataLoader(CalibrationDataset(train_dataset), 1)
q_model = quantizer()
q_model.save(onnx_int8_model_path)

onnx_int8_inc_dynamic_model_path = "onnx/MiniLM_L3_int8_dynamic.onnx"
quantizer = Quantization("MiniLM_L3_ST_distilled_onnx_dynamic.yaml")
quantizer.model = common.Model(model_input)
quantizer.eval_func = eval_func
q_model = quantizer()
q_model.save(onnx_int8_inc_dynamic_model_path)

class OnnxPerformanceBenchmark(PerformanceBenchmark):
    def __init__(self, *args, model_path='', **kwargs):
        super().__init__(*args, **kwargs)
        self.model_path = model_path

    def compute_size(self):
        size_mb = Path(self.model_path).stat().st_size / (1024 * 1024)
        print(f"Model size (MB) - {size_mb:.2f}")
        return {"size_mb": size_mb}

    def compute_accuracy(self):
        def get_data(model, dataloader):
            embeddings = []
            labels = []
            for model_input in tqdm(dataloader):
                labels.append(model_input.pop('labels').numpy())
                embedding = model.forward(model_input)
                embeddings.append(embedding)
            return np.concatenate(embeddings, axis=0), np.concatenate(labels, axis=0)
        # train logistic regressor
        embeddings, labels = get_data(self.model, trainer.get_train_dataloader())
        self.model.fit_predictor(embeddings, labels)

        # evaluate
        embeddings, labels = get_data(self.model, trainer.get_eval_dataloader())
        y_pred_test_sgd = self.model.predict(embeddings)
        accuracy = compute_metrics(y_pred_test_sgd, labels)
        print(f"Accuracy on test set - {accuracy:.4f}")
        return {'accuracy': accuracy}

setfit_onnx_int8_static_model_path = 'onnx/MiniLM_L3_int8_static.onnx'
model = onnxruntime.InferenceSession(onnx.load(setfit_onnx_int8_static_model_path).SerializeToString(), None)
setfit_onnx_int8_static_model = OnnxSetFitModel(model, tokenizer)
optim_type = "MiniLM-L3 (distilled + quantized) INT8 Static ONNX"
pb = OnnxPerformanceBenchmark(setfit_onnx_int8_static_model, test_dataset, optim_type, model_path=setfit_onnx_int8_static_model_path)
perf_metrics.update(pb.run_benchmark())

setfit_onnx_int8_dynamic_model_path = 'onnx/MiniLM_L3_int8_dynamic.onnx'
model = onnxruntime.InferenceSession(onnx.load(setfit_onnx_int8_dynamic_model_path).SerializeToString(), None)
setfit_onnx_int8_dynamic_model = OnnxSetFitModel(model, tokenizer)
optim_type = "MiniLM-L3 (distilled + quantized) INT8 Dynamic ONNX"
pb = OnnxPerformanceBenchmark(setfit_onnx_int8_dynamic_model, test_dataset, optim_type, model_path=setfit_onnx_int8_dynamic_model_path)
perf_metrics.update(pb.run_benchmark())

print('\n'.join(["{}: {:.2f}%, {:.2f}ms, {:.2f}MB".format(k, perf_metrics[k]['accuracy']*100, perf_metrics[k]['time_avg_ms'], perf_metrics[k]['size_mb']) for k in perf_metrics]))

example_sentence = "Fears for T N pension after talks Unions representing workers at Turner   Newall say they are 'disappointed' after talks with stricken parent firm Federal Mogul."
predicted_label = setfit_onnx_int8_dynamic_model(example_sentence)
print(f"Given sentence:\n  {example_sentence}")
print(f"Model's prediction on this sentence is:\n  {topics.int2str(predicted_label)[0]}")