import argparse
import os
import numpy as np
import math

import torchvision.transforms as transforms
from torchvision.utils import save_image

from torch.utils.data import DataLoader
from torchvision import datasets
from torch.autograd import Variable

import torch.nn as nn
import torch.nn.functional as F
import torch
import time

os.makedirs("images", exist_ok=True)

parser = argparse.ArgumentParser()
parser.add_argument("--n_epochs", type=int, default=200, help="number of epochs of training")
parser.add_argument("--batch_size", type=int, default=64, help="size of the batches")
parser.add_argument("--lr", type=float, default=0.0002, help="adam: learning rate")
parser.add_argument("--b1", type=float, default=0.5, help="adam: decay of first order momentum of gradient")
parser.add_argument("--b2", type=float, default=0.999, help="adam: decay of first order momentum of gradient")
parser.add_argument("--n_cpu", type=int, default=8, help="number of cpu threads to use during batch generation")
parser.add_argument("--latent_dim", type=int, default=100, help="dimensionality of the latent space")
parser.add_argument("--n_classes", type=int, default=10, help="number of classes for dataset")
parser.add_argument("--img_size", type=int, default=32, help="size of each image dimension")
parser.add_argument("--channels", type=int, default=1, help="number of image channels")
parser.add_argument("--sample_interval", type=int, default=400, help="interval between image sampling")
parser.add_argument('--inference', action='store_true', default=False)
parser.add_argument('--num-iterations', default=10000, type=int)
parser.add_argument('--ipex', action='store_true', default=False)
parser.add_argument('--precision', default='float32', help='Precision, "float32" or "bfloat16"')
parser.add_argument('--channels_last', type=int, default=1, help='use channels last format')
parser.add_argument('--tune', action='store_true', default=False, help="Tune")
parser.add_argument('--profile', action='store_true', default=False, help="Profile")
opt = parser.parse_args()
print(opt)

img_shape = (opt.channels, opt.img_size, opt.img_size)

cuda = True if torch.cuda.is_available() else False
FloatTensor = torch.cuda.FloatTensor if cuda else torch.FloatTensor
LongTensor = torch.cuda.LongTensor if cuda else torch.LongTensor

def test_quant_fx_graph(model):
    from torch.quantization import default_qconfig
    from torch.quantization.quantize_fx import prepare_fx, convert_fx, fuse_fx
    model = model.eval()
    qconfig_dict = {"": default_qconfig}
    model = prepare_fx(model, qconfig_dict)
    model = convert_fx(model)
    print(type(model).__name__, " fx mode quantization Pass!")
    return model

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

class DataLoader(object):
    def __init__(self, data=None, batch_size=1):
        self.data = data
        self.batch_size = batch_size
    def __iter__(self):
        yield self.data, self.data[1]


class Generator(nn.Module):
    def __init__(self):
        super(Generator, self).__init__()

        self.label_emb = nn.Embedding(opt.n_classes, opt.n_classes)

        def block(in_feat, out_feat, normalize=True):
            layers = [nn.Linear(in_feat, out_feat)]
            if normalize:
                layers.append(nn.BatchNorm1d(out_feat, 0.8))
            layers.append(nn.LeakyReLU(0.2, inplace=True))
            return layers

        self.model = nn.Sequential(
            *block(opt.latent_dim + opt.n_classes, 128, normalize=False),
            *block(128, 256),
            *block(256, 512),
            *block(512, 1024),
            nn.Linear(1024, int(np.prod(img_shape))),
            nn.Tanh()
        )

    def forward(self, noise, labels):
        # Concatenate label embedding and image to produce input
        print('shape', noise.shape, self.label_emb(labels).shape)
        gen_input = torch.cat((self.label_emb(labels), noise), -1)
        img = self.model(gen_input)
        img = img.view(img.size(0), *img_shape)
        return img


class Discriminator(nn.Module):
    def __init__(self):
        super(Discriminator, self).__init__()

        self.label_embedding = nn.Embedding(opt.n_classes, opt.n_classes)

        self.model = nn.Sequential(
            nn.Linear(opt.n_classes + int(np.prod(img_shape)), 512),
            nn.LeakyReLU(0.2, inplace=True),
            nn.Linear(512, 512),
            nn.Dropout(0.4),
            nn.LeakyReLU(0.2, inplace=True),
            nn.Linear(512, 512),
            nn.Dropout(0.4),
            nn.LeakyReLU(0.2, inplace=True),
            nn.Linear(512, 1),
        )

    def forward(self, img, labels):
        # Concatenate label embedding and image to produce input
        d_in = torch.cat((img.view(img.size(0), -1), self.label_embedding(labels)), -1)
        validity = self.model(d_in)
        return validity


