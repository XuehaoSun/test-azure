'''
Training script for ImageNet
Copyright (c) Wei YANG, 2017
'''
from __future__ import print_function

import argparse
import os
import random
import shutil
import time
import warnings

import torch
import torch.nn as nn
import torch.nn.parallel
import torch.backends.cudnn as cudnn
import torch.distributed as dist
import torch.optim
import torch.utils.data
import torch.utils.data.distributed
import torchvision.transforms as transforms
import torchvision.datasets as datasets
import torchvision.models as models

import models.imagenet as customized_models
from utils import Bar, Logger, AverageMeter, accuracy, mkdir_p, savefig
from utils.dataloaders import *
from tensorboardX import SummaryWriter

default_model_names = sorted(name for name in models.__dict__
    if name.islower() and not name.startswith("__")
    and callable(models.__dict__[name]))

customized_models_names = sorted(name for name in customized_models.__dict__
    if name.islower() and not name.startswith("__")
    and callable(customized_models.__dict__[name]))

for name in customized_models.__dict__:
    if name.islower() and not name.startswith("__") and callable(customized_models.__dict__[name]):
        models.__dict__[name] = customized_models.__dict__[name]

model_names = default_model_names + customized_models_names


parser = argparse.ArgumentParser(description='PyTorch ImageNet Training')
parser.add_argument('-d', '--data', metavar='DIR',
                    help='path to dataset')
parser.add_argument('--data-backend', metavar='BACKEND', default='pytorch',
                    choices=DATA_BACKEND_CHOICES)
parser.add_argument('-a', '--arch', metavar='ARCH', default='resnet18',
                    choices=model_names,
                    help='model architecture: ' +
                        ' | '.join(model_names) +
                        ' (default: resnet18)')
parser.add_argument('-j', '--workers', default=4, type=int, metavar='N',
                    help='number of data loading workers (default: 4)')
parser.add_argument('--epochs', default=90, type=int, metavar='N',
                    help='number of total epochs to run')
parser.add_argument('--start-epoch', default=0, type=int, metavar='N',
                    help='manual epoch number (useful on restarts)')
parser.add_argument('-b', '--batch-size', default=256, type=int,
                    metavar='N',
                    help='mini-batch size (default: 256), this is the total '
                         'batch size of all GPUs on the current node when '
                         'using Data Parallel or Distributed Data Parallel')
parser.add_argument('--lr', '--learning-rate', default=0.1, type=float,
                    metavar='LR', help='initial learning rate', dest='lr')
parser.add_argument('--momentum', default=0.9, type=float, metavar='M',
                    help='momentum')
parser.add_argument('--wd', '--weight-decay', default=1e-4, type=float,
                    metavar='W', help='weight decay (default: 1e-4)',
                    dest='weight_decay')
parser.add_argument('-p', '--print-freq', default=10, type=int,
                    metavar='N', help='print frequency (default: 10)')
parser.add_argument('--resume', default='', type=str, metavar='PATH',
                    help='path to latest checkpoint (default: none)')
parser.add_argument('-e', '--evaluate', dest='evaluate', action='store_true',
                    help='evaluate model on validation set')
parser.add_argument('--pretrained', dest='pretrained', action='store_true',
                    help='use pre-trained model')
parser.add_argument('--world-size', default=-1, type=int,
                    help='number of nodes for distributed training')
parser.add_argument('--rank', default=-1, type=int,
                    help='node rank for distributed training')
parser.add_argument('--dist-url', default='tcp://224.66.41.62:23456', type=str,
                    help='url used to set up distributed training')
parser.add_argument('--dist-backend', default='nccl', type=str,
                    help='distributed backend')
parser.add_argument('--seed', default=None, type=int,
                    help='seed for initializing training. ')

parser.add_argument('--lr-decay', type=str, default='step',
                    help='mode for learning rate decay')
parser.add_argument('--step', type=int, default=30,
                    help='interval for learning rate decay in step mode')
parser.add_argument('--schedule', type=int, nargs='+', default=[150, 225],
                    help='decrease learning rate at these epochs.')
parser.add_argument('--gamma', type=float, default=0.1,
                    help='LR is multiplied by gamma on schedule.')
parser.add_argument('--warmup', action='store_true',
                    help='set lower initial learning rate to warm up the training')

