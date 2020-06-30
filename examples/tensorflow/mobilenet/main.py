#
# -*- coding: utf-8 -*-
#
# Copyright (c) 2020 Intel Corporation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

#
# Copyright 2017 The TensorFlow Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==============================================================================

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import argparse
import sys
import os
import time
import numpy as np

from google.protobuf import text_format
import tensorflow as tf
import accuracy_preprocessing as preprocessing
import accuracy_datasets as datasets

import argparse
import sys
import os
from tensorflow.python.tools.optimize_for_inference_lib import optimize_for_inference
from tensorflow.python.framework import dtypes
import tracemalloc
sys.path.append(os.path.dirname(os.path.dirname(os.path.dirname(os.getcwd()))))

from ilit import tuner as iLit

NUM_TEST_IMAGES = 50000

class Dataloader(object):
    '''
    This is a example class that wrapped the model specified parameters,
    such as dataset path, batch size.
    And more importantly, it provides the ability to iterate the dataset.
    '''
    def __init__(self, data_location, subset, input_height, input_width,
                 batch_size):
        self.batch_size = batch_size
        self.subset = subset
        self.dataset = datasets.ImagenetData(data_location)
        self.total_image = self.dataset.num_examples_per_epoch(self.subset)
        self.preprocessor = self.dataset.get_image_preprocessor()(
            input_height,
            input_width,
            batch_size,
            1,
            tf.float32,
            train=False,
            resize_method='bilinear')
        self.n = int(self.total_image / self.batch_size)
        self.n = 50

    def __iter__(self):
        with tf.compat.v1.get_default_graph().as_default():
            images, labels = self.preprocessor.minibatch(self.dataset, subset=self.subset,
                             use_datasets=True, cache_data=False)
            with tf.compat.v1.Session() as sess:
                for i in range(self.n):
                    yield sess.run([images[0], labels[0]])

def load_graph(model_file):
  graph = tf.Graph()
  graph_def = tf.compat.v1.GraphDef()

  import os
  file_ext = os.path.splitext(model_file)[1]

  with open(model_file, "rb") as f:
    if file_ext == '.pbtxt':
      text_format.Merge(f.read(), graph_def)
    else:
      graph_def.ParseFromString(f.read())
  with graph.as_default():
    tf.import_graph_def(graph_def, name='')

  return graph

def inference(graph, args, batch_size):
    input_height = args.input_height
    input_width = args.input_width
    input_layer = args.input_layer
    output_layer = args.output_layer
    num_inter_threads = args.num_inter_threads
    num_intra_threads = args.num_intra_threads
    data_location = args.data_location
    warm_up_steps = 5
    iteration = 0
    total_time = 0

    dataset = datasets.ImagenetData(data_location)
    preprocessor = dataset.get_image_preprocessor()(
        input_height, input_width, batch_size,
        1, # device count
        tf.float32, # data_type for input fed to the graph
        train=False, # doing inference
        resize_method='bilinear')
    with tf.compat.v1.get_default_graph().as_default():
        images, labels = preprocessor.minibatch(dataset, subset='validation',
                                                use_datasets=True, cache_data=False)
    input_tensor = graph.get_tensor_by_name(input_layer + ":0")
    output_tensor = graph.get_tensor_by_name(output_layer + ":0")

    config = tf.compat.v1.ConfigProto()
    config.inter_op_parallelism_threads = num_inter_threads
    config.intra_op_parallelism_threads = num_intra_threads

    total_accuracy1, total_accuracy5 = (0.0, 0.0)
    num_processed_images = 0
    num_remaining_images = dataset.num_examples_per_epoch(subset='validation') \
                           - num_processed_images
    with tf.compat.v1.Session(graph=tf.compat.v1.get_default_graph()) as sess:
        sess_graph = tf.compat.v1.Session(graph=graph, config=config)
        while num_remaining_images >= batch_size:
            iteration += 1
            # Reads and preprocess data
            np_images, np_labels = sess.run([images[0], labels[0]])
            num_processed_images += batch_size
            num_remaining_images -= batch_size
            start_time = time.time()
            # Compute inference on the preprocessed data
            predictions = sess_graph.run(output_tensor,
                                         {input_tensor: np_images})
            elapsed_time = time.time() - start_time
            accuracy1 = tf.reduce_sum(
                input_tensor=tf.cast(tf.nn.in_top_k(predictions=tf.constant(predictions),
                                                    targets=tf.constant(np_labels), k=1), tf.float32))

            accuracy5 = tf.reduce_sum(
                input_tensor=tf.cast(tf.nn.in_top_k(predictions=tf.constant(predictions),
                                                    targets=tf.constant(np_labels), k=5), tf.float32))
            np_accuracy1, np_accuracy5 =  sess.run([accuracy1, accuracy5])
            total_accuracy1 += np_accuracy1
            total_accuracy5 += np_accuracy5
            if iteration > warm_up_steps:
                total_time += elapsed_time

        top1 = total_accuracy1 / num_processed_images
        average_time = total_time / (iteration - warm_up_steps)

    return top1, average_time