# Loss functions
adversarial_loss = torch.nn.MSELoss()

# Initialize generator and discriminator
generator = Generator()
discriminator = Discriminator()

if cuda:
    generator.cuda()
    discriminator.cuda()
    adversarial_loss.cuda()

# Configure data loader
os.makedirs("../../data/mnist", exist_ok=True)
dataloader = torch.utils.data.DataLoader(
    datasets.MNIST(
        "../../data/mnist",
        train=True,
        download=True,
        transform=transforms.Compose(
            [transforms.Resize(opt.img_size), transforms.ToTensor(), transforms.Normalize([0.5], [0.5])]
        ),
    ),
    batch_size=opt.batch_size,
    shuffle=True,
)

# from torch.fx import symbolic_trace
# traced = symbolic_trace(generator)
# torch.save(traced, "traced_cgan-generator.pth")
# print('saved trace')
# try:
#     q_model = torch.jit.script(generator.eval())
# except:
#     try:
#         for input,label in dataloader:
#             print(label)
#             q_model = torch.jit.trace(generator.eval(), (input, label))
#             break
#     except:
#         logger.info("This model can't convert to Script model")
# q_model.save("jit_cgan-generator.pth")
# print('saved jit')
# print(0/0)
# from torch.fx import symbolic_trace
# traced = symbolic_trace(discriminator)
# torch.save(traced, "traced_cgan-discriminator.pth")
# print('saved trace')
# try:
#     q_model = torch.jit.script(discriminator.eval())
# except:
#     try:
#         for input, _ in dataloader:
#             q_model = torch.jit.trace(discriminator.eval(), input)
#             break
#     except:
#         logger.info("This model can't convert to Script model")
# q_model.save("jit_cgan-discriminator.pth")
# print('saved jit')
# print(0/0)

# Optimizers
optimizer_G = torch.optim.Adam(generator.parameters(), lr=opt.lr, betas=(opt.b1, opt.b2))
optimizer_D = torch.optim.Adam(discriminator.parameters(), lr=opt.lr, betas=(opt.b1, opt.b2))

FloatTensor = torch.cuda.FloatTensor if cuda else torch.FloatTensor
LongTensor = torch.cuda.LongTensor if cuda else torch.LongTensor


def sample_image(n_row, batches_done):
    """Saves a grid of generated digits ranging from 0 to n_classes"""
    # Sample noise
    z = Variable(FloatTensor(np.random.normal(0, 1, (n_row ** 2, opt.latent_dim))))
    # Get labels ranging from 0 to n_classes for n rows
    labels = np.array([num for _ in range(n_row) for num in range(n_row)])
    labels = Variable(LongTensor(labels))
    gen_imgs = generator(z, labels)
    save_image(gen_imgs.data, "images/%d.png" % batches_done, nrow=n_row, normalize=True)


def generate(netG, batchsize, device):
    if opt.tune:
        from neural_compressor.experimental import Quantization, common
        quantizer = Quantization(('../../conf.yaml'))
        n_row = 10
        fixed_noise = Variable(FloatTensor(np.random.normal(0, 1, (n_row ** 2, opt.latent_dim))))
        labels = np.array([num for _ in range(n_row) for num in range(n_row)])
        labels = Variable(LongTensor(labels))
        dataset = (fixed_noise, labels)
        calib_dataloader = DataLoader(dataset)
        quantizer.calib_dataloader = calib_dataloader
        quantizer.model = common.Model(netG)
        q_model = quantizer()
        q_model.save("saved_results")
        return
    else:
        if opt.precision == 'int8':
            from neural_compressor.utils.pytorch import load
            netG = load("saved_results", netG)
    # print(netG_int8)
    n_row = 10
    fixed_noise = Variable(FloatTensor(np.random.normal(0, 1, (n_row ** 2, opt.latent_dim))))