parser.add_argument('-c', '--checkpoint', default='checkpoints', type=str, metavar='PATH',
                    help='path to save checkpoint (default: checkpoints)')
parser.add_argument('--cuda', action='store_true', default=False, help="Use CUDA")
parser.add_argument('--width-mult', type=float, default=1.0, help='MobileNet model width multiplier.')
parser.add_argument('--input-size', type=int, default=224, help='MobileNet model input resolution')
parser.add_argument('--weight', default='', type=str, metavar='WEIGHT',
                    help='path to pretrained weight (default: none)')
parser.add_argument('--dummy', action='store_true', default=False, help="Use dummy data")
parser.add_argument('--ipex', action='store_true', default=False, help="Use IPEX")
parser.add_argument('--precision', default='float32', help='Precision, "float32" or "bfloat16"')
parser.add_argument('--jit', action='store_true', default=False, help="Use jit script model")
parser.add_argument('--profile', action='store_true', default=False, help="Trigger profile on current topology.")
parser.add_argument('--max_iters', type=int, default=500, help="max iterations to run")
parser.add_argument('--warmup_iters', type=int, default=10, help="iterations to warmup")
parser.add_argument('--HBONet_name', type=str, default='', help="HBOnet name")
parser.add_argument('--channels_last', type=int, default=1, help='use channels last format')
parser.add_argument('--tune', action='store_true', default=False, help="Tune")
parser.add_argument("--tuned_checkpoint", default='./saved_results', type=str, metavar='PATH',
                    help='path to checkpoint tuned by Low Precision Optimization Tool (default: ./)')


best_prec1 = 0

def test_quant_fx_graph(model):
    from torch.quantization import default_qconfig
    from torch.quantization.quantize_fx import prepare_fx, convert_fx, fuse_fx
    model = model.eval()
    qconfig_dict = {"": default_qconfig}
    model = prepare_fx(model, qconfig_dict)
    model = convert_fx(model)
    print(type(model).__name__, " fx mode quantization Pass!")
    return model


    