if __name__ == "__main__":
  parser = argparse.ArgumentParser()
  parser.add_argument('--config', type=str, default='')
  parser.add_argument("--input_graph", default=None,
                      help="graph/model to be executed")
  parser.add_argument("--data_location", default=None,
                      help="full path to the validation data")
  parser.add_argument("--input_height", default=224,
                      type=int, help="input height")
  parser.add_argument("--input_width", default=224,
                      type=int, help="input width")
  parser.add_argument("--batch_size", default=32,
                      type=int, help="batch size")
  parser.add_argument('--num_batches', type=int, default=100, help='iteration')
  parser.add_argument("--input_layer", default="input",
                      help="name of input layer")
  parser.add_argument("--output_layer", default="MobilenetV1/Predictions/Reshape_1",
                      help="name of output layer")
  parser.add_argument(
      '--num_inter_threads',
      help='number threads across operators',
      type=int, default=2)
  parser.add_argument(
      '--num_intra_threads',
      help='number threads for an operator',
      type=int, default=28)
  parser.add_argument('--fp32_benchmark', dest='fp32_benchmark', action='store_true', help='run benchmark')
  parser.add_argument('--tune', dest='tune', action='store_true', help='use ilit to tune.')

  args = parser.parse_args()

  if args.input_graph:
    model_file = args.input_graph
  else:
    sys.exit("Please provide a graph file.")

  fp32_graph = load_graph(args.input_graph)
  at = iLit.Tuner(args.config)
  dataloader = Dataloader(args.data_location, 'validation',
                          args.input_height, args.input_width,
                          args.batch_size)
  mob_input_output = {
      "inputs": args.input_layer.split(' '),
      "outputs": args.output_layer.split(' ')}

  if args.tune:
      start_tune = time.time()
      q_model = at.tune(
          fp32_graph,
          q_dataloader=dataloader,
          # eval_func=eval_inference)
          eval_func=None,
          eval_dataloader=dataloader,
          model_specific_cfg=mob_input_output)
      end_tune = time.time()
      print("Tuning time spend: %.1f s" % (end_tune-start_tune))

      bs = 100
      tracemalloc.start()
      top1, batch_time = inference(q_model, args, batch_size=bs)
      _, peak = tracemalloc.get_traced_memory()
      tracemalloc.stop()

      print("q_model accuracy batch_size: %d" % bs)
      print("q_model accuracy: %.3f " % top1)
      print("q_model throughput batch_size: %d" % bs)
      print("q_model throughput: %.3f images/sec" % (bs / batch_time))

  if args.fp32_benchmark:
      # for accuracy
      infer_graph = tf.Graph()
      with infer_graph.as_default():
          graph_def = tf.compat.v1.GraphDef()
          with tf.compat.v1.gfile.FastGFile(args.input_graph, 'rb') as input_file:
              input_graph_content = input_file.read()
              graph_def.ParseFromString(input_graph_content)

          output_graph = optimize_for_inference(graph_def, [args.input_layer],
                                                [args.output_layer], dtypes.float32.as_datatype_enum, False)
          tf.import_graph_def(output_graph, name='')

      bs = 100
      top1, batch_time = inference(infer_graph, args, batch_size=bs)
      print("input_model accuracy batch_size: %d" % bs)
      print("input_model accuracy: %.3f " % top1)
      print("input_model throughput batch_size: %d" % bs)
      print("input_model throughput: %.3f images/sec" % (bs / batch_time))