#     # Get labels ranging from 0 to n_classes for n rows
    labels = np.array([num for _ in range(n_row) for num in range(n_row)])
    labels = Variable(LongTensor(labels))

    # fixed_noise = Variable(FloatTensor(np.random.normal(0, 1, (1, opt.latent_dim)))) #Variable(FloatTensor(np.random.normal(0, 1, (img_shape[0], opt.latent_dim))))
    dry_run = 5
    netG.eval()

    if opt.channels_last:
        netG_oob, fixed_noise_oob = netG, fixed_noise
        netG_oob = netG_oob.to(memory_format=torch.channels_last)
        try:
            fixed_noise_oob = fixed_noise_oob.to(memory_format=torch.channels_last)
        except:
            print("Input NHWC failed! Use normal input.")
        netG, fixed_noise = netG_oob, fixed_noise_oob
    else:
        netG = netG.to(device=device)
        fixed_noise = fixed_noise.to(device=device)
    # if opt.jit:
    #     netG = torch.jit.trace(netG, fixed_noise)
    tic = 0
    
    with torch.no_grad():
        for i in range(dry_run + opt.num_iterations):
            if i == dry_run:
                fake = netG(fixed_noise, labels)
                tic = time.time()
            elif i > dry_run:
                fake = netG(fixed_noise, labels)
                if opt.profile and i == dry_run+opt.num_iterations-1:
                    if opt.precision == "bfloat16":
                        with torch.cpu.amp.autocast(enabled=True, dtype=torch.bfloat16):
                            with torch.autograd.profiler.profile() as prof:
                                fake = netG(fixed_noise, labels)
                    else:
                        with torch.autograd.profiler.profile() as prof:
                            fake = netG(fixed_noise, labels)
                        print(prof.key_averages().table(sort_by="self_cpu_time_total"))
                    table_res = prof.key_averages().table(sort_by="cpu_time_total")
                    timeline_path = "timeline_logs/cgan/"
                    if not os.path.exists(timeline_path):
                        os.makedirs(timeline_path)
                    save_profile_result(timeline_path + torch.backends.quantized.engine + opt.precision+"_result_average.xlsx", table_res)
                # else:
                #     if opt.precision == "bfloat16":
                #         with torch.cpu.amp.autocast(enabled=True, dtype=torch.bfloat16):
                #             fake = netG(fixed_noise)
                #     else:
                #         fake = netG(fixed_noise)
            else:
                fake = netG(fixed_noise, labels)
        
            # print(prof.key_averages().table(sort_by="self_cpu_time_total"))


    toc = time.time() - tic
    if opt.profile:
        print(prof.key_averages().table(sort_by="self_cpu_time_total"))
    print("Throughput: %.2f images/sec, batchsize: %d" % ((opt.num_iterations*batchsize)/toc, batchsize))
    print("Latency: %.2f ms" % (1000*toc/opt.num_iterations))

if opt.inference:
    print("----------------Generation benchmarking---------------")
    generate(generator, opt.batch_size, device=torch.device('cpu'))
    import sys
    sys.exit(0)


# ----------
#  Training
# ----------

# for epoch in range(opt.n_epochs):
#     for i, (imgs, labels) in enumerate(dataloader):

#         batch_size = imgs.shape[0]

#         # Adversarial ground truths
#         valid = Variable(FloatTensor(batch_size, 1).fill_(1.0), requires_grad=False)
#         fake = Variable(FloatTensor(batch_size, 1).fill_(0.0), requires_grad=False)

#         # Configure input
#         real_imgs = Variable(imgs.type(FloatTensor))
#         labels = Variable(labels.type(LongTensor))

#         # -----------------
#         #  Train Generator
#         # -----------------

#         optimizer_G.zero_grad()

#         # Sample noise and labels as generator input
#         z = Variable(FloatTensor(np.random.normal(0, 1, (batch_size, opt.latent_dim))))
#         gen_labels = Variable(LongTensor(np.random.randint(0, opt.n_classes, batch_size)))

#         # Generate a batch of images
#         gen_imgs = generator(z, gen_labels)

#         # Loss measures generator's ability to fool the discriminator
#         validity = discriminator(gen_imgs, gen_labels)
#         g_loss = adversarial_loss(validity, valid)

#         g_loss.backward()
#         optimizer_G.step()

#         # ---------------------
#         #  Train Discriminator
#         # ---------------------

#         optimizer_D.zero_grad()

#         # Loss for real images
#         validity_real = discriminator(real_imgs, labels)
#         d_real_loss = adversarial_loss(validity_real, valid)

#         # Loss for fake images
#         validity_fake = discriminator(gen_imgs.detach(), gen_labels)
#         d_fake_loss = adversarial_loss(validity_fake, fake)

#         # Total discriminator loss
#         d_loss = (d_real_loss + d_fake_loss) / 2

#         d_loss.backward()
#         optimizer_D.step()

#         print(
#             "[Epoch %d/%d] [Batch %d/%d] [D loss: %f] [G loss: %f]"
#             % (epoch, opt.n_epochs, i, len(dataloader), d_loss.item(), g_loss.item())
#         )

#         batches_done = epoch * len(dataloader) + i
#         if batches_done % opt.sample_interval == 0:
#             sample_image(n_row=10, batches_done=batches_done)