def main():
    global args, best_prec1
    args = parser.parse_args()
    print(args)

    if args.seed is not None:
        random.seed(args.seed)
        torch.manual_seed(args.seed)
        if args.cuda:
            cudnn.deterministic = True
        warnings.warn('You have chosen to seed training. '
                      'This will turn on the CUDNN deterministic setting, '
                      'which can slow down your training considerably! '
                      'You may see unexpected behavior when restarting '
                      'from checkpoints.')

    args.distributed = args.world_size > 1

    if args.distributed:
        dist.init_process_group(backend=args.dist_backend, init_method=args.dist_url,
                                world_size=args.world_size)

    # create model
    print("=> creating model '{}'".format(args.arch))
    model = models.__dict__[args.arch](width_mult=args.width_mult)

    if args.ipex:
        import intel_pytorch_extension as ipex
        print("Running with IPEX...")
        if args.precision == "bfloat16":
            # Automatically mix precision
            ipex.enable_auto_mixed_precision(mixed_dtype=torch.bfloat16)
            print("Running with bfloat16...")
        model = torch.nn.DataParallel(model).to(ipex.DEVICE)
        if args.jit:
            input = torch.randn(args.batch_size, 3, 224, 224).to(ipex.DEVICE)
            model = torch.jit.trace(model, input)
        args.device = ipex.DEVICE
    elif args.cuda:
        if not args.distributed:
            if args.arch.startswith('alexnet') or args.arch.startswith('vgg'):
                model.features = torch.nn.DataParallel(model.features)
                model.cuda()
            else:
                model = torch.nn.DataParallel(model).cuda()
        else:
            model.cuda()
            model = torch.nn.parallel.DistributedDataParallel(model)
        args.device = torch.device('cuda')
    elif args.channels_last:
        # model = torch.nn.DataParallel(model)
        model_oob = model
        model_oob = model_oob.to(memory_format=torch.channels_last)
        model = model_oob
        args.device = torch.device('cpu')
    else:
        #  model = torch.nn.DataParallel(model)
        args.device = torch.device('cpu')

    # define loss function (criterion) and optimizer
    criterion = nn.CrossEntropyLoss().cuda() if args.cuda else nn.CrossEntropyLoss()
 
    if not args.evaluate:
        optimizer = torch.optim.SGD(model.parameters(), args.lr,
                                    momentum=args.momentum,
                                    weight_decay=args.weight_decay)

    # optionally resume from a checkpoint
    title = 'ImageNet-' + args.arch
    if not os.path.isdir(args.checkpoint):
        mkdir_p(args.checkpoint)

    if args.resume:
        if os.path.isfile(args.resume):
            print("=> loading checkpoint '{}'".format(args.resume))
            checkpoint = torch.load(args.resume)
            args.start_epoch = checkpoint['epoch']
            best_prec1 = checkpoint['best_prec1']
            model.load_state_dict(checkpoint['state_dict'])
            optimizer.load_state_dict(checkpoint['optimizer'])
            print("=> loaded checkpoint '{}' (epoch {})"
                  .format(args.resume, checkpoint['epoch']))
            args.checkpoint = os.path.dirname(args.resume)
            logger = Logger(os.path.join(args.checkpoint, 'log.txt'), title=title, resume=True)
        else:
            print("=> no checkpoint found at '{}'".format(args.resume))
    else:
        logger = Logger(os.path.join(args.checkpoint, 'log.txt'), title=title)
        logger.set_names(['Learning Rate', 'Train Loss', 'Valid Loss', 'Train Acc.', 'Valid Acc.'])

    if args.cuda:
        cudnn.benchmark = True

    if not args.dummy:
    # Data loading code
        if args.data_backend == 'pytorch':
            get_train_loader = get_pytorch_train_loader
            get_val_loader = get_pytorch_val_loader
        elif args.data_backend == 'dali-gpu':
            get_train_loader = get_dali_train_loader(dali_cpu=False)
            get_val_loader = get_dali_val_loader()
        elif args.data_backend == 'dali-cpu':
            get_train_loader = get_dali_train_loader(dali_cpu=True)
            get_val_loader = get_dali_val_loader()

        train_loader, train_loader_len = get_train_loader(args.data, args.batch_size, workers=args.workers, input_size=args.input_size)
        val_loader, val_loader_len = get_val_loader(args.data, args.batch_size, workers=args.workers, input_size=args.input_size)
    else:
        val_loader = None
        val_loader_len = None
    
    if args.evaluate:
        from collections import OrderedDict
        if os.path.isfile(args.weight):
            print("=> loading pretrained weight '{}'".format(args.weight))
            source_state = torch.load(args.weight, map_location=torch.device('cpu'))
            target_state = OrderedDict()
            for k, v in source_state.items():
                # if k[:7] != 'module.':
                #     k = 'module.' + k
                target_state[k] = v
            model.load_state_dict(target_state)
        else:
            print("=> no weight found at '{}'".format(args.weight))
        
        if args.tune:
            from neural_compressor.experimental import Quantization, common
            quantizer = Quantization('../conf.yaml')
            dataset = quantizer.dataset('dummy', (1, 3, 224, 224), label=True)
            quantizer.calib_dataloader = common.DataLoader(dataset)
            quantizer.model = common.Model(model)
            q_model = quantizer()
            q_model.save("saved_results")
            return
        else:
            if args.precision == "int8":
                from neural_compressor.utils.pytorch import load
                model = load("saved_results", model)
            else:
                model = model
        
        validate(val_loader, val_loader_len, model, criterion, args)
        return

    # visualization
    writer = SummaryWriter(os.path.join(args.checkpoint, 'logs'))

    for epoch in range(args.start_epoch, args.epochs):
        if args.distributed:
            train_sampler.set_epoch(epoch)

        print('\nEpoch: [%d | %d]' % (epoch + 1, args.epochs))

        # train for one epoch
        train_loss, train_acc = train(train_loader, train_loader_len, model, criterion, optimizer, epoch)

        # evaluate on validation set
        val_loss, prec1 = validate(val_loader, val_loader_len, model, criterion)

        lr = optimizer.param_groups[0]['lr']

        # append logger file
        logger.append([lr, train_loss, val_loss, train_acc, prec1])

        # tensorboardX
        writer.add_scalar('learning rate', lr, epoch + 1)
        writer.add_scalars('loss', {'train loss': train_loss, 'validation loss': val_loss}, epoch + 1)
        writer.add_scalars('accuracy', {'train accuracy': train_acc, 'validation accuracy': prec1}, epoch + 1)

        is_best = prec1 > best_prec1
        best_prec1 = max(prec1, best_prec1)
        save_checkpoint({
            'epoch': epoch + 1,
            'arch': args.arch,
            'state_dict': model.state_dict(),
            'best_prec1': best_prec1,
            'optimizer' : optimizer.state_dict(),
        }, is_best, checkpoint=args.checkpoint)

    logger.close()
    logger.plot()
    savefig(os.path.join(args.checkpoint, 'log.eps'))
    writer.close()

    print('Best accuracy:')
    print(best_prec1)



