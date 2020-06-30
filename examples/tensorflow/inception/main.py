#
#  -*- coding: utf-8 -*-
#
#  Copyright (c) 2020 Intel Corporation
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
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

import tensorflow as tf
import preprocessing
import datasets
from tensorflow.core.framework import graph_pb2

from google.protobuf import text_format
import argparse
import sys
import os
import time
from tensorflow.python.tools.optimize_for_inference_lib import optimize_for_inference
from tensorflow.python.framework import dtypes
import tracemalloc
sys.path.append(os.path.dirname(os.path.dirname(os.path.dirname(os.getcwd()))))

from ilit import tuner as iLit


def load_graph(model_file):

    graph = tf.Graph()
    graph_def = tf.GraphDef()

    if not isinstance(model_file, graph_pb2.GraphDef):
        file_ext = os.path.splitext(model_file)[1]

        with open(model_file, "rb") as f:
            if file_ext == '.pbtxt':
                text_format.Merge(f.read(), graph_def)
            else:
                graph_def.ParseFromString(f.read())

        with graph.as_default():
            tf.import_graph_def(graph_def, name='')
    else:
        with graph.as_default():
            tf.import_graph_def(model_file, name='')

    return graph


def prepare_dataloader(data_location, input_height, input_width, batch_size):
    dataset = datasets.ImagenetData(data_location)
    preprocessor = preprocessing.ImagePreprocessor(
        input_height,
        input_width,
        1,
        batch_size,  # device count
        tf.float32,  # data_type for input fed to the graph
        train=False,  # doing inference
        resize_method='crop')
    images, labels = preprocessor.minibatch(dataset, subset='validation')
    return images, labels


def inference(graph, args, batch_size=1):
    input_layer = args.inputs
    output_layer = args.outputs
    num_inter_threads = args.num_inter_threads
    num_intra_threads = args.num_intra_threads
    num_batches = 1000/batch_size
    num_processed_images = 0
    batch_size = batch_size
    warm_up_steps = 5
    iteration = 0
    total_time = 0

    input_tensor = graph.get_tensor_by_name(input_layer + ":0")
    output_tensor = graph.get_tensor_by_name(output_layer + ":0")

    config = tf.ConfigProto()
    config.inter_op_parallelism_threads = num_inter_threads
    config.intra_op_parallelism_threads = num_intra_threads

    if num_batches > 0:
        num_remaining_images = batch_size * num_batches

    total_accuracy1, total_accuracy5 = (0.0, 0.0)
    dataset = datasets.ImagenetData(args.data_location)
    preprocessor = preprocessing.ImagePreprocessor(
        args.input_height,
        args.input_width,
        batch_size,
        1,  # device count
        tf.float32,  # data_type for input fed to the graph
        train=False,  # doing inference
        resize_method='crop')
    images, labels = preprocessor.minibatch(dataset, subset='validation')

    with tf.Session() as sess:
        sess_graph = tf.Session(graph=graph, config=config)
        while num_remaining_images >= batch_size:
            iteration += 1
            # Reads and preprocess data
            np_images, np_labels = sess.run([images[0], labels[0]])
            num_processed_images += batch_size
            num_remaining_images -= batch_size
            # Compute inference on the preprocessed data
            start_time = time.time()
            predictions = sess_graph.run(output_tensor,
                                         {input_tensor: np_images})
            time_consume = time.time() - start_time
            # print("Evaluate Processed %d images."% (num_processed_images))
            accuracy1 = tf.reduce_sum(
                tf.cast(
                    tf.nn.in_top_k(tf.constant(predictions),
                                   tf.constant(np_labels), 1), tf.float32))

            accuracy5 = tf.reduce_sum(
                tf.cast(
                    tf.nn.in_top_k(tf.constant(predictions),
                                   tf.constant(np_labels), 5), tf.float32))

            np_accuracy1, np_accuracy5 = sess.run([accuracy1, accuracy5])
            total_accuracy1 += np_accuracy1
            total_accuracy5 += np_accuracy5

            if iteration > warm_up_steps:
                total_time += time_consume
        top1 = total_accuracy1 / num_processed_images
        average_time = total_time / (iteration - warm_up_steps)

    return top1, average_time


class Dataloader:
    def __init__(self, data_location, subset, input_height, input_width,
                 batch_size):
        self.batch_size = batch_size
        self.subset = subset
        self.dataset = datasets.ImagenetData(data_location)
        self.total_image = self.dataset.num_examples_per_epoch(self.subset)
        self.preprocessor = preprocessing.RecordInputImagePreprocessor(
            input_height,
            input_width,
            batch_size,
            28)
        self.n = int(self.total_image / self.batch_size)
        print('original n ', self.n)
        self.n = 100
    def __iter__(self):
        images, labels = self.preprocessor.minibatch(self.dataset, self.subset)
        with tf.compat.v1.Session() as sess:
            for i in range(self.n):
                yield sess.run([images, labels])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(
        description='Tensorflow Resnet50-v1.0 demo for iLit')
    parser.add_argument('--input_graph', type=str, default='')
    parser.add_argument('--config', type=str, default='')
    parser.add_argument('--inputs', type=str, default='input', help='input tensor')
    parser.add_argument('--outputs',
                        type=str,
                        required='',
                        help='output tensor')
    parser.add_argument('--data_location',
                        type=str,
                        required='',
                        help='param file path')
    parser.add_argument('--input_height',
                        type=int,
                        default=224,
                        help='input height')
    parser.add_argument('--input_width',
                        type=int,
                        default=224,
                        help='output height')
    parser.add_argument('--batch_size', type=int, default=1)
    parser.add_argument('--num_batches', type=int, default=100)
    parser.add_argument('--num_inter_threads', type=int, default=2)
    parser.add_argument('--num_intra_threads', type=int, default=28)
    parser.add_argument('--fp32_benchmark', dest='fp32_benchmark', action='store_true', help='run benchmark')
    parser.add_argument('--tune', dest='tune', action='store_true', help='use ilit to tune.')

    args = parser.parse_args()

    fp32_graph = load_graph(args.input_graph)

    if args.tune:
        at = iLit.Tuner(args.config)
        # dataloader = prepare_dataloader(data_location=args.data_location, input_height=args.input_height, input_width=args.input_width, batch_size=args.batch_size)

        dataloader = Dataloader(args.data_location, 'validation',
                                args.input_height, args.input_width,
                                args.batch_size)

        rn50_input_output = {
            "inputs": args.inputs.split(' '),
            "outputs": args.outputs.split(' '),
            "num_batches": args.num_batches
        }
        start_tune = time.time()
        q_model = at.tune(
            fp32_graph,
            q_dataloader=dataloader,
            # eval_func=eval_inference, model_specific_cfg=rn50_input_output)
            eval_func=None,
            eval_dataloader=dataloader,
            model_specific_cfg=rn50_input_output)
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

            output_graph = optimize_for_inference(graph_def, [args.inputs],
                                                  [args.outputs], dtypes.float32.as_datatype_enum, False)
            tf.import_graph_def(output_graph, name='')

        bs = 100
        top1, batch_time = inference(infer_graph, args, batch_size=bs)
        print("input_model accuracy batch_size: %d" % bs)
        print("input_model accuracy: %.3f " % top1)
        print("input_model throughput batch_size: %d" % bs)
        print("input_model throughput: %.3f images/sec" % (bs / batch_time))