def train(train_loader, train_loader_len, model, criterion, optimizer, epoch):
    bar = Bar('Processing', max=train_loader_len)

    batch_time = AverageMeter()
    data_time = AverageMeter()
    losses = AverageMeter()
    top1 = AverageMeter()
    top5 = AverageMeter()

    # switch to train mode
    model.train()

    end = time.time()
    for i, (input, target) in enumerate(train_loader):
        adjust_learning_rate(optimizer, epoch, i, train_loader_len)

        # measure data loading time
        data_time.update(time.time() - end)

        target = target.cuda(non_blocking=True)

        # compute output
        output = model(input)
        loss = criterion(output, target)

        # measure accuracy and record loss
        prec1, prec5 = accuracy(output, target, topk=(1, 5))
        losses.update(loss.item(), input.size(0))
        top1.update(prec1.item(), input.size(0))
        top5.update(prec5.item(), input.size(0))

        # compute gradient and do SGD step
        optimizer.zero_grad()
        loss.backward()
        optimizer.step()

        # measure elapsed time
        batch_time.update(time.time() - end)
        end = time.time()

        # plot progress
        bar.suffix  = '({batch}/{size}) Data: {data:.3f}s | Batch: {bt:.3f}s | Total: {total:} | ETA: {eta:} | Loss: {loss:.4f} | top1: {top1: .4f} | top5: {top5: .4f}'.format(
                    batch=i + 1,
                    size=train_loader_len,
                    data=data_time.avg,
                    bt=batch_time.avg,
                    total=bar.elapsed_td,
                    eta=bar.eta_td,
                    loss=losses.avg,
                    top1=top1.avg,
                    top5=top5.avg,
                    )
        bar.next()
    bar.finish()
    return (losses.avg, top1.avg)


def validate(val_loader, val_loader_len, model, criterion, args=None):
    bar = Bar('Processing',
        max=val_loader_len if (val_loader_len is not None) else args.max_iters + args.warmup_iters)

    batch_time = AverageMeter()
    data_time = AverageMeter()
    losses = AverageMeter()
    top1 = AverageMeter()
    top5 = AverageMeter()

    # switch to evaluate mode
    model.eval()

    if args.dummy:
        if args.channels_last:
            input = torch.randn(args.batch_size, 3 , 224, 224)
            input_oob = input
            input_oob = input_oob.to(memory_format=torch.channels_last)
            input = input_oob
        else:
            input = torch.randn(args.batch_size, 3 , 224, 224).to(args.device)
        for i in range(args.max_iters + args.warmup_iters):
            if i >= args.warmup_iters:
                start = time.time()
            with torch.no_grad():
                if args.profile and i == args.max_iters + args.warmup_iters -1:
                    if args.precision == "bfloat16":
                        with torch.cpu.amp.autocast(enabled=True, dtype=torch.bfloat16):
                            with torch.autograd.profiler.profile(use_cuda=False) as prof:
                                output = model(input)
                    else:
                        with torch.autograd.profiler.profile(use_cuda=False) as prof:
                            output = model(input)
                        # print(profkey_averages().table(sort_by="self_cpu_time_total"))
                    table_res = prof.key_averages().table(sort_by="cpu_time_total")
                    timeline_path = "timeline_logs/hbonet" + str(args.width_mult) + "/"
                    if not os.path.exists(timeline_path):
                        os.makedirs(timeline_path)
                    save_profile_result(timeline_path + torch.backends.quantized.engine + args.precision + "_result_average.xlsx", table_res)
                else:
                    if args.precision == "bfloat16":
                        with torch.cpu.amp.autocast(enabled=True, dtype=torch.bfloat16):
                            output = model(input)
                    else:
                        output = model(input)

                #output = model(input)
            # measure elapsed time
            if i >= args.warmup_iters:
                batch_time.update(time.time() - start)
            bar.suffix  = '({batch}/{size}) Data: {data:.3f}s | Batch: {bt:.3f}s | Total: {total:} | ETA: {eta:}'.format(
                        batch=i + 1,
                        size=args.max_iters + args.warmup_iters,
                        data=data_time.avg,
                        bt=batch_time.avg,
                        total=bar.elapsed_td,
                        eta=bar.eta_td,
                        )
            bar.next()
    else:
        end = time.time()
        for i, (input, target) in enumerate(val_loader):
            # measure data loading time
            data_time.update(time.time() - end)

            target = target.cuda(non_blocking=True)

            with torch.no_grad():
                # compute output
                output = model(input)
                loss = criterion(output, target)

            # measure accuracy and record loss
            prec1, prec5 = accuracy(output, target, topk=(1, 5))
            losses.update(loss.item(), input.size(0))
            top1.update(prec1.item(), input.size(0))
            top5.update(prec5.item(), input.size(0))

            # measure elapsed time
            batch_time.update(time.time() - end)
            end = time.time()

            # plot progress
            bar.suffix  = '({batch}/{size}) Data: {data:.3f}s | Batch: {bt:.3f}s | Total: {total:} | ETA: {eta:} | Loss: {loss:.4f} | top1: {top1: .4f} | top5: {top5: .4f}'.format(
                        batch=i + 1,
                        size=val_loader_len,
                        data=data_time.avg,
                        bt=batch_time.avg,
                        total=bar.elapsed_td,
                        eta=bar.eta_td,
                        loss=losses.avg,
                        top1=top1.avg,
                        top5=top5.avg,
                        )
            bar.next()
    bar.finish()
    latency = batch_time.avg / args.batch_size * 1000
    perf = args.batch_size/batch_time.avg
    print('Latency: %3.3f ms'%latency)
    print('inference Throughput: %3.3f fps'%perf)
    return (losses.avg, top1.avg)


def save_checkpoint(state, is_best, checkpoint='checkpoint', filename='checkpoint.pth.tar'):
    filepath = os.path.join(checkpoint, filename)
    torch.save(state, filepath)
    if is_best:
        shutil.copyfile(filepath, os.path.join(checkpoint, 'model_best.pth.tar'))


from math import cos, pi
def adjust_learning_rate(optimizer, epoch, iteration, num_iter):
    lr = optimizer.param_groups[0]['lr']

    warmup_epoch = 5 if args.warmup else 0
    warmup_iter = warmup_epoch * num_iter
    current_iter = iteration + epoch * num_iter
    max_iter = args.epochs * num_iter

    if args.lr_decay == 'step':
        lr = args.lr * (args.gamma ** ((current_iter - warmup_iter) // (max_iter - warmup_iter)))
    elif args.lr_decay == 'cos':
        lr = args.lr * (1 + cos(pi * (current_iter - warmup_iter) / (max_iter - warmup_iter))) / 2
    elif args.lr_decay == 'linear':
        lr = args.lr * (1 - (current_iter - warmup_iter) / (max_iter - warmup_iter))
    elif args.lr_decay == 'schedule':
        count = sum([1 for s in args.schedule if s <= epoch])
        lr = args.lr * pow(args.gamma, count)
    else:
        raise ValueError('Unknown lr mode {}'.format(args.lr_decay))

    if epoch < warmup_epoch:
        lr = args.lr * current_iter / warmup_iter


    for param_group in optimizer.param_groups:
        param_group['lr'] = lr
def save_profile_result(filename, table):
    import xlsxwriter
    workbook = xlsxwriter.Workbook(filename)
    worksheet = workbook.add_worksheet()
    keys = ["Name", "Self CPU total %", "Self CPU total", "CPU total %" , "CPU total", \
            "CPU time avg", "Number of Calls"]
    for j in range(len(keys)):
        worksheet.write(0, j, keys[j])

    lines = table.split("\n")
    for i in range(3, len(lines)-4):
        words = lines[i].split(" ")
        j = 0
        for word in words:
            if not word == "":
                worksheet.write(i-2, j, word)
                j += 1
    workbook.close()


if __name__ == '__main__':
    main()
